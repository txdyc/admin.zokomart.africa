-- ===========================================================================
-- V19: 数据看板（CEO 经营概览）。
--   目录 1010 数据看板(/dashboard, sort=0 置顶) / 页面 1118 经营概览
--   / 按钮 2069 dashboard:view。
--   仪表盘含公司级财务口径，默认仅超管（通配）可见；其它角色由管理员在
--   角色管理里按需授权（沿用 V12 约定）。
--   ID 约定：目录 1010 / 菜单 1118 / 按钮 2069（接续现有分配，永不冲突）。
-- ===========================================================================
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(1010, 0,    '数据看板', 1, NULL,             '/dashboard',          NULL,              'ant-design:dashboard-outlined', 0, 1, 1, NOW(), 0, 0),
(1118, 1010, '经营概览', 2, NULL,             '/dashboard/overview', 'dashboard/index', NULL,                            1, 1, 1, NOW(), 0, 0),
(2069, 1118, '查看仪表盘', 3, 'dashboard:view', NULL,                  NULL,              NULL,                            1, 1, 1, NOW(), 0, 0);
