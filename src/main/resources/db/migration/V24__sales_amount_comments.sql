-- ===========================================================================
-- V24: 修正 sales_order / sales_order_item 三个金额字段的 COMMENT
--   自销售订单导入（V23）起，Sale Price 是行小计而非 unit_price*qty：
--   unitPrice = salePrice/qty（四舍五入到 2 位），amount 保留 salePrice 原值，
--   使订单总额精确等于 Excel 该列之和（即便除不尽，如 700/3）。
--   V6 建表时的注释仍写着旧的 unit_price*qty 恒等式，需要更正，
--   但 V6/V23 已在生产环境执行过，不能改历史迁移，只能在此新增 MODIFY COLUMN。
--   （纯 DDL 注释变更，不改列类型/约束，不影响既有数据。）
-- ===========================================================================
ALTER TABLE sales_order
    MODIFY COLUMN total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00
        COMMENT '应收货款 = Σ(item.amount)（导入订单里 amount 是 Excel 行小计原值，不等于 unit_price*qty）';

ALTER TABLE sales_order_item
    MODIFY COLUMN amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00
        COMMENT '行金额：手工下单 = unit_price*qty；导入订单 = Excel Sale Price 原值（行小计，可能与 unit_price*qty 有尾差）',
    MODIFY COLUMN actual_amount DECIMAL(12, 2) DEFAULT NULL
        COMMENT '实收：未拒收(reject_qty=0)时 = amount 原值；有拒收时 = amount*(qty-reject_qty)/qty，四舍五入到 2 位';
