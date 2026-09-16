-- ===========================================================================
-- V23: 销售订单 Excel 导入
--   1. sales_order 增加 city / order_date（业务日期，区别于 create_time 语义）
--   2. sales_order_item 增加 external_order_id（源 Excel 的 Order ID，行级，仅溯源）
--   3. 历史数据回填 order_date
--   4. 新增按钮权限 sales:order:import，授予销售员 SALES(904)
-- ===========================================================================
ALTER TABLE sales_order
    ADD COLUMN city       VARCHAR(128) NULL COMMENT '城市'                         AFTER customer_address,
    ADD COLUMN order_date DATE         NULL COMMENT '订单日期（业务日期，非创建时刻）' AFTER city,
    ADD KEY idx_sales_order_date (order_date);

ALTER TABLE sales_order_item
    ADD COLUMN external_order_id VARCHAR(64) NULL COMMENT '来源 Excel 的 Order ID（行级，仅溯源）' AFTER order_id;

UPDATE sales_order SET order_date = DATE(create_time) WHERE order_date IS NULL;

INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2078, 1114, '导入销售订单', 3, 'sales:order:import', NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 904 * 100000 + m.id, 904, m.id, NOW() FROM sys_menu m WHERE m.id = 2078;
