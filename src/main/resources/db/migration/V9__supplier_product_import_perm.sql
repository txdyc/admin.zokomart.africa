-- ===========================================================================
-- V9: 供应商产品 CSV 批量导入权限。
--     菜单按钮 supplierProduct:import（挂"供应商产品" 1110），授予采购员 BUYER(901)。
--     superadmin 走通配 *，无需显式授权。
-- ===========================================================================
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2063, 1110, '导入供应商产品', 3, 'supplierProduct:import', NULL, NULL, NULL, 5, 1, 1, NOW(), 0, 0);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 901 * 100000 + m.id, 901, m.id, NOW() FROM sys_menu m WHERE m.id = 2063;
