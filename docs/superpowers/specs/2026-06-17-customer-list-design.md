# 客户管理（客户列表）— 设计文档

- 日期：2026-06-17
- 范围：`backend/`（admin.zokomart.africa）+ `frontend/`（front.admin.zokomart.africa）
- 特性分支：两仓 `feat/customer-list`

## 1. 目标

新增「客户管理」模块，当前只做**只读的客户列表**：展示下过单的客户。后端目前没有独立客户表，客户信息内嵌在 `sales_order`（`customer_name`/`customer_phone`/`customer_address`），因此客户列表由销售订单聚合得出。

## 2. 关键决策（已与用户确认）

| # | 决策 | 选择 |
|---|------|------|
| 1 | 数据来源 | 从 `sales_order` 聚合（不新建客户表） |
| 2 | 去重键 | **`customer_phone`**；同一电话视为同一客户 |
| 3 | 姓名/地址取值 | 取该客户**最近一单**（`create_time` 最大）的 `customer_name`/`customer_address` |
| 4 | 电话为空的订单 | 暂不计入（电话是客户稳定标识） |
| 5 | 菜单层级 | **顶级菜单**「客户管理」（与 销售管理/物流管理 同级） |
| 6 | 范围 | 只读列表（无增删改），不建独立 customer 表 |

## 3. 后端设计（新 `module/customer`）

### 3.1 端点
- `GET /api/customers` —— 分页 + 可选 `keyword`（姓名/电话模糊）。权限 `@SaCheckPermission("customer:list")`。
- 参数：`keyword`(可选)、`current`(默认1)、`size`(默认10)。
- 返回：`Result<PageResult<CustomerVO>>`。

### 3.2 CustomerVO
- `customerName` (String)、`customerPhone` (String)、`customerAddress` (String)、`orderCount` (long)、`totalAmount` (BigDecimal)、`lastOrderTime` (LocalDateTime)。

### 3.3 聚合查询（XML mapper）
仅统计 `deleted=0` 且 `customer_phone` 非空的销售订单：
- `GROUP BY customer_phone`
- `orderCount = COUNT(*)`、`totalAmount = SUM(actual_amount)`、`lastOrderTime = MAX(create_time)`
- `customerName`/`customerAddress`：取最近一单的值（按 `customer_phone` 关联取 `create_time` 最大的那条订单的姓名/地址）。
- `keyword`：`customer_name LIKE %kw% OR customer_phone LIKE %kw%`。
- 分页：先按聚合结果分页（COUNT(DISTINCT customer_phone) 作为 total），按 `lastOrderTime DESC` 排序。

实现要点：用一个 `customer` 模块自有的 mapper + `resources/mapper/CustomerMapper.xml` 写聚合 SQL（不复用 SalesOrder 实体，避免污染销售模块）。分页用 MyBatis-Plus 分页插件对聚合结果分页，或在 XML 里手写 count + limit。优先用 MP `Page` + XML（`<select>` 接收 `Page` 参数，MP 自动包 count）。姓名/地址取最近一单可用关联子查询：
```sql
SELECT s.customer_phone, s.customer_name, s.customer_address,
       agg.order_count, agg.total_amount, agg.last_order_time
FROM (
  SELECT customer_phone, COUNT(*) order_count, SUM(actual_amount) total_amount, MAX(create_time) last_order_time
  FROM sales_order
  WHERE deleted=0 AND customer_phone IS NOT NULL AND customer_phone <> ''
  [AND (customer_name LIKE #{kw} OR customer_phone LIKE #{kw})]
  GROUP BY customer_phone
) agg
JOIN sales_order s ON s.customer_phone = agg.customer_phone
  AND s.create_time = agg.last_order_time AND s.deleted=0
ORDER BY agg.last_order_time DESC
```
（若同一电话最近时间存在并列，取任一条即可；可加 `GROUP BY agg.customer_phone` 兜底防重。）

### 3.4 权限与迁移（V12）
- 新增顶级目录菜单「客户管理」+ 其下「客户列表」页面菜单 + 列表按钮 `customer:list`。
- id 取当前未占用段（V7 用到 1xxx 目录、11xx 菜单、20xx 按钮；本特性新菜单 id 取 1008 目录 / 1116 菜单 / 2064 按钮，路由 `/customer`、组件 `customer/index`）。具体可用 id 落地时再核对，避免与现有冲突。
- superadmin 通配自动可见；其它角色后续在「角色管理」里授权。

## 4. 前端设计
- 路由/菜单由后端菜单驱动（登录返回菜单树）。新增页 `src/views/customer/index.vue`，组件路径与菜单 `component` 字段（`customer/index`）对齐。
- 只读表格列：客户姓名 / 电话 / 地址 / 订单数 / 累计金额 (GHS) / 最近下单时间；顶部关键字搜索（姓名或电话）+ 查询/重置。
- `src/api/customer.ts`：`apiCustomerPage(query)`；`src/types/customer.d.ts`：`CustomerVO`、`CustomerQuery`。
- 复用 `BasicTable`（既有分页表格组件）。

## 5. 测试
- **后端**：集成测试 —— 自建供应商/产品后下 3 张销售订单（同一电话 phoneA 下 2 单、phoneB 下 1 单），调 `GET /api/customers` 断言：返回 2 个客户、phoneA 的 `orderCount=2`、`totalAmount` 为两单之和、`lastOrderTime` 为较晚一单、姓名/地址为最近一单值；keyword 过滤命中。测试结束清理。
- **前端**：列表页组件测试（mock `apiCustomerPage` 返回 2 行，断言渲染行数与关键字段）。

## 6. 改动范围与交付
- `backend/`：`module/customer`（CustomerController、CustomerService(+impl)、CustomerMapper + `resources/mapper/CustomerMapper.xml`、CustomerVO、CustomerQuery）、V12 迁移、集成测试。
- `frontend/`：`src/views/customer/index.vue`、`src/api/customer.ts`、`src/types/customer.d.ts`、组件测试。
- 两仓 `feat/customer-list` 分支。

## 7. 不做（YAGNI）
- 不做客户新增/编辑/删除。
- 不新建独立 customer 表。
- 电话为空的订单不归集。
- 不做客户详情/订单下钻（仅列表）。
