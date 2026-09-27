-- ===========================================================================
-- V25: 销售订单编辑 / 删除（逻辑删除）按钮权限，挂在 1114 销售订单页下。
--      仅超管（通配）默认可见；其它角色按需在角色管理里授权。
-- ===========================================================================
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2079, 1114, '编辑销售订单', 3, 'sales:order:update', NULL, NULL, NULL, 5, 1, 1, NOW(), 0, 0),
(2080, 1114, '删除销售订单', 3, 'sales:order:delete', NULL, NULL, NULL, 6, 1, 1, NOW(), 0, 0);
