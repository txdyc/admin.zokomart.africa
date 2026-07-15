# Design: Sales Support & Logistics Support roles

**Date:** 2026-07-15
**Status:** Approved (brainstorming)
**Repos touched:** `backend` (migration + one controller/service change + test), `frontend` (e2e test only)

## Goal

Provide two operational roles with **per-user data isolation** on sales orders:

1. **Sales Support** — front-line order takers. Each user sees only the sales
   orders they created (across the sales page *and* the logistics-tracking view),
   plus read access to the catalog and inventory needed to place an order.
2. **Logistics Support** — fulfilment operators. They process logistics for
   **all** orders (dispatch, status, delivery fee, reject, complete), and their
   left nav is limited to the Logistics module only.

## Decisions (from brainstorming)

- **Repurpose the two existing V7 seed roles in place** rather than adding new
  ones: `销售员/SALES` (id 904) → **Sales Support**, `物流专员/LOGISTICS` (id 905)
  → **Logistics Support**. The role *codes* `SALES`/`LOGISTICS` are not referenced
  anywhere in application code (only in the V7 seed and one e2e display label), so
  they stay unchanged; only the display `name`/`remark` and the role→menu bindings
  change. Display names are Chinese to match the UI: `销售支持` / `物流支持`.
- **Inventory for Sales Support is exposed as a real nav menu** (库存管理 →
  库存列表, read-only), making the left nav 4 groups for that role. This overrides
  the "3 groups" wording in the original request per the user's explicit choice.
- Role display names are **Chinese** (`销售支持` / `物流支持`) to match the UI.

## What already exists (reused, not rebuilt)

- **Ownership stamping:** `SalesOrderServiceImpl.create` sets
  `salesperson_id = current login id`.
- **List/labels isolation:** `SalesOrderController` computes
  `salespersonId = hasPermission("sales:order:list:all") ? null : loginId` and the
  service filters by it. Holders of `sales:order:list:all` see everything; others
  see only their own.
- **Tracking page reuses the same list endpoint:** `views/logistics/track` calls
  `GET /api/sales-orders` (`apiSalesOrderPage`). Therefore the per-user filter
  above **already** governs what each role sees in tracking — no separate logic.
- **Nav vs. permission split:** `router/dynamic.ts#buildRoutes` filters out
  `type=3` button nodes, so a bound button contributes a *permission code* (via
  `LoginUserVO.permissions` → `selectPermCodesByUserId`) **without** producing a
  nav item. This is the mechanism that lets Logistics Support hold
  `sales:order:list:all` with no 销售管理 nav entry.

## Gaps this work closes

1. **Detail endpoint leaks across users.** `GET /api/sales-orders/{id}`
   (`SalesOrderController.detail`) currently returns any order by id with only a
   `sales:order:list` check — a Sales Support user could read another user's order
   by guessing the id. Fixed below so isolation holds on detail too.
2. **`物流专员` cannot list orders.** The tracking page lists via
   `sales:order:list`, which the old `物流专员` role lacked, so it could not see
   any order to act on. Logistics Support is granted `sales:order:list` +
   `sales:order:list:all`.

## Design

### 1. Migration `V17__support_roles.sql`

Flyway migrations are immutable, so this is a new file (not an edit to V7). It:

1. **Updates role rows 904 / 905** — set `name`, `remark`; codes unchanged.
   ```
   904: name='销售支持', code='SALES'     (unchanged)
   905: name='物流支持', code='LOGISTICS'  (unchanged)
   ```
2. **Clears and re-inserts `sys_role_menu` for 904 and 905** only
   (`DELETE ... WHERE role_id IN (904,905)`), reusing V7's deterministic id scheme
   `id = role_id*100000 + menu_id`. Other roles are untouched.

**Sales Support (904) — bound `menu_id`s**

| Purpose | Dir | Menu (nav) | Button (perm) |
|---|---|---|---|
| Catalog / supplier products | 1003 平台目录 | 1110 供应商产品 | 2038 `supplierProduct:list` |
| Inventory (read-only) | 1005 库存管理 | 1113 库存列表 | 2051 `inventory:list` |
| Sales orders (own scope) | 1006 销售管理 | 1114 销售订单 | 2054 `sales:order:list`, 2055 `sales:order:create` |
| Logistics tracking (view-only) | 1007 物流管理 | 1115 物流跟踪 | *(none)* |

Set: `{1003,1005,1006,1007, 1110,1113,1114,1115, 2038,2051,2054,2055}`

Deliberately **excluded**: `inventory:edit`/`inventory:inbound` (2052/2053) →
read-only stock; all `logistics:*` (2057–2060) → view-only tracking;
`sales:order:list:all` (2056) → own orders only.

**Logistics Support (905) — bound `menu_id`s**

| Purpose | Dir | Menu (nav) | Button (perm) |
|---|---|---|---|
| Logistics tracking (all orders) | 1007 物流管理 | 1115 物流跟踪 | 2057 `logistics:dispatch`, 2058 `logistics:status`, 2059 `logistics:reject`, 2060 `logistics:complete` |
| List all orders (perm only, **no nav**) | — | — | 2054 `sales:order:list`, 2056 `sales:order:list:all` |

Set: `{1007,1115, 2054,2056,2057,2058,2059,2060}`

Buttons 2054 and 2056 are bound **without** their parent menu 1114 / dir 1006. The
orphaned `type=3` nodes are filtered out of the nav by `buildRoutes`, so Logistics
Support's left nav stays exactly **物流管理 → 物流跟踪**, while the permission codes
still flow through `permissions` — granting list-all capability on the tracking
page. `2054` is bound to both roles; the `role_id*100000+menu_id` id keeps the two
`sys_role_menu` rows distinct.

### 2. Backend — enforce ownership on detail

Make `GET /api/sales-orders/{id}` respect the same scope as the list. The check
goes in **`SalesOrderController.detail`, not the service**: `getDetail(Long id)` is
called directly by `SalesFlowServiceTest`, so changing its signature would break
that test. The VO already carries `salespersonId`, so the controller has what it
needs:

```java
@GetMapping("/{id}")
@SaCheckPermission("sales:order:list")
public Result<SalesOrderVO> detail(@PathVariable Long id) {
    SalesOrderVO vo = salesOrderService.getDetail(id);
    if (!StpUtil.hasPermission(PERM_VIEW_ALL)
            && !StpUtil.getLoginIdAsLong().equals(vo.getSalespersonId())) {
        // 同 NOT_FOUND，避免 id 可枚举
        throw new BusinessException(ResultCode.NOT_FOUND, "销售订单不存在");
    }
    return Result.ok(vo);
}
```

This covers both surfaces that fetch detail: the sales page and the tracking page
(`apiSalesOrderGet`). Sales Support → own only; Logistics Support (has
`list:all`) → any order, as intended. The service, its interface, and
`SalesFlowServiceTest` are untouched.

### 3. Isolation summary (behavioural)

| Action | Sales Support | Logistics Support |
|---|---|---|
| Create sales order | ✅ (owner = self) | ❌ |
| List / detail sales orders | own only | all |
| Tracking page: view orders | own only | all |
| Tracking page: dispatch/status/fee/reject/complete | ❌ (buttons hidden) | ✅ |
| Supplier products | view | ❌ |
| Inventory list | view (read-only) | ❌ |
| Left nav groups | 平台目录 · 库存管理 · 销售管理 · 物流管理 | 物流管理 |

Superadmin is unaffected (bypasses menu filtering and sees all).

## Testing

- **Backend (new):** ownership on detail — a user without `sales:order:list:all`
  gets `200` for their own order and `NOT_FOUND` for another user's order; a user
  with `list:all` gets `200` for any. Existing `SalesOrderApiTest` builds its own
  role and is unaffected.
- **Frontend e2e (`tests/e2e/role-access.e2e.ts`):**
  - Rename the two role labels `销售员`→`销售支持`, `物流专员`→`物流支持`.
  - Sales Support: assert nav shows 平台目录 / 库存管理 / 销售管理 / 物流管理;
    hides 采购管理 / 系统管理; tracking page shows **no** dispatch/status/complete
    buttons; `新增销售订单` present.
  - Logistics Support: nav shows **only** 物流管理; `/sales/order` route forbidden;
    tracking page lists orders (list-all) and shows action buttons.
- **Migration:** confirm Flyway `V17` applies on a clean rebuild; because the app
  runs as a built jar, rebuild + restart before manual verification.

## Out of scope (YAGNI)

- No org/team/hierarchy model — isolation is strictly per individual user id.
- No back-fill or reassignment of `salesperson_id` on pre-existing orders.
- No dedicated "logistics order list" endpoint — the existing `/sales-orders`
  list with the `list:all` scope is sufficient.
- No change to role *codes* or to any other seed role (采购员/审批主管/仓库管理员).
