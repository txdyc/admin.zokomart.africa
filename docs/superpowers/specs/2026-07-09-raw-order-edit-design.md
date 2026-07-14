# Raw Order Row Edit — Design

Date: 2026-07-09
Scope: backend (`admin.zokomart.africa.git`) + frontend (`front.admin.zokomart.africa.git`)
Builds on: `2026-07-09-raw-order-import-design.md` (branch `feat/raw-order-import` in both repos)

## Goal

Let administrators edit any imported raw order directly from the Raw Orders list and save
the changes. Editing opens a modal form pre-filled with the row; all 14 business fields
are editable; validation matches the CSV import rules.

## Decisions (confirmed with user)

1. **Edit button → modal form** (codebase's standard `a-modal` + `SchemaForm` pattern),
   not inline cell editing.
2. **All 14 business fields editable**: date, brand, price, customer_name, city, address,
   telephone, product_name, product_code, quantity, status, cod, freight, balance.
3. Validation identical to import: strict date, amounts ≥ 0, quantity ≥ 1, status one of
   the 4 allowed values, all fields required.

## Backend

### Migration `V16__raw_order_update_perm.sql`

One button menu row (no table change):

- id **2068**, parent 1117 (原始订单 page), type 3, perm_code `raw-order:update`,
  name 编辑原始订单, sort 3. Superadmin sees via wildcard; other roles granted in role
  management.

### `RawOrderUpdateDTO` (`module/raworder/dto/`)

All 14 fields, `jakarta.validation` annotations:

| field | type | validation |
|---|---|---|
| orderDate | LocalDate | `@NotNull` (JSON `yyyy-MM-dd`) |
| brand | String | `@NotBlank @Size(max=128)` |
| price | BigDecimal | `@NotNull @DecimalMin("0")` |
| customerName | String | `@NotBlank @Size(max=128)` |
| city | String | `@NotBlank @Size(max=128)` |
| address | String | `@NotBlank @Size(max=512)` |
| telephone | String | `@NotBlank @Size(max=32)` |
| productName | String | `@NotBlank @Size(max=255)` |
| productCode | String | `@NotBlank @Size(max=64)` |
| quantity | Integer | `@NotNull @Min(1)` |
| status | String | `@NotBlank` + service check against `RawOrderStatus.ALL` |
| cod | BigDecimal | `@NotNull @DecimalMin("0")` |
| freight | BigDecimal | `@NotNull @DecimalMin("0")` |
| balance | BigDecimal | `@NotNull @DecimalMin("0")` |

### Endpoint

| Method & path | Perm | Behavior |
|---|---|---|
| `PUT /api/raw-orders/{id}` | `raw-order:update` | Validate DTO (`@Valid`), service updates the row, returns `Result<Void>`. |

Service `RawOrderService.update(Long id, RawOrderUpdateDTO dto)`:

1. `selectById(id)` → `BusinessException(NOT_FOUND, "原始订单不存在")` if null (logical
   delete respected by MP).
2. `status` not in `RawOrderStatus.ALL` → `BusinessException(BAD_REQUEST, "status 非法: …")`.
3. Copy the 14 DTO fields onto the loaded entity, `updateById` — audit fields auto-filled
   by MetaObjectHandler, optimistic-lock `version` preserved from the loaded entity.

Import logic is untouched.

## Frontend

### `SchemaForm` extension (additive, reusable)

New `component: 'date'` renders `a-date-picker` with `value-format="YYYY-MM-DD"`
(`class="w-full"`, passes `f.props` through). No behavior change for existing forms.

### Raw Orders page (`views/order/raw/index.vue`)

- New fixed-right **操作** column with an **编辑** link, `v-perm="'raw-order:update'"`,
  `data-test="raw-order-edit"` (also add the missing `data-test="raw-order-import"` /
  `data-test="raw-order-search"` attributes while touching the page).
- Click → edit modal (`a-modal` + `SchemaForm`) pre-filled from the row:
  - `orderDate`: date component;
  - `status`: select with the 4 options (localized display text);
  - `price`/`cod`/`freight`/`balance`: number, `min: 0`, `precision: 2`;
  - `quantity`: number, `min: 1`, `precision: 0`;
  - the 8 text fields: input, required rules.
- Save → `SchemaForm.validate()` → `apiRawOrderUpdate(id, values)` → success toast →
  close modal, `tableRef.reload()` (keeps current filters/page).

### API + types

- `apiRawOrderUpdate = (id: Id, dto: RawOrderUpdateDTO) => http.put<void>(`/raw-orders/${id}`, dto)`.
- `RawOrderUpdateDTO` type in `types/order.d.ts` (14 fields).

### i18n

`rawOrder.editTitle` (编辑原始订单 / Edit Raw Order) plus a generic required-field rule
reusing existing `common` keys where possible; both `zh-CN` and `en-US` updated together.

## Error handling

- Validation failures → global exception handler returns business code + message; the
  Axios interceptor toasts it. No special frontend handling.
- Unknown id → `NOT_FOUND` business error toast.
- No secrets/PII in logs.

## Testing

Backend (`RawOrderApiTest` gains cases or a sibling test class):

- Import one row → `PUT` a full valid update → `GET` shows the new values.
- `PUT` with status `SHIPPED` → business error (400xx), row unchanged.
- `PUT` unknown id → `NOT_FOUND` code.
- `PUT` with blank `customer_name` → validation error code.

Frontend (vitest):

- Page spec: `openEdit(row)` pre-fills the form initial; submit calls
  `apiRawOrderUpdate` with the row id and edited payload.
- SchemaForm spec: `date` component renders and round-trips a `YYYY-MM-DD` string.

Full `mvn test` + `pnpm vitest run` + `pnpm build` green; manual UI smoke: edit a row in
the browser, verify the list reflects the change.

## Out of scope

- Delete, bulk edit, edit history/audit UI, the import modal's `.csv` extension check
  (tracked separately from the verification findings).
