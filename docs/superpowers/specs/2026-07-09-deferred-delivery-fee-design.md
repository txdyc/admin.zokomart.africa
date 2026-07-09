# Deferred Delivery Fee (Logistics Tracking) — Design

Date: 2026-07-09
Scope: backend (`admin.zokomart.africa.git`) + frontend (`front.admin.zokomart.africa.git`)
Module: sales/logistics（物流跟踪 Logistics → Logistics Tracking）

## Goal

Delivery stations in the region typically only communicate the courier fee once the
delivery is finished. Therefore:

1. **Dispatch** may proceed with the Delivery Fee left blank (stored as `NULL` =
   "unknown", distinct from a real 0).
2. The fee can be entered later, when the operator records the delivery outcome —
   **Signed**, **Signed & Paid**, or **Rejected** — via an optional prompt on those
   status actions.

## Decisions (confirmed with user)

1. Fee prompt at outcome statuses is **optional** — the operator may confirm without a
   fee; completing an order never requires a fee.
2. The prompt appears on **SIGNED, SIGNED_PAID, and REJECTED** (rejected deliveries
   usually still incur a courier fee; REJECTED also auto-completes the order, so the fee
   must ride the same call). UNREACHABLE and resume-DISPATCHING stay one-click.

## Semantics

- `delivery_fee` column is already nullable — **no migration**.
- `NULL` = fee unknown; `0` = confirmed free. The dispatch UI defaults to blank, not 0.
- A fee provided at a status update **overwrites** any previously stored fee (later
  information from the station wins). A `null`/absent fee keeps the existing value.
- No new permissions: dispatch stays under `logistics:dispatch`, status+fee under
  `logistics:status`.

## Backend

### `DispatchDTO`

Remove `@NotNull` from `deliveryFee`; keep `@DecimalMin(value = "0")`. Null allowed.

### `StatusUpdateDTO`

Add:

```java
@DecimalMin(value = "0", message = "派送费不能为负")
private BigDecimal deliveryFee; // 可空：outcome 时可顺带填写/修正派送费
```

### `SalesLogisticsService.updateStatus`

Signature becomes `updateStatus(Long orderId, String targetStatus, BigDecimal deliveryFee)`.
In the impl, after the transition validation and **before** the `REJECTED` branch:

```java
if (deliveryFee != null) {
    order.setDeliveryFee(deliveryFee);
}
```

(The REJECTED branch persists the order via `finish(...)`, so setting the fee first
covers that path too.)

Controller passes `dto.getDeliveryFee()` through. `dispatch(...)` is unchanged apart
from now receiving null when the fee is blank.

## Frontend (`views/logistics/track/index.vue`)

### Dispatch modal

- `dispatchForm.deliveryFee` type `number | undefined`, default `undefined` (currently
  defaults to 0 — change it; 0 pollutes "unknown").
- Form item no longer marked required; placeholder/extra hint
  `logistics.track.feeOptionalHint`（未知可留空，签收时再填 / 'Leave blank if unknown —
  enter it when the delivery completes').
- Submit sends `deliveryFee: dispatchForm.deliveryFee ?? null`; the existing `< 0` local
  check drops (input already has `min: 0`, and blank is legal).

### Status actions

- New small modal (status confirm): opened by the SIGNED / SIGNED_PAID / REJECTED
  buttons instead of firing immediately. Contents: the target status name + one optional
  `a-input-number` Delivery Fee field (min 0, precision 2), **pre-filled with
  `detail.deliveryFee ?? undefined`**. OK → `apiLogisticsUpdateStatus(id, { status,
  deliveryFee: value ?? null })` → toast → refresh detail + list.
- UNREACHABLE and DISPATCHING targets keep the current direct one-click behavior
  (they call the same API with `deliveryFee: null`).
- Side effect (intended): REJECTED, which irreversibly completes the order, now has a
  confirmation step it previously lacked.

### Drawer

Add a Delivery Fee row to the descriptions block: `detail.deliveryFee != null ?
money(detail.deliveryFee) : '—'`, label `logistics.track.deliveryFeeGhs` (existing key).

### API + types

- `apiLogisticsUpdateStatus(id: Id, dto: { status: SalesStatus; deliveryFee?: number | null })`
  — body `{ status, deliveryFee }`.
- `LogisticsDispatchDTO.deliveryFee: number | null`.
- New i18n keys (both locales): `logistics.track.feeOptionalHint`,
  `logistics.track.statusConfirmTitle`（更新状态 / Update Status）,
  `logistics.track.deliveryFeeNowHint`（如已知本单派送费可现在填写 / 'Enter the courier
  fee now if known'）.

## Error handling

- Negative fee → 400 via jakarta validation (both endpoints), toasted by the Axios
  interceptor.
- All existing transition/permission errors unchanged.

## Testing

Backend — new `src/test/java/africa/zokomart/admin/sales/DeferredDeliveryFeeApiTest.java`
(follow the MockMvc + superadmin login pattern of `SalesOrderApiTest`; reuse its
data-setup approach for creating a dispatchable order):

- Dispatch with `deliveryFee` absent → order dispatched, `deliveryFee` null in detail VO.
- Dispatch with fee → stored (regression).
- Status → SIGNED with fee 25.00 → fee stored, status SIGNED.
- Status → SIGNED_PAID without fee → previous fee kept.
- Status → REJECTED with fee → fee stored AND order completed (`completed=1`).
- Negative fee on either endpoint → code 400.

Frontend (vitest, extend the logistics track page spec):

- `doDispatch` with blank fee sends `deliveryFee: null`.
- SIGNED button opens the status modal (doesn't call the API immediately); confirming
  sends `{ status: 'SIGNED', deliveryFee }`.
- UNREACHABLE button still calls the API directly.

Full `mvn test` + `pnpm vitest run` + `pnpm build`; browser walkthrough:
dispatch-with-blank-fee → sign-with-fee → fee visible in drawer.

## Out of scope

- Editing the fee after order completion, fee change history, freight reporting.
- Resetting a stored fee back to "unknown" (NULL): a provided fee overwrites, an absent
  fee keeps the value — once set, the fee can only be changed to another number
  (including 0 = free), never cleared. Operator complaints about this are by design.
