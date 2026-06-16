-- ===========================================================================
-- V10: 供应商产品新增「每箱数量 / 整箱价 / 库存状态」三列，用于 URL 抓取导入保真。
-- ===========================================================================
ALTER TABLE supplier_product
  ADD COLUMN qty_per_box  INT            DEFAULT NULL COMMENT '每箱数量',
  ADD COLUMN box_price    DECIMAL(12,2)  DEFAULT NULL COMMENT '整箱价 (GH)',
  ADD COLUMN stock_status VARCHAR(64)    DEFAULT NULL COMMENT '库存状态文本';
