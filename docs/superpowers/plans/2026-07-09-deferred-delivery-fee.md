# Deferred Delivery Fee Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow Dispatch with a blank Delivery Fee (stored NULL = unknown) and let the operator enter/correct the fee later via an optional prompt on the Signed / Signed & Paid / Rejected status actions in Logistics Tracking.

**Architecture:** No migration (`sales_order.delivery_fee` is already nullable). Backend relaxes `DispatchDTO`, adds optional `deliveryFee` to `StatusUpdateDTO`, and threads it through `SalesLogisticsService.updateStatus` (provided fee overwrites, null keeps existing — set before the REJECTED auto-complete branch). Frontend makes the dispatch fee field optional (default blank, not 0), turns the three outcome-status buttons into a small confirm modal with an optional fee input, and shows the fee in the handling drawer.

**Tech Stack:** SpringBoot 3.5 + MyBatis-Plus + Sa-Token (backend); Vue3 + Ant Design Vue + vitest (frontend).

**Spec:** `docs/superpowers/specs/2026-07-09-deferred-delivery-fee-design.md`

**Branches:** backend is already on `feat/deferred-delivery-fee` (branched from main, spec committed). Frontend: create the same branch from main (Task 1). Backend dir `D:\GHANA\claude\admin.zokomart.africa\backend`, frontend dir `D:\GHANA\claude\admin.zokomart.africa\frontend`.

**Environment:** backend builds need `export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11"`; MySQL + Redis must be running for `mvn test`. Semantics to keep in mind everywhere: `NULL` fee = unknown, `0` = confirmed free; a fee sent with a status update overwrites, absent/null keeps the existing value.

**Breaking-change checklist:** `SalesLogisticsService.updateStatus` gains a third parameter — `SalesFlowServiceTest` calls the old signature at lines ~122/143/160/174 and MUST be updated (Task 3). Frontend `apiLogisticsUpdateStatus` changes signature — `logistics-track-page.spec.ts` line ~124 asserts the old call shape and MUST be updated (Task 5).

---

### Task 1: Frontend branch

**Files:** none (git only)

- [ ] **Step 1: Branch frontend from main**

```bash
cd "D:\GHANA\claude\admin.zokomart.africa\frontend"
git checkout main && git checkout -b feat/deferred-delivery-fee
```

Expected: `git branch --show-current` → `feat/deferred-delivery-fee`. (If `feat/raw-order-import` is unmerged that's fine — this feature is independent and branches from main.)

---

### Task 2: Backend — failing integration test

**Files:**
- Create: `backend/src/test/java/africa/zokomart/admin/sales/DeferredDeliveryFeeApiTest.java`

- [ ] **Step 1: Write the test**

```java
package africa.zokomart.admin.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 延迟录入派送费：派送时可不填（NULL=未知），签收/拒签时可补录或修正；
 * 未提供时保留原值；负数 400。区域惯例：站点送达后才告知运费。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeferredDeliveryFeeApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String login() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}"))
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

    /** 建一个可派送订单（供应商+产品+库存+下单），返回 orderId。 */
    private long newDispatchableOrder(String t, String tag) throws Exception {
        long supplierId = postForId("/api/suppliers", "{\"name\":\"DDF_Sup_" + tag + "\",\"status\":1}", t);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"DDF_Prod_" + tag
                        + "\",\"productCode\":\"DDF_" + tag + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", t);
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
                .andExpect(jsonPath("$.code").value(0));
        return postForId("/api/sales-orders",
                "{\"customerName\":\"DDF\",\"customerPhone\":\"024\",\"customerAddress\":\"Accra\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":1}]}", t);
    }

    private JsonNode detail(String t, long orderId) throws Exception {
        MvcResult r = mvc.perform(get("/api/sales-orders/" + orderId).header("Authorization", t)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data");
    }

    @Test
    void dispatch_without_fee_then_fill_fee_at_sign() throws Exception {
        String t = login();
        long orderId = newDispatchableOrder(t, "A" + System.nanoTime());

        // 派送：不带 deliveryFee → 成功，detail 中为 null
        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logisticsProviderId\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(detail(t, orderId).get("deliveryFee").isNull()).isTrue();

        // 签收时补录 25.00
        mvc.perform(put("/api/sales-orders/" + orderId + "/status").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SIGNED\",\"deliveryFee\":25.00}"))
                .andExpect(jsonPath("$.code").value(0));
        JsonNode d = detail(t, orderId);
        assertThat(d.get("status").asText()).isEqualTo("SIGNED");
        assertThat(d.get("deliveryFee").decimalValue()).isEqualByComparingTo("25.00");

        // 再转 SIGNED_PAID 不带费用 → 保留 25.00
        mvc.perform(put("/api/sales-orders/" + orderId + "/status").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SIGNED_PAID\"}"))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(detail(t, orderId).get("deliveryFee").decimalValue()).isEqualByComparingTo("25.00");
    }

    @Test
    void rejected_with_fee_stores_fee_and_completes() throws Exception {
        String t = login();
        long orderId = newDispatchableOrder(t, "B" + System.nanoTime());
        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logisticsProviderId\":1}"))
                .andExpect(jsonPath("$.code").value(0));

        // 整单拒签同时补录派送费 18.00（拒签会自动完成并锁单，费用必须同调用写入）
        mvc.perform(put("/api/sales-orders/" + orderId + "/status").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"deliveryFee\":18.00}"))
                .andExpect(jsonPath("$.code").value(0));
        JsonNode d = detail(t, orderId);
        assertThat(d.get("status").asText()).isEqualTo("REJECTED");
        assertThat(d.get("completed").asInt()).isEqualTo(1);
        assertThat(d.get("deliveryFee").decimalValue()).isEqualByComparingTo("18.00");
    }

    @Test
    void negative_fee_rejected_on_both_endpoints() throws Exception {
        String t = login();
        long orderId = newDispatchableOrder(t, "C" + System.nanoTime());

        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logisticsProviderId\":1,\"deliveryFee\":-1}"))
                .andExpect(jsonPath("$.code").value(400));

        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logisticsProviderId\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(put("/api/sales-orders/" + orderId + "/status").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SIGNED\",\"deliveryFee\":-5}"))
                .andExpect(jsonPath("$.code").value(400));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd "D:\GHANA\claude\admin.zokomart.africa\backend" && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn test -Dtest=DeferredDeliveryFeeApiTest`
Expected: FAIL — first test's dispatch without `deliveryFee` returns `$.code` 400 (`派送费不能为空`), not 0.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/africa/zokomart/admin/sales/DeferredDeliveryFeeApiTest.java
git commit -m "test(logistics): failing tests for deferred delivery fee

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Backend — DTOs, service, controller

**Files:**
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/dto/DispatchDTO.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/dto/StatusUpdateDTO.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/service/SalesLogisticsService.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/service/impl/SalesLogisticsServiceImpl.java:44-62`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/controller/SalesLogisticsController.java:30-35`
- Modify: `backend/src/test/java/africa/zokomart/admin/sales/SalesFlowServiceTest.java` (4 call sites)

- [ ] **Step 1: DispatchDTO — fee optional**

Replace the `deliveryFee` field (delete the `@NotNull`):

```java
    /** 派送费可空：区域惯例是送达后站点才告知，NULL=未知（区别于 0=免费）。 */
    @DecimalMin(value = "0", message = "派送费不能为负")
    private BigDecimal deliveryFee;
```

Remove the now-unused `jakarta.validation.constraints.NotNull` import **only if** `logisticsProviderId` no longer uses it — it does use it, so keep the import.

- [ ] **Step 2: StatusUpdateDTO — add optional fee**

```java
package africa.zokomart.admin.module.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StatusUpdateDTO {
    @NotBlank(message = "目标状态不能为空")
    private String status;

    /** 可空：签收/拒签等 outcome 时顺带补录或修正派送费；null 保留原值。 */
    @DecimalMin(value = "0", message = "派送费不能为负")
    private BigDecimal deliveryFee;
}
```

- [ ] **Step 3: Service interface**

In `SalesLogisticsService`, change (add `java.math.BigDecimal` import if missing — the interface already imports it for `dispatch`):

```java
    void updateStatus(Long orderId, String targetStatus, BigDecimal deliveryFee);
```

- [ ] **Step 4: Service impl**

In `SalesLogisticsServiceImpl.updateStatus`, change the signature and set the fee after transition validation, **before** the REJECTED branch:

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long orderId, String targetStatus, BigDecimal deliveryFee) {
        SalesOrder order = requireOpen(orderId);
        var allowed = SalesConst.TRANSITIONS.getOrDefault(order.getStatus(), java.util.Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION,
                    "非法状态流转 " + order.getStatus() + "→" + targetStatus);
        }
        // outcome 时补录/修正派送费；null=未提供，保留原值
        if (deliveryFee != null) {
            order.setDeliveryFee(deliveryFee);
        }
        if (SalesConst.REJECTED.equals(targetStatus)) {
            rejectWholeOrder(order);
            return;
        }
        order.setStatus(targetStatus);
        if (SalesConst.SIGNED_STATES.contains(targetStatus) && order.getSignTime() == null) {
            order.setSignTime(LocalDateTime.now());
        }
        orderMapper.updateById(order);
    }
```

- [ ] **Step 5: Controller**

```java
    @PutMapping("/{id}/status")
    @SaCheckPermission("logistics:status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        logisticsService.updateStatus(id, dto.getStatus(), dto.getDeliveryFee());
        return Result.ok();
    }
```

- [ ] **Step 6: Fix SalesFlowServiceTest call sites**

The old 2-arg calls no longer compile. Update all four (lines ~122, ~143, ~160, ~174) to pass `null`:

```java
        assertThatThrownBy(() -> logisticsService.updateStatus(id, SalesConst.SIGNED, null))
```
```java
        logisticsService.updateStatus(id, SalesConst.SIGNED, null);
```
```java
        logisticsService.updateStatus(id, SalesConst.REJECTED, null);
```
```java
        logisticsService.updateStatus(id, SalesConst.SIGNED_PAID, null);
```

- [ ] **Step 7: Run the new test class, then the full suite**

Run: `mvn test -Dtest=DeferredDeliveryFeeApiTest`
Expected: 3/3 PASS.
Run: `mvn test`
Expected: BUILD SUCCESS, no failures (SalesFlowServiceTest and SalesOrderApiTest still green — the HTTP contract is backward compatible since `deliveryFee` in dispatch bodies is still accepted).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/sales src/test/java/africa/zokomart/admin/sales/SalesFlowServiceTest.java
git commit -m "feat(logistics): delivery fee optional at dispatch, enterable at outcome statuses

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Frontend — API signature, types, i18n

**Files:**
- Modify: `frontend/src/types/sales.d.ts` (LogisticsDispatchDTO, new LogisticsStatusUpdateDTO)
- Modify: `frontend/src/api/sales/logistics.ts`
- Modify: `frontend/src/locales/lang/en-US.ts` (inside `logistics.track`)
- Modify: `frontend/src/locales/lang/zh-CN.ts` (same keys)

- [ ] **Step 1: types/sales.d.ts**

Replace `LogisticsDispatchDTO` and add the status DTO next to it:

```ts
export interface LogisticsDispatchDTO {
  logisticsProviderId: Id;
  /** null = 未知（送达后再补录），区别于 0 = 免费 */
  deliveryFee: number | null;
}
export interface LogisticsStatusUpdateDTO {
  status: SalesStatus;
  deliveryFee?: number | null;
}
```

- [ ] **Step 2: api/sales/logistics.ts**

Update the import and `apiLogisticsUpdateStatus`:

```ts
import type { LogisticsDispatchDTO, LogisticsStatusUpdateDTO, RejectItemDTO } from '@/types/sales';
```

```ts
export const apiLogisticsUpdateStatus = (id: Id, dto: LogisticsStatusUpdateDTO) =>
  http.put<void>(`/sales-orders/${id}/status`, dto);
```

(`SalesStatus` is no longer imported here — remove it from the type import if unused.)

- [ ] **Step 3: i18n — add three keys inside the `logistics: { track: { ... } }` section**

`en-US.ts`:

```ts
      feeOptionalHint: 'Leave blank if unknown — enter it when the delivery completes',
      statusConfirmTitle: 'Update Status',
      deliveryFeeNowHint: 'Enter the courier fee now if known (optional)',
```

`zh-CN.ts` (same position):

```ts
      feeOptionalHint: '未知可留空，签收/拒签时再填',
      statusConfirmTitle: '更新状态',
      deliveryFeeNowHint: '如已知本单派送费可现在填写（可选）',
```

- [ ] **Step 4: Commit**

```bash
cd "D:\GHANA\claude\admin.zokomart.africa\frontend"
git add src/types/sales.d.ts src/api/sales/logistics.ts src/locales/lang/en-US.ts src/locales/lang/zh-CN.ts
git commit -m "feat(logistics): status update API carries optional delivery fee

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

(The build is temporarily broken here — `index.vue` still calls the old signature; fixed in Task 5.)

---

### Task 5: Frontend — track page (TDD)

**Files:**
- Modify: `frontend/tests/unit/logistics-track-page.spec.ts`
- Modify: `frontend/src/views/logistics/track/index.vue`

- [ ] **Step 1: Update + add specs**

In `logistics-track-page.spec.ts`:

(a) The last test (`转状态 / 完成：调用对应 api`, line ~114) — `doUpdateStatus` gains a fee argument and the API call shape changes. Replace the two lines:

```ts
    await vm.doUpdateStatus('SIGNED_PAID');
```
→
```ts
    await vm.doUpdateStatus('SIGNED_PAID', null);
```
and
```ts
    expect(apiLogisticsUpdateStatus).toHaveBeenCalledWith(6, 'SIGNED_PAID');
```
→
```ts
    expect(apiLogisticsUpdateStatus).toHaveBeenCalledWith(6, { status: 'SIGNED_PAID', deliveryFee: null });
```

(b) Append three new tests inside the `describe` block:

```ts
  it('派送：不填派送费 → 载荷 deliveryFee: null（NULL=未知）', async () => {
    setUser({ isSuper: 1 });
    const vm = mountPage().vm as any;
    await openWith(vm, { id: 7, status: 'PENDING_DISPATCH', items: [] });
    vm.openDispatch();
    vm.dispatchForm.logisticsProviderId = 5;
    // deliveryFee 保持 undefined（默认留空）
    await vm.doDispatch();
    await flushPromises();
    expect(apiLogisticsDispatch).toHaveBeenCalledWith(7, { logisticsProviderId: 5, deliveryFee: null });
  });

  it('SIGNED/SIGNED_PAID/REJECTED 点击打开费用确认弹窗而非直接调接口', async () => {
    setUser({ isSuper: 1 });
    const vm = mountPage().vm as any;
    await openWith(vm, { id: 8, status: 'DISPATCHING', deliveryFee: 12.5, items: [] });
    vm.onStatusClick('SIGNED');
    expect(apiLogisticsUpdateStatus).not.toHaveBeenCalled();
    expect(vm.statusModal.open).toBe(true);
    expect(vm.statusModal.target).toBe('SIGNED');
    expect(vm.statusModal.deliveryFee).toBe(12.5); // 预填已知费用
    vm.statusModal.deliveryFee = 30;
    await vm.doStatusConfirm();
    await flushPromises();
    expect(apiLogisticsUpdateStatus).toHaveBeenCalledWith(8, { status: 'SIGNED', deliveryFee: 30 });
    expect(vm.statusModal.open).toBe(false);
  });

  it('UNREACHABLE 仍为一键直发（不弹窗，deliveryFee: null）', async () => {
    setUser({ isSuper: 1 });
    const vm = mountPage().vm as any;
    apiSalesOrderGet.mockResolvedValue({ id: 9, status: 'DISPATCHING', items: [] });
    await vm.openDetail({ id: 9 });
    await flushPromises();
    vm.onStatusClick('UNREACHABLE');
    await flushPromises();
    expect(vm.statusModal.open).toBe(false);
    expect(apiLogisticsUpdateStatus).toHaveBeenCalledWith(9, { status: 'UNREACHABLE', deliveryFee: null });
  });
```

- [ ] **Step 2: Run to verify new tests fail**

Run: `pnpm vitest run tests/unit/logistics-track-page.spec.ts`
Expected: new tests FAIL (`onStatusClick is not a function`; dispatch payload `deliveryFee: 0` instead of `null`). The updated test (a) also fails until the page is changed.

- [ ] **Step 3: Implement in index.vue**

Script changes:

(1) Dispatch form — fee optional, default blank. Replace the dispatch block (`const dispatchOpen = ...` through `doDispatch`):

```ts
// 派送（派送费可留空：NULL=未知，送达后于签收/拒签时补录）
const dispatchOpen = ref(false);
const dispatchForm = reactive<{ logisticsProviderId?: Id; deliveryFee?: number }>({});
function openDispatch() {
  dispatchForm.logisticsProviderId = undefined;
  dispatchForm.deliveryFee = undefined;
  dispatchOpen.value = true;
}
async function doDispatch() {
  if (dispatchForm.logisticsProviderId == null) {
    message.warning(t('logistics.track.selectProvider'));
    return;
  }
  acting.value = true;
  try {
    await apiLogisticsDispatch(detail.value!.id, {
      logisticsProviderId: dispatchForm.logisticsProviderId,
      deliveryFee: dispatchForm.deliveryFee ?? null,
    });
    message.success(t('logistics.track.dispatched'));
    dispatchOpen.value = false;
    await reloadDetail();
  } finally {
    acting.value = false;
  }
}
```

(2) Status flow — replace `doUpdateStatus` with click-router + confirm modal + updated API call:

```ts
// 状态流转：outcome（签收/签收已付/拒签）弹费用确认框，其余一键直发
const FEE_PROMPT_STATES: SalesStatus[] = ['SIGNED', 'SIGNED_PAID', 'REJECTED'];
const statusModal = reactive<{ open: boolean; target: SalesStatus | null; deliveryFee?: number }>({
  open: false,
  target: null,
});
function onStatusClick(s: SalesStatus) {
  if (FEE_PROMPT_STATES.includes(s)) {
    statusModal.target = s;
    statusModal.deliveryFee = detail.value?.deliveryFee ?? undefined;
    statusModal.open = true;
  } else {
    doUpdateStatus(s, null);
  }
}
async function doUpdateStatus(status: SalesStatus, deliveryFee: number | null) {
  acting.value = true;
  try {
    await apiLogisticsUpdateStatus(detail.value!.id, { status, deliveryFee });
    message.success(t('logistics.track.statusUpdatedTo', { label: STATUS.value[status].label }));
    await reloadDetail();
  } finally {
    acting.value = false;
  }
}
async function doStatusConfirm() {
  if (!statusModal.target) return;
  await doUpdateStatus(statusModal.target, statusModal.deliveryFee ?? null);
  statusModal.open = false;
}
```

(3) Extend `defineExpose`:

```ts
defineExpose({
  openDetail, openDispatch, doDispatch, doUpdateStatus, openReject, doReject, doComplete,
  onStatusClick, doStatusConfirm, dispatchForm, statusModal,
});
```

(Note: `dispatchForm` was previously reachable in specs via the proxy; adding it and `statusModal` to expose makes it explicit.)

Template changes:

(4) Status buttons — route through `onStatusClick`:

```html
            <a-button
              v-for="s in statusTargets"
              :key="s"
              v-perm="'logistics:status'"
              :loading="acting"
              :data-test="`track-to-${s}`"
              @click="onStatusClick(s)"
            >
              {{ t('logistics.track.toStatus', { label: STATUS[s].label }) }}
            </a-button>
```

(5) Drawer descriptions — add a fee row after the `receivedGhs` item:

```html
          <a-descriptions-item :label="t('logistics.track.deliveryFeeGhs')">
            {{ detail.deliveryFee != null ? money(detail.deliveryFee) : '—' }}
          </a-descriptions-item>
```

(6) Dispatch modal — fee no longer required, add hint:

```html
        <a-form-item :label="t('logistics.track.deliveryFeeGhs')" :extra="t('logistics.track.feeOptionalHint')">
          <a-input-number v-model:value="dispatchForm.deliveryFee" :min="0" :precision="2" class="w-full" data-test="dispatch-fee" />
        </a-form-item>
```

(7) New status confirm modal — add after the reject modal:

```html
    <!-- 状态确认 + 补录派送费弹窗（SIGNED / SIGNED_PAID / REJECTED） -->
    <a-modal v-model:open="statusModal.open" :title="t('logistics.track.statusConfirmTitle')" :confirm-loading="acting" @ok="doStatusConfirm">
      <a-form layout="vertical">
        <a-form-item :label="t('common.status')">
          <a-tag v-if="statusModal.target" :color="STATUS[statusModal.target].color">
            {{ STATUS[statusModal.target].label }}
          </a-tag>
        </a-form-item>
        <a-form-item :label="t('logistics.track.deliveryFeeGhs')" :extra="t('logistics.track.deliveryFeeNowHint')">
          <a-input-number v-model:value="statusModal.deliveryFee" :min="0" :precision="2" class="w-full" data-test="status-fee" />
        </a-form-item>
      </a-form>
    </a-modal>
```

- [ ] **Step 4: Run the page spec, then full suite + build**

Run: `pnpm vitest run tests/unit/logistics-track-page.spec.ts`
Expected: 10/10 PASS (7 existing incl. the updated one + 3 new).
Run: `pnpm vitest run && pnpm build`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add src/views/logistics/track/index.vue tests/unit/logistics-track-page.spec.ts
git commit -m "feat(logistics): optional delivery fee at dispatch + fee prompt on outcome statuses

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: End-to-end smoke verification

**Files:** none

- [ ] **Step 1: Rebuild and restart the backend jar**

The running jar locks `target/` — kill it first:

```bash
netstat -ano | grep ":8081" | grep LISTENING   # 找 PID
taskkill //PID <pid> //F
cd "D:\GHANA\claude\admin.zokomart.africa\backend"
export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11"
mvn clean package -DskipTests
"$JAVA_HOME/bin/java" -jar target/admin-1.0.0.jar > run.log 2>&1 &
```

Confirm `curl -s http://localhost:8081/api/ping` → pong.

- [ ] **Step 2: Drive the UI** (frontend dev via `.claude/launch.json` `frontend-dev`, port 5199, superadmin)

1. Sales 下单 (or reuse a PENDING_DISPATCH order) → Logistics → Logistics Tracking → 处理.
2. Dispatch with provider selected, fee left blank → succeeds; drawer fee row shows `—`.
3. Click 转为已签收 → modal opens with blank fee → enter 25 → confirm → status SIGNED, drawer fee row shows 25.00.
4. Click 转为签收已付 → modal opens **pre-filled with 25** → confirm unchanged → fee stays 25.00.
5. On another dispatched order, click 转为已拒签 → modal opens → enter a fee → confirm → order REJECTED + completed, fee stored.
6. UNREACHABLE (from a DISPATCHING order) still fires one-click without a modal.

Expected: all six checks pass.

- [ ] **Step 3: Final full test runs**

```bash
cd "D:\GHANA\claude\admin.zokomart.africa\backend" && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn test
cd "D:\GHANA\claude\admin.zokomart.africa\frontend" && pnpm vitest run && pnpm build
```

Expected: everything green. Hand off to superpowers:finishing-a-development-branch (`feat/deferred-delivery-fee` in both repos).
