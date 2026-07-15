# Sales Support & Logistics Support Roles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repurpose the two existing seed roles into `销售支持` (own-scope sales orders + read-only catalog/inventory) and `物流支持` (process logistics for all orders), with real per-user data isolation.

**Architecture:** Pure RBAC re-binding via a new Flyway migration `V17`, plus one controller-level ownership guard on the sales-order detail endpoint. No schema changes, no new entities — everything reuses the existing `salesperson_id` isolation and the nav/permission split already in the codebase.

**Tech Stack:** Spring Boot 3.5 + MyBatis-Plus + Flyway (MySQL) backend; Sa-Token RBAC; Vue3 + Playwright e2e frontend.

**Design reference:** `docs/superpowers/specs/2026-07-15-support-roles-design.md`

**Runtime note:** the backend runs as a built jar. After changing backend code or adding a migration, **rebuild and restart** before manual/HTTP verification, or new endpoints/migrations won't be live.

---

## Task 1: Backend — enforce ownership on sales-order detail (TDD)

Close the isolation hole where `GET /api/sales-orders/{id}` returns any order by id
to any holder of `sales:order:list`.

**Files:**
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/controller/SalesOrderController.java`
- Test: `backend/src/test/java/africa/zokomart/admin/sales/SalesOrderDetailIsolationTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/africa/zokomart/admin/sales/SalesOrderDetailIsolationTest.java`:

```java
package africa.zokomart.admin.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 销售订单详情的本人隔离：无 sales:order:list:all 的用户只能看自己的订单，
 * 访问他人订单返回 NOT_FOUND(404)；超管（通配 list:all）可看任意订单。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderDetailIsolationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String login(String u, String p) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    private long menu(String t, String name, String perm) throws Exception {
        return postForId("/api/system/menus",
                "{\"name\":\"" + name + "\",\"type\":3,\"permCode\":\"" + perm + "\",\"status\":1}", t);
    }

    /** 建一个仅含 create+list（无 list:all）的角色并返回其 id。 */
    private long ownScopeRole(String su, long ts, String tag) throws Exception {
        long mCreate = menu(su, "det下单_" + tag + ts, "sales:order:create");
        long mList = menu(su, "det列表_" + tag + ts, "sales:order:list");
        return postForId("/api/system/roles",
                "{\"name\":\"DetRole_" + tag + ts + "\",\"code\":\"DETR_" + tag + ts
                        + "\",\"status\":1,\"menuIds\":[" + mCreate + "," + mList + "]}", su);
    }

    private long createUserWithRole(String su, long roleId, String uname) throws Exception {
        long userId = postForId("/api/system/users",
                "{\"username\":\"" + uname + "\",\"password\":\"Test@123\",\"status\":1}", su);
        mvc.perform(put("/api/system/users/" + userId + "/roles").header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[" + roleId + "]}"))
                .andExpect(jsonPath("$.code").value(0));
        return userId;
    }

    @Test
    void detail_is_isolated_per_salesperson() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();

        long supplierId = postForId("/api/suppliers", "{\"name\":\"DETSup_" + ts + "\",\"status\":1}", su);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"DETProd_" + ts
                        + "\",\"productCode\":\"DETC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":50}"))
                .andExpect(jsonPath("$.code").value(0));

        long roleA = ownScopeRole(su, ts, "A");
        long roleB = ownScopeRole(su, ts, "B");
        createUserWithRole(su, roleA, "detA_" + ts);
        createUserWithRole(su, roleB, "detB_" + ts);

        // A 下单
        String ta = login("detA_" + ts, "Test@123");
        long orderA = postForId("/api/sales-orders",
                "{\"customerName\":\"Kofi\",\"customerPhone\":\"024\",\"customerAddress\":\"Accra\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":1}]}", ta);

        // A 看自己的订单 -> 200/code=0
        mvc.perform(get("/api/sales-orders/" + orderA).header("Authorization", ta))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(orderA));

        // B 看 A 的订单 -> NOT_FOUND(404)，且无 data
        String tb = login("detB_" + ts, "Test@123");
        mvc.perform(get("/api/sales-orders/" + orderA).header("Authorization", tb))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 超管（通配 list:all）看 A 的订单 -> 200/code=0
        mvc.perform(get("/api/sales-orders/" + orderA).header("Authorization", su))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(orderA));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -Dtest=SalesOrderDetailIsolationTest test`
Expected: FAIL — the "B sees A's order" assertion gets `$.code == 0` (currently detail returns any order), not `404`.

- [ ] **Step 3: Add the ownership guard in the controller**

In `backend/src/main/java/africa/zokomart/admin/module/sales/controller/SalesOrderController.java`, add imports and replace the `detail` method.

Add these imports alongside the existing `common.result` / exception imports:

```java
import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
```

Replace the existing `detail` method (currently lines 60-64):

```java
    @GetMapping("/{id}")
    @SaCheckPermission("sales:order:list")
    public Result<SalesOrderVO> detail(@PathVariable Long id) {
        SalesOrderVO vo = salesOrderService.getDetail(id);
        // 非全局查看权限者只能看自己的订单；否则按 NOT_FOUND 处理避免 id 枚举（#隔离）
        if (!StpUtil.hasPermission(PERM_VIEW_ALL)
                && !StpUtil.getLoginIdAsLong().equals(vo.getSalespersonId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "销售订单不存在");
        }
        return Result.ok(vo);
    }
```

(`StpUtil`, `PERM_VIEW_ALL`, `SalesOrderVO`, `Result` are already imported/defined in this file.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q -Dtest=SalesOrderDetailIsolationTest test`
Expected: PASS.

- [ ] **Step 5: Run the existing sales tests to confirm no regression**

Run: `cd backend && mvn -q -Dtest=SalesOrderApiTest,SalesFlowServiceTest test`
Expected: PASS. (`SalesOrderApiTest` only fetches the salesperson's own order detail; `SalesFlowServiceTest` calls the service directly, whose signature is unchanged.)

- [ ] **Step 6: Commit**

```bash
cd backend
git add src/main/java/africa/zokomart/admin/module/sales/controller/SalesOrderController.java \
        src/test/java/africa/zokomart/admin/sales/SalesOrderDetailIsolationTest.java
git commit -m "feat(sales): enforce per-salesperson ownership on order detail"
```

---

## Task 2: Backend — V17 migration repurposing the two roles

Rename roles 904/905 and rebind their menus per the spec. Codes stay `SALES`/`LOGISTICS`.

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__support_roles.sql`

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V17__support_roles.sql`:

```sql
-- ===========================================================================
-- V17: 把 V7 的两个模板角色改造为「销售支持 / 物流支持」，并重绑菜单/权限。
--   904 销售员   -> 销售支持：平台目录(供应商产品查看) + 库存管理(库存列表只读)
--                  + 销售管理(下单/查看本人) + 物流管理(物流跟踪只读，仅本人订单)
--   905 物流专员 -> 物流支持：物流管理(物流跟踪，处理全部订单) +
--                  sales:order:list / sales:order:list:all（仅权限码，不进左侧菜单）
-- 角色 code（SALES/LOGISTICS）保持不变；仅改 name/remark + sys_role_menu。
-- 复用 V7 约定：role_menu.id = role_id*100000 + menu_id。
-- ===========================================================================

-- 1) 更新角色显示名/备注（code 不变）
UPDATE sys_role SET name = '销售支持',
    remark = '供应商产品/库存查看 + 销售下单/查看(仅本人) + 物流跟踪查看(仅本人)'
    WHERE id = 904;
UPDATE sys_role SET name = '物流支持',
    remark = '物流跟踪：处理全部订单的派送/状态/派送费/拒收/完成'
    WHERE id = 905;

-- 2) 清空这两个角色的旧菜单绑定（仅 904/905，其它角色不动）
DELETE FROM sys_role_menu WHERE role_id IN (904, 905);

-- 3) 销售支持(904) 绑定：目录+菜单(进左侧导航) + 按钮(权限码)
--    目录 1003 平台目录 / 1005 库存管理 / 1006 销售管理 / 1007 物流管理
--    菜单 1110 供应商产品 / 1113 库存列表 / 1114 销售订单 / 1115 物流跟踪
--    按钮 2038 supplierProduct:list / 2051 inventory:list
--         2054 sales:order:list / 2055 sales:order:create
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 904 * 100000 + m.id, 904, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    1003, 1005, 1006, 1007,
    1110, 1113, 1114, 1115,
    2038, 2051, 2054, 2055
);

-- 4) 物流支持(905) 绑定：
--    目录/菜单 1007 物流管理 + 1115 物流跟踪（左侧导航仅此一组）
--    按钮 2057/2058/2059/2060 logistics:dispatch/status/reject/complete
--    按钮 2054 sales:order:list + 2056 sales:order:list:all
--        （2054/2056 的父菜单 1114/1006 不绑定：前端 buildRoutes 过滤 type=3，
--          故只授予权限码、不产生「销售管理」菜单）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 905 * 100000 + m.id, 905, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    1007, 1115,
    2054, 2056,
    2057, 2058, 2059, 2060
);
```

- [ ] **Step 2: Rebuild the jar so the migration is packaged**

Run: `cd backend && mvn -q clean package -DskipTests`
Expected: BUILD SUCCESS; `target/*.jar` produced.

- [ ] **Step 3: Restart the backend and confirm Flyway applied V17**

Restart the running jar (per the runtime note), then check startup logs / DB:

Run (verify migration + resulting bindings):
```bash
cd backend
# adjust connection to your local dev DB
mysql -uroot -p"$DB_PW" zokomart_admin -e \
  "SELECT version, description, success FROM flyway_schema_history WHERE version='17';
   SELECT id, name, code FROM sys_role WHERE id IN (904,905);
   SELECT role_id, COUNT(*) cnt FROM sys_role_menu WHERE role_id IN (904,905) GROUP BY role_id;"
```
Expected: V17 row `success=1`; names `销售支持` / `物流支持`; `904 -> 12` bindings, `905 -> 8` bindings.

- [ ] **Step 4: Run the full backend test suite (Flyway runs against the test DB too)**

Run: `cd backend && mvn -q test`
Expected: PASS (Flyway migrates the test DB cleanly, V17 included).

- [ ] **Step 5: Commit**

```bash
cd backend
git add src/main/resources/db/migration/V17__support_roles.sql
git commit -m "feat(rbac): V17 repurpose seed roles into 销售支持/物流支持 with rebound menus"
```

---

## Task 3: Backend — manual HTTP verification of the two roles

Prove isolation and nav/permission scoping end-to-end against the running jar.
(No code; a verification checklist that must pass before moving to the frontend.)

**Files:** none.

- [ ] **Step 1: Assign the roles to two throwaway users (superadmin)**

Using the running app (UI 系统管理→用户管理, or HTTP): create `sup_sales` and
`sup_logi`, assign role `销售支持` (904) and `物流支持` (905) respectively.

- [ ] **Step 2: Verify 销售支持 own-scope + read-only**

Log in as `sup_sales`:
- Left nav shows exactly: 平台目录 / 库存管理 / 销售管理 / 物流管理 (no 采购管理, no 系统管理).
- Can open 供应商产品 (list) and 库存列表 (list) — no create/edit buttons on inventory.
- Can create a sales order; the 销售订单 list shows only their own orders.
- Open 物流跟踪: sees only their own orders; **no** 派送/更新状态/完成/拒收 buttons.
- `GET /api/sales-orders/{id}` for another user's order id returns `code=404`.

Expected: all of the above hold.

- [ ] **Step 3: Verify 物流支持 all-scope + logistics actions, nav = Logistics only**

Log in as `sup_logi`:
- Left nav shows exactly: 物流管理 (no 销售管理, no others).
- 物流跟踪 lists **all** orders (including those created by `sup_sales`).
- Can dispatch / update status (enter delivery fee) / reject / complete.
- Direct-navigating to `/sales/order` is not reachable (falls through to 404).

Expected: all of the above hold.

- [ ] **Step 4: Record results**

Note pass/fail for each check in the PR description. If any check fails, stop and
diagnose before proceeding (do not paper over with frontend changes).

---

## Task 4: Frontend — update the role-access e2e walk-through

The e2e picks roles by their Chinese display name and asserts nav visibility +
override interception. Update the two renamed labels and drop the now-fixed
known-gap comment.

**Files:**
- Modify: `frontend/tests/e2e/role-access.e2e.ts`

- [ ] **Step 1: Update the role labels and remove the stale gap note**

In `frontend/tests/e2e/role-access.e2e.ts`, replace the stale comment block
(currently lines 6-8, the "已知后端权限缺口: LOGISTICS ..." note) — delete those
three comment lines, since V17 grants `物流支持` both `sales:order:list` and
`sales:order:list:all`.

Then update the last two `CASES` entries so the labels match the new role names:

```ts
  { roleLabel: '销售支持', visibleMenu: '销售管理', hiddenMenu: '采购管理', forbiddenPath: '/purchase/plan', forbiddenText: '新增采购计划' },
  { roleLabel: '物流支持', visibleMenu: '物流管理', hiddenMenu: '销售管理', forbiddenPath: '/sales/order', forbiddenText: '新增销售订单' },
```

(The `采购员` and `仓库管理员` cases are unchanged. These assertions still hold:
`销售支持` keeps 销售管理 visible / 采购管理 hidden; `物流支持` keeps 物流管理
visible / 销售管理 hidden, and `/sales/order` stays unreachable because menu 1114
is not bound to it.)

- [ ] **Step 2: Run the e2e walk-through**

Prereq: backend running with V17 applied, frontend dev server up (dev proxy `/api`
→ backend). Run:

`cd frontend && pnpm exec playwright test tests/e2e/role-access.e2e.ts`
Expected: PASS for all four role cases, including the two renamed roles.

- [ ] **Step 3: Commit**

```bash
cd frontend
git add tests/e2e/role-access.e2e.ts
git commit -m "test(e2e): rename role-access cases to 销售支持/物流支持; drop fixed gap note"
```

---

## Task 5: Finish the branch

- [ ] **Step 1: Full backend suite once more**

Run: `cd backend && mvn -q test`
Expected: PASS.

- [ ] **Step 2: Frontend build/typecheck**

Run: `cd frontend && pnpm build`
Expected: build succeeds (no type errors from the e2e edit).

- [ ] **Step 3: Open PRs per repo**

Use the finishing-a-development-branch skill to open one PR in `backend`
(migration + controller guard + isolation test) and one in `frontend` (e2e rename).
Cross-link them; note in each body that they must be released together (frontend
e2e depends on the V17 role names).

---

## Notes on coverage vs. spec

- Spec §1 (V17 rebind) → Task 2. Spec §2 (detail ownership) → Task 1.
- Spec §3 (behavioural isolation) is verified by Task 1's automated test plus the
  Task 3 manual matrix.
- Spec "Testing" bullet about extending Sales Support/Logistics Support *behaviour*
  in e2e is intentionally handled by the **backend** isolation test (Task 1) plus
  the Task 3 manual matrix, not by adding data-dependent Playwright specs — the e2e
  layer stays a menu-visibility/override walk-through (Task 4) to avoid flaky,
  seed-dependent UI assertions. This is a deliberate scope decision, not a gap.
