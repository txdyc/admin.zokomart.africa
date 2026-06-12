# ZokoMart Admin 后端 PRD（产品需求与工程设计文档）

> 面向加纳电商独立站的自研后台管理系统 —— **后端**。
> 本文档既是产品需求说明，也是可直接落地的工程设计（数据模型 + 状态机 + 接口 + 权限）。
> 技术约定遵循工作区根目录 `../CLAUDE.md`（SpringBoot 3.5.15 / JDK 21 / Maven / MySQL /
> MyBatis-Plus / Redis / Sa-Token，基础包 `africa.zokomart.admin`）。

| 项 | 内容 |
|----|------|
| 版本 | v1.0（初稿） |
| 日期 | 2026-06-12 |
| 状态 | 已与需求方对齐核心设计，待评审 |
| 适用范围 | 后端（`backend/`）。前端约定以前端仓库为准，接口字段需双方对齐 |

---

## 1. 概述

### 1.1 背景与目标
ZokoMart 经营家电与家居百货。本系统支撑独立站的**进销存全链路**内部运营：
从供应商产品维护 → 采购计划与审批 → 付款与入库 → 库存 → 销售 → 物流派送与结算。
目标是用一套 RBAC 后台，让采购、仓库、销售、物流各角色在统一单据流上协作，数据可追溯。

### 1.2 业务主线（单据流转总览）
```
供应商产品(supplier_product)
  └─►采购计划(填采购数量,校验最小采购量)──审批通过──►采购订单(按供应商拆分)
        └─►付款标记(明细级)──►实际采购单(=已付款明细)──►入库──►库存↑
              └─►销售订单(扣减库存,录客户信息)──►物流派送──►签收/拒收(拒收回补库存)──►实收金额结算
```

### 1.3 核心设计决策（已与需求方确认）
1. **操作单元 = 供应商产品**。采购、入库、库存、销售全链路都以 `supplier_product` 为操作对象，
   库存按供应商产品维度记账。
2. **平台商品目录 SPU/SKU 与流转解耦**。`product_spu` / `product_sku` 为平台自有商品目录
   （面向前台展示），本期仅建表 + 基础维护；`supplier_product.sku_id` 可空关联，**不参与**采购-销售流转。
3. **零售价维护在供应商产品上**（`retail_price`）。销售下单时带出为默认单价，**销售员可逐行改价**。
4. **已付款明细 = 实际采购单**。采购员在采购订单上逐明细标记付款；仅"已付款"明细构成实际采购单并进入入库。
5. **采购订单按供应商拆分**（付款、入库天然按供应商组织）。
6. **库存在销售下单时即扣减**；物流环节"拒收"按拒收数量**回补库存**。
7. **实收金额 = Σ(明细单价 ×(数量 − 拒收量))，仅含货款**；派送费为成本单独记录、不计入实收。
8. 角色由超级管理员**动态创建**，不写死；本文档给出推荐权限码与角色模板供组装。

### 1.4 术语表
| 术语 | 说明 |
|------|------|
| 供应商产品 | 某供应商售卖的一个产品（含批发价、零售价、最小采购量、产品编码）。系统的操作单元 |
| SPU / SKU | 平台自有商品目录的标准产品单元 / 库存量单元。本期与流转解耦 |
| 最小采购量(MOQ) | 供应商对该产品的最小起订量；采购计划填写数量需 ≥ MOQ |
| 采购计划 | 按筛选条件批量录入采购数量形成的待审批单据 |
| 采购订单 | 审批通过后按供应商生成的正式采购单据 |
| 实际采购单 | 采购订单中"已付款"明细的集合，进入入库环节 |
| 实收金额 | 销售订单完成结算后实际收到的货款（扣除拒收部分） |

---

## 2. 角色与权限（RBAC）

### 2.1 模型
标准 RBAC：**用户 — 角色 — 权限(菜单/按钮)**。多对多。

- `sys_user` 持 `is_super` 标记：**超级管理员绕过一切权限校验**，可做任意操作（需求 #1）。
- 其他用户由超管创建，分配账号密码与角色（一人可多角色）。
- 权限粒度到**按钮/接口级**（`perm_code`），与前端 Vben 的按钮级权限对齐。
- 菜单树（`sys_menu`）同时承担前端路由与权限载体：`type` ∈ {目录, 菜单, 按钮}。

### 2.2 鉴权实现要点（Sa-Token）
- 登录签发 token，会话存 Redis；接口用注解 `@SaCheckLogin` / `@SaCheckPermission("...")` 或拦截器校验。
- `StpInterface` 实现：根据用户角色聚合其全部 `perm_code`；若 `is_super=1` 直接返回全权限（或在校验层短路放行）。
- **token 不得写入日志**；密码用 BCrypt 存储，禁止明文与可逆加密。
- 超管账号通过初始化脚本/数据迁移内置一个，密码首次登录强制修改（建议，作为非阻断项）。

### 2.3 推荐权限码（perm_code，节选；完整见附录 B）
`system:user:*`、`system:role:*`、`system:menu:*`、`brand:*`、`supplier:*`、`category:*`、
`product:spu:*`、`product:sku:*`、`supplierProduct:*`、`logisticsProvider:*`、
`purchase:plan:create|update|delete|submit`、`purchase:plan:approve`、
`purchase:order:pay|confirm`、`inventory:inbound`、`inventory:edit`、
`sales:order:create|list`、`logistics:dispatch|status|reject|complete`。

### 2.4 推荐角色模板（超管可据此组装，非硬编码）
| 角色 | 主要权限码 | 对应需求 |
|------|-----------|----------|
| 超级管理员 | （`is_super`，全权限） | #1 |
| 系统管理员 | `system:*` | #1 |
| 采购员 | `supplierProduct:*`、`purchase:plan:*`、`purchase:order:pay\|confirm`、查看实际采购单 | #6,#8,#10 |
| 审批主管 | `purchase:plan:approve`（+ 查看） | #9 |
| 仓库管理员 | `inventory:inbound`、`inventory:edit\|list` | #11,#12 |
| 销售员 | `sales:order:create\|list`（仅本人） | #13,#15 |
| 物流专员 | `logistics:*` | #14 |

---

## 3. 通用约定（与 CLAUDE.md 一致）

- **统一返回** `Result<T>`：`{ "code": 0, "msg": "success", "data": {} }`，业务码集中在 `ResultCode`。
  分页统一 `PageResult<T>`（`records` + `total` + `current` + `size`）。**最终字段名以前端 Vben 拦截器约定为准。**
- **统一异常**：业务异常抛 `BusinessException`，`GlobalExceptionHandler` 统一转 `Result`；controller 不 try/catch 吞异常。
- **参数校验**：`jakarta.validation` 注解写在 DTO 上，`@Valid` 触发，失败由全局异常统一返回。
- **分层**：controller（仅校验/编排）→ service/impl（业务+事务）→ mapper（数据访问）。entity/dto/vo 分离，对外不暴露 entity。
- **审计字段**（统一在 `BaseEntity`，MP 自动填充）：`create_time` `update_time` `create_by` `update_by`。
- **逻辑删除**：所有业务表含 `deleted`（MP 逻辑删除），不做物理删除。
- **乐观锁**：库存等高并发表含 `version`（MP `@Version`）。
- **命名**：表名/字段 snake_case，Java camelCase，靠 MP 驼峰映射。
- **单据编号规则**：`前缀 + yyyyMMdd + 当日流水`，当日流水用 Redis `INCR` 生成。
  前缀：采购计划 `CGJH`、采购订单 `CGDD`、实际采购单 `SJCG`、销售订单 `XSDD`。
- **金额**：`DECIMAL(12,2)`，货币默认 GHS（加纳塞地），单一币种，本期不做多币种。
- **数量**：库存/采购/销售数量为整数 `INT`（家电家居以件计）。

---

## 4. 数据模型

> 下列所有业务表默认含审计字段（`create_time`/`update_time`/`create_by`/`update_by`）与 `deleted`；
> 标注「乐观锁」的表额外含 `version`。主键统一 `id BIGINT`（雪花或自增,在 `MybatisPlusConfig` 统一）。

### 4.1 ER 概览（主键关系）
```
sys_user ─┬─< sys_user_role >─┬─ sys_role ─< sys_role_menu >─ sys_menu
supplier ─┬─< supplier_product >─┬─ brand
          │                      └─ category(self-tree)
          │                      └─ product_sku(可空) ─ product_spu
purchase_plan ─< purchase_plan_item >─ supplier_product
purchase_plan ─1:N─ purchase_order ─< purchase_order_item >─ supplier_product
purchase_order ─1:1─ actual_purchase_order ─< actual_purchase_order_item >─ purchase_order_item
supplier_product ─1:1─ inventory_stock ─1:N─ inventory_transaction
sales_order ─< sales_order_item >─ supplier_product
sales_order ─N:1─ logistics_provider
```

### 4.2 系统 / RBAC

**sys_user**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| username | VARCHAR(64) | 唯一，登录名 |
| password | VARCHAR(100) | BCrypt 密文 |
| nickname | VARCHAR(64) | 显示名 |
| phone | VARCHAR(32) | |
| email | VARCHAR(128) | 可空 |
| status | TINYINT | 0禁用 / 1启用 |
| is_super | TINYINT | 0否 / 1超级管理员 |
| remark | VARCHAR(255) | |

**sys_role**：id、name、code(唯一)、sort、status、remark
**sys_menu**：id、parent_id、name、type(1目录/2菜单/3按钮)、perm_code、route_path、component、icon、sort、visible(0隐藏/1显示)、status
**sys_user_role**：id、user_id、role_id
**sys_role_menu**：id、role_id、menu_id

### 4.3 基础数据

**brand**（品牌, #2）：id、name(唯一)、code、logo_url、sort、status(0停用/1启用)、remark
**supplier**（供应商, #3）：id、name(唯一)、code、contact_person、contact_phone、address、status、remark
**category**（商品分类, #4, 树）：id、parent_id(0为根)、name、sort、status
**logistics_provider**（物流服务商, #7）：id、name、code、contact_person、contact_phone、status、remark

### 4.4 平台商品目录（#5，本期建表+基础维护）

**product_spu**：id、name、brand_id、category_id、main_image、description、status
**product_sku**：id、spu_id、sku_code(唯一)、spec(规格,VARCHAR/JSON)、image、price `DECIMAL(12,2)`、status

### 4.5 供应商产品（#6，**核心操作单元**）

**supplier_product**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| supplier_id | BIGINT | 所属供应商 |
| name | VARCHAR(255) | 产品名称 |
| brand_id | BIGINT | 品牌 |
| category_id | BIGINT | 分类 |
| product_code | VARCHAR(64) | 产品编码，唯一约束 `(supplier_id, product_code)` |
| wholesale_price | DECIMAL(12,2) | 批发价（采购成本价） |
| retail_price | DECIMAL(12,2) | 零售价（销售默认单价，**新增字段**） |
| image_url | VARCHAR(512) | 产品图片 |
| min_purchase_qty | INT | 最小采购量(MOQ)，≥1 |
| sku_id | BIGINT | 可空，关联平台 SKU（不参与流转） |
| status | TINYINT | 0停用 / 1启用 |
| remark | VARCHAR(255) | |

### 4.6 采购

**purchase_plan**（采购计划, #8）：id、plan_no(唯一)、status(DRAFT/PENDING/APPROVED/REJECTED)、submit_time、approver_id、approve_time、approve_remark(退回原因)、total_qty、total_amount、remark
**purchase_plan_item**：id、plan_id、supplier_id、supplier_product_id、brand_id、category_id、product_name、product_code、wholesale_price(快照)、min_purchase_qty(快照)、purchase_qty(`≥ MOQ`)、amount(=wholesale_price×qty)

**purchase_order**（采购订单, #9生成 / #10）：id、order_no(唯一)、plan_id、supplier_id、status(PENDING_PAYMENT/CONFIRMED)、total_qty、total_amount、paid_amount、remark
**purchase_order_item**：id、order_id、supplier_product_id、product_name、product_code、wholesale_price、qty、amount、**payment_status**(UNSET待付款/PAID已付款/UNPAID未付款)

**actual_purchase_order**（实际采购单, #10→#11）：id、actual_no(唯一)、purchase_order_id、supplier_id、total_qty、total_amount、status(PENDING_INBOUND/INBOUND_DONE)、remark
**actual_purchase_order_item**：id、actual_order_id、purchase_order_item_id、supplier_product_id、product_name、qty、wholesale_price、amount、**inbound_status**(PENDING待入库/DONE已入库)、inbound_qty、inbound_time

### 4.7 库存（#11,#12）

**inventory_stock**（按供应商产品唯一记账，乐观锁）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| supplier_product_id | BIGINT | 唯一 |
| supplier_id / brand_id / category_id | BIGINT | 冗余，支持联动筛选 |
| quantity | INT | 当前库存（≥0） |
| version | INT | 乐观锁 |

**inventory_transaction**（出入库流水，只增不改）：id、supplier_product_id、type(PURCHASE_IN采购入库/SALES_OUT销售出库/REJECT_RETURN拒收回补/MANUAL_ADJUST手工调整)、qty_change(带正负)、before_qty、after_qty、ref_type(ACTUAL_PURCHASE_ORDER/SALES_ORDER/MANUAL)、ref_id、ref_no、operator_id、remark、create_time

### 4.8 销售 / 物流（#13,#14）

**sales_order**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| order_no | VARCHAR | 唯一 |
| status | VARCHAR | 见 5.13 状态机：PENDING_DISPATCH/DISPATCHING/SIGNED/SIGNED_PAID/UNREACHABLE/REJECTED |
| customer_name | VARCHAR(128) | 客户姓名（必填） |
| customer_phone | VARCHAR(32) | 客户手机号（必填） |
| customer_address | VARCHAR(512) | 详细地址（必填） |
| salesperson_id | BIGINT | 销售人员（=制单人，用于 #15 本人筛选） |
| total_qty | INT | 总数量 |
| total_amount | DECIMAL(12,2) | 应收货款 = Σ(unit_price×qty) |
| actual_amount | DECIMAL(12,2) | 实收金额，完成时计算 |
| logistics_provider_id | BIGINT | 物流服务商，派送时填 |
| delivery_fee | DECIMAL(12,2) | 派送费，派送时填（成本，不计入实收） |
| dispatch_time / sign_time / complete_time | DATETIME | 关键时间戳 |
| completed | TINYINT | 0未完成 / 1已完成（#15 筛选用） |
| remark | VARCHAR(255) | |

**sales_order_item**：id、order_id、supplier_product_id、product_name、product_code、unit_price(零售价带出,可改)、qty、reject_qty(拒收数量,默认0)、amount(=unit_price×qty)、actual_amount(=unit_price×(qty−reject_qty))

---

## 5. 功能模块详述

> 接口统一前缀 `/api`，RESTful 风格；下列接口为代表性集合，CRUD 标准操作不再逐一展开。
> 每个写操作均标注权限码，列表/详情默认需登录 + 对应 `:list` 权限。

### 5.1 用户登录与账号管理（#1）
**功能**：登录/登出；超管创建用户、分配角色、重置密码、启停；角色与菜单权限维护。
| Method | Path | 说明 | 权限码 |
|--------|------|------|--------|
| POST | /api/auth/login | 登录，返回 token + 用户信息 | 免登 |
| POST | /api/auth/logout | 登出 | 登录 |
| GET | /api/auth/user-info | 当前用户信息+权限码+菜单 | 登录 |
| GET/POST/PUT/DELETE | /api/system/users[/{id}] | 用户增删改查 | `system:user:*` |
| PUT | /api/system/users/{id}/password | 重置密码 | `system:user:resetPwd` |
| GET/POST/PUT/DELETE | /api/system/roles[/{id}] | 角色 | `system:role:*` |
| GET/POST/PUT/DELETE | /api/system/menus[/{id}] | 菜单/权限 | `system:menu:*` |

**规则**：超管不可被删除/停用；密码 BCrypt；用户停用后其 token 失效（踢下线）。

### 5.2 品牌管理（#2）
标准 CRUD。`brand:list|create|update|delete`。删除前校验是否被 `supplier_product` 引用，被引用则禁止删除（或仅停用）。

### 5.3 供应商管理（#3）
标准 CRUD。`supplier:*`。删除前校验是否被供应商产品/采购单引用。

### 5.4 商品分类管理（#4）
树形 CRUD（含 `GET /api/categories/tree`）。`category:*`。删除前校验子节点与被引用情况。

### 5.5 商品管理 SPU/SKU（#5）
SPU、SKU 两级 CRUD（`product:spu:*` / `product:sku:*`）。一个 SPU 下多个 SKU。
**本期范围**：仅目录维护，不与采购/库存/销售联动。

### 5.6 供应商产品管理（#6）
**功能**：维护每家供应商售卖的产品（名称、品牌、分类、产品编码、批发价、零售价、图片、最小采购量）。
| Method | Path | 说明 | 权限码 |
|--------|------|------|--------|
| GET | /api/supplier-products | 列表，支持 `supplierId/brandId/categoryId/keyword/status` 筛选 + 分页 | `supplierProduct:list` |
| POST/PUT/DELETE | /api/supplier-products[/{id}] | 增改删 | `supplierProduct:create\|update\|delete` |

**联动筛选支撑接口**（供采购/库存/销售页复用）：
- `GET /api/suppliers/options`、`GET /api/suppliers/{id}/brands`（该供应商有产品的品牌）、`GET /api/suppliers/{id}/categories`。

**校验**：`min_purchase_qty ≥ 1`；批发价/零售价 ≥ 0；`(supplier_id, product_code)` 唯一。

### 5.7 物流服务商管理（#7）
标准 CRUD。`logisticsProvider:*`。

### 5.8 采购计划管理（#8）
**流程**：进入页面按 `供应商→品牌→分类` 联动筛选供应商产品 → 对筛选出的列表逐条填写采购数量（`purchase_qty ≥ min_purchase_qty`，0 表示不采购）→ 保存为采购计划（草稿）→ 提交进入审批。
| Method | Path | 说明 | 权限码 |
|--------|------|------|--------|
| GET | /api/supplier-products | （复用）作为可采购产品来源，回显 MOQ | `purchase:plan:create` |
| POST | /api/purchase-plans | 创建采购计划（草稿，含明细） | `purchase:plan:create` |
| PUT | /api/purchase-plans/{id} | 编辑（仅 DRAFT/REJECTED 可编辑） | `purchase:plan:update` |
| POST | /api/purchase-plans/{id}/submit | 提交审批：DRAFT→PENDING | `purchase:plan:submit` |
| DELETE | /api/purchase-plans/{id} | 删除（仅 DRAFT/REJECTED） | `purchase:plan:delete` |
| GET | /api/purchase-plans[/{id}] | 列表/详情，可按状态/供应商筛选 | `purchase:plan:list` |

**校验**：每条明细 `purchase_qty` 必须 ≥ 对应 MOQ；至少一条明细 `qty>0`；计划金额自动汇总。明细保存产品与批发价**快照**（防止后续供应商产品改价影响已立计划）。

### 5.9 采购计划审批（#9）
**流程**：有 `purchase:plan:approve` 权限者对 PENDING 计划**通过**或**退回**。通过→计划 APPROVED 并**按供应商分组生成采购订单**（每供应商一张，状态 PENDING_PAYMENT）；退回→计划 REJECTED（回到提交人，可编辑后重新提交），需填退回原因。
| Method | Path | 说明 | 权限码 |
|--------|------|------|--------|
| POST | /api/purchase-plans/{id}/approve | 通过：PENDING→APPROVED，生成采购订单 | `purchase:plan:approve` |
| POST | /api/purchase-plans/{id}/reject | 退回：PENDING→REJECTED（带原因） | `purchase:plan:approve` |

**事务边界**：通过操作在单事务内完成「更新计划状态 + 生成订单+明细」；任一失败整体回滚。

### 5.10 采购订单付款管理（#10）
**流程**：采购员对采购订单逐明细标记 `已付款 / 未付款`（默认 UNSET 待付款）。确认后，将**已付款明细**汇总生成**实际采购单**（`actual_purchase_order`，状态 PENDING_INBOUND）；未付款明细不入实际采购单。
| Method | Path | 说明 | 权限码 |
|--------|------|------|--------|
| GET | /api/purchase-orders[/{id}] | 列表/详情（含明细付款状态） | `purchase:order:list` |
| PUT | /api/purchase-orders/{id}/items/payment | 批量设置明细付款状态 | `purchase:order:pay` |
| POST | /api/purchase-orders/{id}/confirm | 生成实际采购单（取 PAID 明细） | `purchase:order:confirm` |

**校验/规则**：确认生成时至少一条 PAID 明细；订单 `paid_amount` = Σ已付款明细金额；订单状态置 CONFIRMED。事务内完成「校验付款状态 + 生成实际采购单+明细 + 更新订单状态」。

### 5.11 入库（#11）
**流程**：实际采购单中的产品（即实际付款的产品）由仓库管理员标记**入库**；入库即增加对应供应商产品库存，并写入库流水。
| Method | Path | 说明 | 权限码 |
|--------|------|------|--------|
| GET | /api/actual-purchase-orders[/{id}] | 实际采购单列表/详情 | `purchase:order:list` |
| POST | /api/actual-purchase-orders/{id}/inbound | 明细入库（可逐条或整单） | `inventory:inbound` |

**事务**：单事务内对每条入库明细：`inbound_status PENDING→DONE` + `inventory_stock.quantity += qty`（无记录则创建，乐观锁重试）+ 写 `inventory_transaction(type=PURCHASE_IN)`。整单全部明细入库后，实际采购单 → INBOUND_DONE。重复入库幂等保护（已 DONE 明细跳过）。

### 5.12 库存管理（#12）
**流程**：仓库管理员按 `供应商→品牌→分类` 联动筛选，查看并**手工维护**每个供应商产品的实际库存数量。
| Method | Path | 说明 | 权限码 |
|--------|------|------|--------|
| GET | /api/inventory/stocks | 库存列表，联动筛选 + 分页 | `inventory:list` |
| PUT | /api/inventory/stocks/{supplierProductId} | 手工调整库存数量（带原因） | `inventory:edit` |

**规则**：手工调整必须写 `inventory_transaction(type=MANUAL_ADJUST)` 记录前后值与操作人；数量不可为负；乐观锁防并发覆盖。

### 5.13 销售管理（#13、#15）
**流程**：销售员按 `供应商→品牌→分类` 等筛选选择已售出的产品并填数量、确认/修改单价（默认带出零售价），填写客户信息（姓名、手机号、详细地址），生成销售订单：状态 `PENDING_DISPATCH`（即"已售未付款/未派送"），**同时扣减库存**。
| Method | Path | 说明 | 权限码 |
|--------|------|------|--------|
| GET | /api/inventory/stocks | （复用）作为可售产品来源，回显库存与零售价 | `sales:order:create` |
| POST | /api/sales-orders | 创建销售订单（扣库存 + 录客户信息） | `sales:order:create` |
| GET | /api/sales-orders | 列表，支持 `completed=true/false`（已完成/未完成）、本人筛选 | `sales:order:list` |
| GET | /api/sales-orders/{id} | 详情 | `sales:order:list` |

**销售订单状态机**：
```
PENDING_DISPATCH(未派送,=已售未付款)
   └─派送─► DISPATCHING(派送中)
                ├─► SIGNED(已签收)
                ├─► SIGNED_PAID(已签收并付款)
                ├─► UNREACHABLE(无法联系) ──可重派─► DISPATCHING / ──► REJECTED
                └─► REJECTED(已拒签)
完成(completed=1, #15"已完成")：由"确认完成"动作置位，可从 SIGNED 或 SIGNED_PAID 触发；
REJECTED(全拒签)视为自动完成且实收=0。UNREACHABLE 须先转为签收类或 REJECTED 才能完成。其余订单为"未完成"。
```
**扣库存校验**：下单时对每条明细校验 `inventory_stock.quantity ≥ qty`，不足则整单失败并提示具体产品；事务内扣减并写 `inventory_transaction(type=SALES_OUT)`，乐观锁防超卖。客户三项信息必填。`#15` 列表按 `salesperson_id=当前用户` + `completed` 筛选。

### 5.14 物流管理（#14）
**流程**：物流专员对销售订单安排派送 —— 选择物流服务商、填写派送费 → 订单进入派送中；之后更新签收类状态。在 `已签收`/`已签收并付款` 下，可对订单内一个或多个产品标记**拒收**（设置 `reject_qty`），拒收数量**回补库存**。**确认订单完成**时计算并生成**实收金额**。
| Method | Path | 说明 | 权限码 |
|--------|------|------|--------|
| POST | /api/sales-orders/{id}/dispatch | 选物流服务商+派送费，PENDING_DISPATCH→DISPATCHING | `logistics:dispatch` |
| PUT | /api/sales-orders/{id}/status | 更新为 SIGNED/SIGNED_PAID/UNREACHABLE/REJECTED | `logistics:status` |
| PUT | /api/sales-orders/{id}/items/reject | 标记明细拒收量（仅 SIGNED/SIGNED_PAID） | `logistics:reject` |
| POST | /api/sales-orders/{id}/complete | 确认完成（可从 SIGNED/SIGNED_PAID），结算实收金额，completed=1 | `logistics:complete` |

**结算规则**：
- 拒收时事务内：更新 `reject_qty` + `inventory_stock.quantity += reject_qty` + 写 `inventory_transaction(type=REJECT_RETURN)`。
- 完成时：`actual_amount = Σ(unit_price ×(qty − reject_qty))`（按实际接收的货款计）；`REJECTED` 全拒签则实收=0。
- `delivery_fee` 仅记录为成本，**不计入** `actual_amount`。
- 确认完成可从 `SIGNED` 或 `SIGNED_PAID` 触发；`completed=1` 并写 `complete_time`；完成后订单只读。`UNREACHABLE` 不能直接完成。

### 5.15 销售订单完成情况筛选（#15）
在销售管理列表通过 `completed` 参数区分该销售员的**已完成 / 未完成**订单（见 5.13 接口）。普通销售员仅可见本人订单；含全局查看权限的角色可见全部。

---

## 6. 端到端流程串讲（示例）

1. 采购员维护「供应商A — 品牌X — 分类Y」下的供应商产品（批发价/零售价/MOQ/图片）。
2. 采购员在采购计划页筛选 A/X/Y，逐条填采购数量（≥MOQ），提交采购计划。
3. 审批主管通过 → 系统按供应商生成采购订单（A 一张，PENDING_PAYMENT）。
4. 采购员逐明细标记付款；确认 → 已付款明细生成实际采购单（PENDING_INBOUND）。
5. 仓库管理员对实际采购单标记入库 → 对应供应商产品库存 +N，写采购入库流水。
6. 仓库管理员日常按 A/X/Y 联动筛选，必要时手工校正库存（写手工调整流水）。
7. 销售员筛选可售产品，选品+数量+单价（默认零售价），填客户信息，下单 → 状态未派送，库存 −N（销售出库流水）。
8. 物流专员选物流服务商+派送费 → 派送中 → 签收/已签收并付款；如有拒收，标记拒收量 → 库存回补。
9. 物流专员确认完成 → 计算实收金额，订单 completed=1。
10. 销售员在销售页查看本人「已完成 / 未完成」订单。

---

## 7. 非功能性需求

- **一致性与事务**：审批生成订单、生成实际采购单、入库、销售扣库存、拒收回补、完成结算等跨表写操作均在 `@Transactional` 单事务内；库存增减一律经 `inventory_stock`(乐观锁) + `inventory_transaction`(流水) 双写，保证可追溯、不超卖。
- **并发**：库存表乐观锁 + 失败重试（建议有限次重试）；单据编号经 Redis `INCR` 保证当日不重号。
- **安全**：Sa-Token 鉴权；密码 BCrypt；token 与完整客户 PII 不写日志；越权访问由权限码拦截；超管短路放行。
- **审计**：所有业务表保留创建人/时间、更新人/时间；库存与单据状态变更可经流水/状态字段回溯。
- **数据校验**：金额 ≥0、数量 ≥0、MOQ 约束、客户必填项、唯一性约束（用户名、产品编码、单据号）。
- **接口文档**：建议集成 Knife4j 输出 OpenAPI，便于前端联调。
- **国际化/币种**：单一币种 GHS；时间统一存储 UTC 或服务器时区（落地时与前端统一）。

---

## 8. 本期范围与后续（YAGNI）

**本期实现**：第 5 章全部模块（需求 #1–#15）。
**本期仅建表/基础维护、暂不深做**：
- 平台 SPU/SKU 目录（#5）与采购-销售流转的联动（仅可空关联）。
- 多币种、多仓库（当前单仓单币种）。
- 采购订单的部分付款分期、退货退款、对账报表。
- 数据权限（按部门/数据范围细分），当前仅菜单/按钮级 + 销售"本人"过滤。

**明确不在本期**：前台独立站商城、与既有 WooCommerce/Odoo Hub 的对接（本系统独立）。

---

## 附录 A：状态枚举汇总
| 实体 | 状态 |
|------|------|
| 采购计划 purchase_plan | DRAFT草稿 / PENDING待审批 / APPROVED已通过 / REJECTED已退回 |
| 采购订单 purchase_order | PENDING_PAYMENT待付款 / CONFIRMED已生成实际采购单 |
| 采购订单明细付款 payment_status | UNSET待付款 / PAID已付款 / UNPAID未付款 |
| 实际采购单 actual_purchase_order | PENDING_INBOUND待入库 / INBOUND_DONE已入库 |
| 实际采购单明细入库 inbound_status | PENDING待入库 / DONE已入库 |
| 销售订单 sales_order | PENDING_DISPATCH未派送 / DISPATCHING派送中 / SIGNED已签收 / SIGNED_PAID已签收并付款 / UNREACHABLE无法联系 / REJECTED已拒签 |
| 库存流水类型 inventory_transaction.type | PURCHASE_IN / SALES_OUT / REJECT_RETURN / MANUAL_ADJUST |

## 附录 B：权限码汇总（建议）
```
system:user:list|create|update|delete|resetPwd
system:role:list|create|update|delete
system:menu:list|create|update|delete
brand:list|create|update|delete
supplier:list|create|update|delete
category:list|create|update|delete
product:spu:list|create|update|delete
product:sku:list|create|update|delete
supplierProduct:list|create|update|delete
logisticsProvider:list|create|update|delete
purchase:plan:list|create|update|delete|submit|approve
purchase:order:list|pay|confirm
inventory:inbound|list|edit
sales:order:list|create
logistics:dispatch|status|reject|complete
```

## 附录 C：建议建表顺序（外键依赖）
1. sys_*（用户/角色/菜单/关联）
2. brand、supplier、category、logistics_provider
3. product_spu → product_sku
4. supplier_product
5. purchase_plan → purchase_plan_item
6. purchase_order → purchase_order_item
7. actual_purchase_order → actual_purchase_order_item
8. inventory_stock、inventory_transaction
9. sales_order → sales_order_item
