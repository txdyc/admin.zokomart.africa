-- ===========================================================================
-- V11: 修正历史抓取导入数据 —— MOQ（最小采购量）应等于每箱量。
--      每种产品的最小采购量即一箱；早期抓取导入把 MOQ 误置为默认 1。
--      仅影响有 qty_per_box 的行（抓取导入产生），CSV/手工录入不受影响。
-- ===========================================================================
UPDATE supplier_product
SET min_purchase_qty = qty_per_box
WHERE qty_per_box IS NOT NULL
  AND qty_per_box >= 1
  AND (min_purchase_qty IS NULL OR min_purchase_qty <> qty_per_box);
