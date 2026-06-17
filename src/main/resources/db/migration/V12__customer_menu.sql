-- ===========================================================================
-- V12: 客户管理（顶级菜单）。目录 1008 / 页面 1116 / 按钮 customer:list (2064)。
--      superadmin 通配自动可见；其它角色后续在角色管理里授权。
-- ===========================================================================
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(1008, 0,    '客户管理', 1, NULL,            '/customer',      NULL,             'ant-design:team-outlined', 8, 1, 1, NOW(), 0, 0),
(1116, 1008, '客户列表', 2, NULL,            '/customer/list', 'customer/index', NULL,                       1, 1, 1, NOW(), 0, 0),
(2064, 1116, '查询客户', 3, 'customer:list', NULL,             NULL,             NULL,                       1, 1, 1, NOW(), 0, 0);
