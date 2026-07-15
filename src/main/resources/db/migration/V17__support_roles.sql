-- ===========================================================================
-- V17: 把 V7 的两个模板角色改造为「销售支持 / 物流支持」，并重绑菜单/权限。
--   904 销售员   -> 销售支持：平台目录(供应商产品查看) + 库存管理(库存列表只读)
--                  + 销售管理(下单/查看本人) + 物流管理(物流跟踪只读，仅本人订单)
--   905 物流专员 -> 物流支持：物流管理(物流跟踪，处理全部订单) +
--                  sales:order:list / sales:order:list:all（仅权限码，不进左侧菜单）
-- 角色 code（SALES/LOGISTICS）保持不变；仅改 name/remark + sys_role_menu。
-- 复用 V7 约定：role_menu.id = role_id*100000 + menu_id。
-- ===========================================================================

-- 1) 更新角色显示名/备注（code 不变）
UPDATE sys_role SET name = '销售支持',
    remark = '供应商产品/库存查看 + 销售下单/查看(仅本人) + 物流跟踪查看(仅本人)'
    WHERE id = 904;
UPDATE sys_role SET name = '物流支持',
    remark = '物流跟踪：处理全部订单的派送/状态/派送费/拒收/完成'
    WHERE id = 905;

-- 2) 清空这两个角色的旧菜单绑定（仅 904/905，其它角色不动）
DELETE FROM sys_role_menu WHERE role_id IN (904, 905);

-- 3) 销售支持(904) 绑定：目录+菜单(进左侧导航) + 按钮(权限码)
--    目录 1003 平台目录 / 1005 库存管理 / 1006 销售管理 / 1007 物流管理
--    菜单 1110 供应商产品 / 1113 库存列表 / 1114 销售订单 / 1115 物流跟踪
--    按钮 2038 supplierProduct:list / 2051 inventory:list
--         2054 sales:order:list / 2055 sales:order:create
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 904 * 100000 + m.id, 904, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    1003, 1005, 1006, 1007,
    1110, 1113, 1114, 1115,
    2038, 2051, 2054, 2055
);

-- 4) 物流支持(905) 绑定：
--    目录/菜单 1007 物流管理 + 1115 物流跟踪（左侧导航仅此一组）
--    按钮 2057/2058/2059/2060 logistics:dispatch/status/reject/complete
--    按钮 2054 sales:order:list + 2056 sales:order:list:all
--        （2054/2056 的父菜单 1114/1006 不绑定：前端 buildRoutes 过滤 type=3，
--          故只授予权限码、不产生「销售管理」菜单）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 905 * 100000 + m.id, 905, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    1007, 1115,
    2054, 2056,
    2057, 2058, 2059, 2060
);
