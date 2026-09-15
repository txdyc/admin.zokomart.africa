# Sales Order Excel Import — Design

Date: 2026-09-15
Scope: backend (`admin.zokomart.africa.git`) + frontend (`front.admin.zokomart.africa.git`)
Branch: `feat/sales-order-import` (both repos), cut from `feat/wc-multi-site-sync`

## Goal

在 `/sales/order` 页面增加「导入订单」功能。用户上传一份 `.xlsx`，系统把其中的
商品行**按客户归并成销售订单**，生成正式 `sales_order` + `sales_order_item`，
并像手工下单一样扣减库存。

归并是这个特性的核心：源表每行是一件商品、每行一个 `Order ID`，同一个客户买多件
会占多行多个 `Order ID`。必须合并成**一张订单**，因为同一客户的货要安排在**同一次派送**里。

## Decisions（已与用户逐条确认）

| # | 决策 | 取值 |
|---|---|---|
| 1 | `Order ID` 粒度 | **行级**（明细号），**不能**用作分组键 |
| 2 | 分组键 | `Phone Number` + `Customer Name` + `Shipping Address` + `Order Date` 四者一致<br>（同一客户**跨天**下单拆成两单，不报错） |
| 3 | `Sale Price` 含义 | **行小计**（= 单价 × 数量） |
| 4 | 库存 | **照常扣减**，与手工下单完全一致（`SALES_OUT` 流水，允许负库存） |
| 5 | `Product Code` 查不到 | **整单失败**，文件内其余订单照常导入 |
| 6 | `Product Code` 匹配到多条 | 视为歧义，**整单失败**并报错 |
| 7 | `Status` 列 | **忽略其值**，一律 `PENDING_DISPATCH`；**表头可缺**该列 |
| 8 | `Order Date` | 新增 `sales_order.order_date` 列，**同时** `create_time` 也写该日期 |
| 9 | `Order Date` 为空/无法解析 | **整单失败**（它是分组键与查重键的一部分） |
| 10 | `City` | 新增 `sales_order.city` 列 |
| 11 | 重复导入 | 库内按**同一个分组键**查重，命中则**跳过**并单独计数 |
| 12 | 电话归一化 | 做**国际区号归一**（`0244239227` ≡ `233244239227` ≡ `244239227`） |
| 13 | 电话入库格式 | **存 Excel 原值**；归一值仅用于分组与查重 |
| 14 | `salespersonId` | **当前导入操作人** |
| 15 | 文件格式 | 仅 `.xlsx`，上限 **1000 行** |
| 16 | 表头拼写 | 只认 **`Sale Price`**；`utils/OrdersTemplate.xlsx` 的 `Sake Price` 笔误一并改掉 |

## Excel 契约

第一个 sheet，第 1 行为表头。列名匹配规则：**trim + 折叠内部空白 + 忽略大小写**，列序不限。

**必需列（10）**

`Order ID` · `Sale Price` · `Customer Name` · `City` · `Shipping Address` ·
`Phone Number` · `Product Name` · `Product Code` · `Quantity` · `Order Date`

**可选列（1）**：`Status` —— 存在则忽略其值，缺失也不报错。

缺任一必需列 → 整个文件拒绝（`IMPORT_FILE_INVALID`）。
数据行 > 1000 → 拒绝（`IMPORT_TOO_MANY_ROWS`）。
非 `.xlsx` / 解析失败 → `IMPORT_FILE_INVALID`。

### 单元格取值规则

| 列 | 规则 | 违规后果 |
|---|---|---|
| `Order ID` | 文本，trim。**仅用于错误提示与溯源**，不参与分组 | 空 → 整单失败 |
| `Sale Price` | 数字，≥ 0，scale 2 | 非数/负 → 整单失败 |
| `Customer Name` | 文本，trim，非空 | 空 → 整单失败 |
| `City` | 文本，trim，**可为空** | — |
| `Shipping Address` | 文本，trim，非空 | 空 → 整单失败 |
| `Phone Number` | 文本 trim；数字单元格转为无科学计数的整数串 | 空 → 整单失败 |
| `Product Name` | 文本。**仅用于错误提示**，落库用供应商产品库里的名字 | — |
| `Product Code` | 文本，trim，非空 | 空 → 整单失败 |
| `Quantity` | 整数 ≥ 1 | 非整数/< 1 → 整单失败 |
| `Order Date` | 见下 | 空/无法解析 → 整单失败 |

**`Order Date` 解析**（样例文件里是 Excel 序列号 `46280` = 2026-09-15）：

1. 日期样式单元格 → 直接取日期。
2. 纯数字 → 按 Excel 序列号解析，epoch `1899-12-30`，值须 ≥ 1。
3. 文本 → 只接受 `yyyy-MM-dd` 与 `yyyy/M/d`。
   **刻意不支持 `dd/MM/yyyy` 与 `MM/dd/yyyy`** —— 两者无法区分，猜错会静默造出错误日期。

### 电话归一化（仅内存使用，不入库）

```
digits = 去掉所有非数字字符
if digits 以 "00233" 开头        -> 去掉 "00233"
else if digits 以 "233" 开头 且 长度 12 -> 去掉 "233"
else if digits 以 "0"   开头 且 长度 10 -> 去掉前导 "0"
结果不足/超过 9 位时原样保留（仍可作键，只是不跨格式归并）
```

### 姓名 / 地址归一化（仅内存使用）

`trim` → 折叠内部连续空白为单个空格 → 转小写。
**不做**全半角转换 —— 样例文件里同一客户的地址字符串逐字符相同，没有必要引入这层风险。

## 归并与处理流程

```
上传 .xlsx
  → 解析首个 sheet，校验表头与行数
  → 逐行取值（行级字段错误不立刻失败，挂到所属分组上）
  → 按 归一化(phone|name|address|orderDate) 分组，LinkedHashMap 保留文件原始顺序
  → 一次性查出这批 order_date 涉及的全部已有订单，在内存里建查重集合
  → 逐组处理（每组一个独立事务）
  → 汇总结果
```

**分组键 = `归一化电话 | 归一化姓名 | 归一化地址 | order_date`**

日期进键，所以同一个客户在不同日期下的单会自然落到不同组，**拆成两张订单**，
不需要额外校验也不会报错。查重用的是同一个键，两者定义一致。

**日期缺失的行**无法构成合法键：按 `phone|name|address` 单独归为一个失败组，
整组记错误（原因"Order Date 为空或无法解析"），不入库。

**逐组处理**

1. 该组内任一行有字段错误 → 整组失败，记错误，继续下一组。
2. 按 `product_code` 查 `supplier_product`（`deleted = 0`）：0 条 → 整组失败；> 1 条 → 整组失败（歧义）。
3. 查重集合命中 → **跳过**，`skipped + 1`。
4. 组装 `SalesOrderCreateDTO` → 调 `SalesOrderService.create(dto)`。
5. 新建成功的订单键**加入查重集合**，防同一文件内后续重复。

**金额计算**

```
unitPrice   = SalePrice / Quantity   (scale 2, HALF_UP)
item.amount = SalePrice              (原值，不回乘)
order.totalAmount = Σ item.amount
order.totalQty    = Σ Quantity
```

`amount` 用原值而非 `unitPrice × qty`，这样**订单总额精确等于 Excel 各行之和**，
不会因为除不尽（如 700 ÷ 3）产生漂移。

## 事务模型

导入服务是**独立的 bean**，注入 `SalesOrderService` 后调用 —— 走 Spring 代理，
`create()` 上的 `@Transactional` 真正生效，于是「每单一个事务，单失败只回滚该单」天然成立。
导入服务本身**不加** `@Transactional`。

> 这是选择"复用 `create()`"而非"另写一套落库逻辑"的主要技术理由：
> 库存扣减、单号生成、状态初始化只有一份实现，不会随时间漂移。

## 后端

### 迁移 `V23__sales_order_import.sql`

```sql
ALTER TABLE sales_order
  ADD COLUMN city       VARCHAR(128) NULL COMMENT '城市'                       AFTER customer_address,
  ADD COLUMN order_date DATE         NULL COMMENT '订单日期（业务日期，非创建时刻）' AFTER city,
  ADD KEY idx_sales_order_date (order_date);

ALTER TABLE sales_order_item
  ADD COLUMN external_order_id VARCHAR(64) NULL COMMENT '来源 Excel 的 Order ID（行级，仅溯源）' AFTER order_id;

UPDATE sales_order SET order_date = DATE(create_time) WHERE order_date IS NULL;  -- 历史回填
```

权限 seed（照 V9 写法，下一个空闲 id = **2078**）：

```sql
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, ...) VALUES
(2078, 1114, '导入销售订单', 3, 'sales:order:import', NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 904 * 100000 + m.id, 904, m.id, NOW() FROM sys_menu m WHERE m.id = 2078;  -- 销售员 SALES
```
superadmin 走通配 `*`，无需显式授权。

### 依赖

新增 `org.dhatim:fastexcel-reader` —— 只读、流式、体积远小于 Apache POI，
且原生提供 `getCellAsDate()` 处理序列号日期。
**实施第 1 步先验证版本可用**；不可用则退回 `org.apache.poi:poi-ooxml`。

### 代码改动

`module/sales` 内新增：

- `service/SalesOrderImportService` + `impl/SalesOrderImportServiceImpl`
- `service/impl/SalesOrderExcelParser`（纯解析，无 Spring 依赖，便于单测）
- `vo/SalesOrderImportResultVO`、`vo/SalesOrderImportError`
- `constant/SalesImportConst`（列名、上限、epoch）

改动既有文件：

- `SalesOrderCreateDTO`：新增可空 `city`、`orderDate`；`Item` 新增可空 `amount`、`externalOrderId`。
- `SalesOrderServiceImpl.create()`：
  - `item.amount` = 传入值，为空则 `unitPrice × qty`（手工下单行为不变）
  - `order.city` = `dto.city`
  - `order.orderDate` = `dto.orderDate`，为空则 `LocalDate.now()`（手工下单）
  - 当 `dto.orderDate` 非空时，显式 `order.setCreateTime(orderDate.atStartOfDay())`
- `SalesOrderServiceImpl.page()`：排序追加 `.orderByDesc(SalesOrder::getId)` 作为 tiebreaker
  （导入订单的 `create_time` 同秒，只按 `create_time` 排序在 MySQL 里顺序不确定）。
- `SalesOrderVO`：补 `city`、`orderDate`。
- `SalesOrderController`：新增 `POST /api/sales-orders/import`。

> **对 CLAUDE.md 红线的一处刻意例外**：约定里写明「审计字段由自动填充统一处理，业务代码不手动 set」。
> 导入路径会显式设置 `create_time`，因为仪表盘与列表统计都以它为口径，历史订单必须归到业务日期上。
> 范围严格限定在"`dto.orderDate` 非空"这一条分支；`update_time` 仍由自动填充写入真实导入时刻，
> 因此"什么时候导入的"这个信息不会丢失。

### 接口

`POST /api/sales-orders/import`，`multipart/form-data`，`@SaCheckPermission("sales:order:import")`

```json
{ "code": 0, "msg": "success", "data": {
  "totalRows": 14,
  "orderCount": 9,
  "success": 8,
  "skipped": 1,
  "failed": 0,
  "errors": [
    { "rows": "4,5,6,7", "externalOrderIds": "SSK202609151503, ...",
      "customerName": "Baaba Maison", "productCode": "BD-55",
      "reason": "产品编码不存在" }
  ]
}}
```

错误**按订单聚合**（一单多行合成一条），`rows` 为源文件行号（表头 = 第 1 行）。

## 前端

- `/sales/order` 工具栏新增「导入订单」按钮：`v-perm="'sales:order:import'"`，`data-test="sales-import"`，
  位置在「打印标签」与「新增订单」之间。
- 新增 `views/sales/order/SalesOrderImportModal.vue`，结构照 `RawOrderImportModal.vue`：
  选文件 → 提交 → 摘要（总行/成单/成功/跳过/失败）+ 错误表格。
  **`beforeUpload` 做 `.xlsx` 后缀校验**（把 raw order 那边遗留的同类坑一并补上）。
- 弹窗内以说明文字列出 10 个必需列名 + 1 个可选列。**不做模板下载**（前端生成 xlsx 需引额外依赖，
  收益不足；真需要时后续加一个后端 `GET /import-template` 端点即可）。
- 新增 `api/sales/order.ts::apiSalesOrderImport`、`types/sales.ts` 类型、i18n `zh-CN` + `en-US`。
- 导入成功后 `tableRef.reload()`。

## 测试

**后端**（`SalesOrderExcelParserTest` + `SalesOrderImportServiceImplTest`）

- 表头：列序打乱 / 大小写与空白差异 / 缺 `Status`（应通过）/ 缺必需列（应拒）
- 行数超 1000 → 拒
- 分组：样例文件 14 行 → 9 单；Baaba Maison 4 行合 1350、Nuhu yahaya 2 行合 1400、ohannes 2 行合 1400
- 金额：`qty > 1` 的行（600 / 2 → 单价 300，amount 600）；**除不尽**用例（700 / 3 → 单价 233.33，amount 仍 700）
- 日期：Excel 序列号 46280 → 2026-09-15；文本 `2026-09-15`；空 → 整单失败
- 电话归一：`0244239227` / `233244239227` / `+233 244 239 227` 归并为一单，入库仍为原值
- 编码：不存在 → 整单失败且其余单照常；匹配 2 条 → 歧义失败
- 查重：同客户同日期已有订单 → skipped；同一文件内不会自我重复
- **同一客户跨天**：同 phone/name/address、不同 `Order Date` 的行 → **拆成两张订单**，各自金额独立
- 库存：导入后 `SALES_OUT` 流水与库存变化正确；单失败时该单**无**流水残留（事务边界）
- 集成测试覆盖 `sales:order:import` 权限（有/无权限）

> 注意已知坑：集成测试跑的是真实 dev 库，不会自动清理。
> 本特性的测试**必须自清理**，不得往 `sales_order` / `inventory_*` 留垃圾数据。

**前端**：vitest 覆盖 `beforeUpload` 后缀校验、`onSubmit`、结果渲染；`vue-tsc` + `build` 通过。

**UI 冒烟**：用 `utils/OrdersTemplate.xlsx` 跑全链 —— 登录 → 销售订单 → 导入 → 摘要
→ 列表出现 9 单 → 抽查 Baaba Maison 详情（4 件、总额 1350）→ 再导一次确认 9 单全部 skipped。

## 不在本次范围

- 导入前预览/确认（用户选了"库内查重"而非"预览再提交"）
- 销售订单删除/撤销导入接口（现在没有，误导入只能靠查重防住）
- `.xls` / `.csv` 支持
- Excel `Status` 列到系统状态的映射（明确忽略）
- 手工下单表单增加 City 输入框（DTO 支持了，UI 本次不加）

## 风险

| 风险 | 缓解 |
|---|---|
| 样例文件里只有 1 行 `qty > 1`，行小计假设实测覆盖不足 | 单测专门造多件与除不尽用例 |
| 地址写法轻微差异会漏合并，拆成两单 | 已选保守策略：宁可漏合并，不可错合并；操作员可在源表里统一地址后重导（查重会挡住已导入的） |
| 导入的订单全部归属操作人，销售员导入大批量单后自己名下暴涨 | 已确认接受；管理员有 `sales:order:list:all` 可看全部 |
| `create_time` 被写成业务日期，与其它模块的审计语义不一致 | 范围限定在导入路径；`update_time` 保留真实时刻 |
| `feat/wc-multi-site-sync`（V22）尚未合并，V23 依赖其先落地 | 本分支从 `feat/wc-multi-site-sync` 切出；若该分支被放弃则迁移改回 V22 |
