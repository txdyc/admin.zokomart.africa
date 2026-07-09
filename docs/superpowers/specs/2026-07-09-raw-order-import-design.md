# Raw Order CSV Import — Design

Date: 2026-07-09
Scope: backend (`admin.zokomart.africa.git`) + frontend (`front.admin.zokomart.africa.git`)

## Goal

Add a **Raw Order Import** feature to a new **Orders** module in the admin panel.
Administrators upload a CSV file of original ("raw") order data; each valid row becomes a
new `raw_order` record. Invalid rows are skipped and reported; the import never aborts
mid-file. Raw orders are stored **independently of the existing `sales_order`** workflow
(different fields, different status vocabulary).

## Decisions (confirmed with user)

1. **New `raw_order` table/module** — not mapped into `sales_order`.
2. **List page + import** — a paginated Raw Orders list page hosts the Import button.
3. **No dedupe** — every valid row inserts a new record; re-importing duplicates is the
   operator's responsibility (CSV has no natural unique key).
4. **Strict formats** — `date` must be `yyyy-MM-dd`; `price`/`cod`/`freight`/`balance`
   decimal ≥ 0; `quantity` integer ≥ 1.

## CSV Contract

- UTF-8 (BOM tolerated and stripped), first row = header, header names must match exactly.
- All 14 columns required per row: `date, brand, price, customer_name, city, address,
  telephone, product_name, product_code, quantity, status, cod, freight, balance`.
- Missing any required header → whole file rejected (`IMPORT_FILE_INVALID`).
- More than 1000 data rows → rejected (`IMPORT_TOO_MANY_ROWS`).

### Status values

| CSV value | Display text |
|---|---|
| `PAID` | Paid |
| `RECIPIENT_REFUSED` | Refused by Recipient |
| `UNABLE_TO_CONTACT_RECIPIENT` | Unable to Contact Recipient |
| `RECIPIENT_UNABLE_TO_PAY` | Recipient Unable to Pay |

Any other value → row-level validation error.

## Backend

New module `africa.zokomart.admin.module.raworder` (standard layering:
controller / service+impl / mapper / entity / dto / vo / constant).

### Migration `V15__raw_order.sql`

Table `raw_order` (InnoDB, utf8mb4), all business columns `NOT NULL`:

| column | type |
|---|---|
| id | BIGINT PK (snowflake) |
| order_date | DATE |
| brand | VARCHAR(128) |
| price | DECIMAL(12,2) |
| customer_name | VARCHAR(128) |
| city | VARCHAR(128) |
| address | VARCHAR(512) |
| telephone | VARCHAR(32) |
| product_name | VARCHAR(255) |
| product_code | VARCHAR(64) |
| quantity | INT |
| status | VARCHAR(40) |
| cod | DECIMAL(12,2) |
| freight | DECIMAL(12,2) |
| balance | DECIMAL(12,2) |
| (audit) | create_time / update_time / create_by / update_by / deleted / version |

Indexes: `idx_raw_order_date (order_date)`, `idx_raw_order_status (status)`,
`idx_raw_order_telephone (telephone)`.

Menu seed (same migration, follows V12 pattern):

- 1009 / parent 0: directory `订单管理`, route `/order`
- 1117 / parent 1009: page `原始订单`, route `/order/raw`, component `order/raw/index`
- 2066 / parent 1117: button perm `raw-order:list`
- 2067 / parent 1117: button perm `raw-order:import`

Superadmin sees via wildcard; other roles granted later via role management.

### Endpoints

| Method & path | Perm | Behavior |
|---|---|---|
| `GET /api/raw-orders` | `raw-order:list` | Paginated list. Filters: order_date range, status, brand (like), keyword (customer_name / telephone like). Returns `PageResult<RawOrderVO>`. |
| `POST /api/raw-orders/import` | `raw-order:import` | Multipart CSV upload → `RawOrderImportResultVO`. |

### Import service (`RawOrderImportService`)

Mirrors `SupplierProductImportServiceImpl`: Apache Commons CSV, BOM strip, header check,
row cap, per-row try/catch. Per row:

1. All 14 fields non-empty (trimmed).
2. `date` parses as strict `yyyy-MM-dd` (`LocalDate`, `ResolverStyle.STRICT`).
3. `price`/`cod`/`freight`/`balance` parse as `BigDecimal`, ≥ 0.
4. `quantity` parses as int, ≥ 1.
5. `status` ∈ the 4 allowed codes (`RawOrderStatus` constants).
6. Valid → insert `raw_order` (audit fields via MetaObjectHandler).
7. Invalid → `errors += {line, ref: product_code, reason}`, continue.

Result VO: `{ total, success, failed, errors: [{line, ref, reason}] }`
(`total = success + failed`). Row insertion is per-row, no wrapping transaction —
a later row's failure must not roll back earlier successes.

## Frontend

- `src/api/order/rawOrder.ts` — `getRawOrderPage(params)`, `importRawOrders(file)`
  (FormData upload).
- `src/views/order/raw/index.vue` — Vben table page: filter form (date range, status
  select, brand, keyword), table columns for all business fields, status rendered as a
  colored tag with the display text from the table above. Import button gated by
  `raw-order:import` permission code.
- `src/views/order/raw/RawOrderImportModal.vue` — modeled on
  `SupplierProductImportModal.vue`: pick `.csv`, upload, show summary counts
  (total / success / failed) and a per-row error table (line, product_code, reason);
  refresh list on success.
- Route/menu is backend-driven (component string `order/raw/index`); snowflake IDs
  follow the existing string-serialization convention.

## Error handling

- File-level failures (empty file, bad headers, too many rows) → `BusinessException`
  with existing `ResultCode.IMPORT_FILE_INVALID` / `IMPORT_TOO_MANY_ROWS`, surfaced by
  the global exception handler.
- Row-level failures never abort the import; they are collected and returned.
- No secrets/PII in logs; row errors carry only line number, product_code, and reason.

## Testing

Backend (`mvn test`), service-level tests for `RawOrderImportService`:

- Happy path: all rows import, counts correct.
- Header missing a required column → `IMPORT_FILE_INVALID`.
- Row errors, one test each: empty required field, invalid status, bad date format,
  non-numeric / negative amount, quantity 0. Failed rows reported with correct line
  numbers; valid rows in the same file still insert.
- BOM-prefixed file parses fine.
- 1001 rows → `IMPORT_TOO_MANY_ROWS`.

Frontend: `pnpm build` passes; existing vitest suite stays green.

## Out of scope

- Editing/deleting raw orders, dedupe, async/job-based import, conversion of raw
  orders into `sales_order`, export.
