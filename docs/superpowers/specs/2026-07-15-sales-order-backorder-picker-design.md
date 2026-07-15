# Design: Sales-order picker searches all products (in-stock first) + backorder

**Date:** 2026-07-15
**Status:** Approved (brainstorming)
**Repos touched:** `backend` (new read endpoint + relax sales-out stock check + tests), `frontend` (sales-order create picker)

## Goal

In the "Add Sales Order" drawer on `/sales/order`, a salesperson can currently only
pick products that already have inventory. Change it so the picker searches **all**
products in the Supplier Products list, showing **in-stock products first** and
out-of-stock ones after — and let out-of-stock products be **backordered** (added to
the order even when stock is insufficient).

## Decisions (from brainstorming)

- **Backorder is allowed.** Ordering an out-of-stock / under-stocked product succeeds;
  inventory stock is allowed to go **negative** for the sales-out movement (negative =
  owed, to be procured). This overrides today's `INSUFFICIENT_STOCK` block, for the
  sales path only.
- **New dedicated endpoint** drives the picker (product-driven join), rather than
  overloading `/api/inventory/stocks` or sorting client-side.

## Current behaviour (what exists today)

- The picker fetcher is `apiStockPage` → `GET /api/inventory/stocks`
  (`InventoryStockServiceImpl.pageStocks`), which **pages over the `inventory_stock`
  table**. `inventory_stock` rows are created **lazily** on first inbound/adjust
  (`changeStock` → `createStock`), so a product that has never been stocked has no row
  and never appears; a depleted product appears with `quantity = 0` but the qty input
  is capped at `record.quantity`, so it can't be added.
- `SalesOrderServiceImpl.create` pre-checks `stockService.getQty(spId) < qty` and
  throws `INSUFFICIENT_STOCK`, then calls
  `changeStock(spId, -qty, TYPE_SALES_OUT, REF_SALES_ORDER, orderId, orderNo, "销售出库")`.
  `changeStock` throws `INSUFFICIENT_STOCK` whenever the resulting quantity would be `< 0`.
- The frontend caps the qty input at `record.quantity` and flags `qty > stock` as
  `rowInvalid`, which blocks submit (`hasInvalid` → `canSubmit`). Adding a row fetches
  the product's `retailPrice` via a separate `apiSupplierProductGet` call.

## Design

### 1. Backend — new endpoint `GET /api/sales-orders/orderable-products`

- **Guard:** `@SaCheckPermission("sales:order:create")` (only order-creators use the
  picker; `销售支持`/Sales Support already holds this).
- **Params:** `supplierId?`, `brandId?`, `categoryId?`, `keyword?`, `current` (default 1),
  `size` (default 10) — the same shape the picker's `CascadeFilter` + keyword produce.
- **Returns:** `PageResult<OrderableProductVO>` with fields:
  `supplierProductId`, `productName`, `productCode`, `supplierName`, `quantity` (int,
  `COALESCE(stock, 0)`), `retailPrice` (BigDecimal, nullable).
- **Query (XML mapper, MP `IPage` pagination, following `CustomerMapper.xml`):**
  page over
  `supplier_product sp
     LEFT JOIN inventory_stock st ON st.supplier_product_id = sp.id AND st.deleted = 0
     LEFT JOIN supplier sup ON sup.id = sp.supplier_id`
  filtered by `sp.deleted = 0 AND sp.status = 1`, plus optional
  `sp.supplier_id / sp.brand_id / sp.category_id` equals and
  `keyword` LIKE on `sp.name` / `sp.product_code`.
  **Ordering: `(COALESCE(st.quantity,0) > 0) DESC, sp.name ASC`** → in-stock first,
  then out-of-stock, alphabetical within each group for stable paging.
- **Placement:** lives in the **sales module** (it is the order picker) — a new
  `OrderableProductMapper` (+ `mapper/OrderableProductMapper.xml`), a service method,
  and a `SalesOrderController` endpoint. The query reads `supplier_product` /
  `inventory_stock` (cross-module read via SQL is acceptable here).

### 2. Backend — allow backorder on the sales-out movement

- **`InventoryStockService.changeStock`:** add a variant that takes
  `boolean allowNegative`. When `true`, the `after < 0` guard is skipped — an existing
  row is updated to the negative value (optimistic-lock retry unchanged), and when no
  row exists a new row is created with the negative quantity. The **existing signature
  is kept** and delegates with `allowNegative = false`, so every other caller
  (manual adjust, purchase inbound, etc.) is byte-for-byte unchanged and still
  protected against going negative.
- **`SalesOrderServiceImpl.create`:** remove the `getQty(spId) < qty` pre-check and its
  `INSUFFICIENT_STOCK` throw; call the sales-out `changeStock(..., allowNegative = true)`.
  The order is created, stock may go negative, and the `SALES_OUT` inventory
  transaction records the movement (before/after may be negative) for procurement
  visibility.

### 3. Frontend — `views/sales/order/index.vue`

- **New api + type:** `apiOrderableProductsPage(query)` → `/sales-orders/orderable-products`
  in `@/api/sales/order`, returning `PageResult<OrderableProductVO>`; add
  `OrderableProductVO` to `@/types/sales`.
- **Picker fetcher:** swap `apiStockPage` → `apiOrderableProductsPage` (same
  `stockQuery` params from `CascadeFilter`). The picker's stock column reads
  `record.quantity` as before (now possibly 0).
- **Qty input:** remove `:max="record.quantity"` (backorder allowed); keep `min 0`,
  integer precision.
- **Pricing:** `setQty` uses `row.retailPrice` from the VO directly, dropping the
  extra per-row `apiSupplierProductGet` call.
- **Validation:** `qty > stock` is no longer invalid/blocking. Remove that from
  `rowInvalid` / `hasInvalid` (keep `qty >= 1`); `canSubmit` no longer blocks on
  exceeding stock. Show a **non-blocking "backorder" tag** in the picker row and cart
  when `quantity <= 0` or `qty > quantity`, so staff see the item is out of stock but
  can still submit. Add the i18n key(s) for that label.
- `CascadeFilter` is unchanged.

### Data flow

Picker → `GET /sales-orders/orderable-products` (in-stock first) → add to cart (any
qty) → submit → `POST /sales-orders` → order created; sales-out `changeStock` allows
negative; `SALES_OUT` transaction written.

## Testing

- **Backend**
  - `orderable-products`: returns all `status=1` products including never-stocked and
    depleted; in-stock rows sort before out-of-stock; `supplierId`/`brand`/`category`
    filters and `keyword` work; `quantity` is 0 when no stock row.
  - Create an order for an out-of-stock product → succeeds; stock ends negative; a
    `SALES_OUT` transaction exists with the expected delta.
  - Invariant guard: a **manual stock adjust** that would go negative still throws
    `INSUFFICIENT_STOCK` (proves `allowNegative` defaults off elsewhere).
  - Existing `SalesOrderApiTest` / `SalesFlowServiceTest` still pass (the happy-path
    in-stock flow is unchanged).
- **Frontend:** `pnpm build` (typecheck); manually/e2e verify the picker lists
  out-of-stock products with the backorder tag, allows entering a qty, and submits.

## Bundled fix — V18: Sales Support basedata read (supplier search)

Rolled into this work because it touches the same sales-support surface. `销售支持`
(role 904) currently can't filter by supplier/brand/category on
`/product/supplier-product` (and in the `CascadeFilter` used by this very picker),
because that page's on-mount lookups call `GET /api/suppliers` (`supplier:list`),
`GET /api/brands` (`brand:list`), and `GET /api/categories/tree` (`category:list`),
none of which role 904 holds — so the dropdowns 403 and search is unusable.

- **Migration `V18__sales_support_basedata_read.sql`** (already drafted) grants role
  904 the three read buttons **`2018 supplier:list`, `2014 brand:list`,
  `2022 category:list`**, bound as type=3 buttons **without** their parent basedata
  menus/dir. Per the nav filter (`buildRoutes` / `BasicLayout` drop `type=3`), this
  adds the permission codes **without** introducing a 基础数据 nav group — Sales
  Support's left nav stays the same four groups. Idempotent (`DELETE` then `INSERT`),
  reusing `role_menu.id = role_id*100000 + menu_id`.
- This also unblocks the `CascadeFilter` supplier dropdown in the new orderable-products
  picker for Sales Support.

## Out of scope (YAGNI)

- No procurement automation off negative stock (just visibility via the transaction).
- No change to who may create orders, nor to the inventory-list UI beyond it now
  being able to display negative quantities.
- No back-fill of `inventory_stock` rows for never-stocked products.
