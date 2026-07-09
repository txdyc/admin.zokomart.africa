-- ===========================================================================
-- V16: 原始订单行编辑权限。按钮 2068 raw-order:update（挂在 1117 原始订单页下）。
--      superadmin 通配自动可见；其它角色后续在角色管理里授权。
-- ===========================================================================
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2068, 1117, '编辑原始订单', 3, 'raw-order:update', NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0);
