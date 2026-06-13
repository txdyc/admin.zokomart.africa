-- ===========================================================================
-- V7: 菜单/权限码种子 + 推荐角色模板
-- ---------------------------------------------------------------------------
-- 1. sys_menu：插入完整的目录(1)/菜单(2)/按钮(3) 树，按钮携带 PRD 附录 B 的全部
--    perm_code（与控制器 @SaCheckPermission 逐一核对，含内联校验的
--    sales:order:list:all）。前端 Vben 据此渲染菜单与按钮级权限。
-- 2. sys_role：插入 5 个推荐角色模板（采购员/审批主管/仓库管理员/销售员/物流专员），
--    超管仍可在后台动态增删改，不属硬编码。
-- 3. sys_role_menu：按职责将角色绑定到所需按钮 + 其祖先菜单/目录（保证菜单树可渲染）。
--
-- ID 约定（人为分配，避开运行期 ASSIGN_ID 雪花大整数，永不冲突）：
--   目录   1001-1007    菜单   1101-1115    按钮   2001-2060
--   角色   901-905
--   role_menu.id = role_id*100000 + menu_id（确定性、跨角色唯一）
-- 审计列：create_time=NOW()，deleted=0，version=0，status/visible=1。
-- ===========================================================================

-- ---------- 1. 目录（type=1, parent_id=0） ----------
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(1001, 0, '系统管理', 1, NULL, '/system',    NULL, 'ant-design:setting-outlined',        1, 1, 1, NOW(), 0, 0),
(1002, 0, '基础数据', 1, NULL, '/basedata',  NULL, 'ant-design:database-outlined',       2, 1, 1, NOW(), 0, 0),
(1003, 0, '平台目录', 1, NULL, '/product',   NULL, 'ant-design:appstore-outlined',       3, 1, 1, NOW(), 0, 0),
(1004, 0, '采购管理', 1, NULL, '/purchase',  NULL, 'ant-design:shopping-cart-outlined',  4, 1, 1, NOW(), 0, 0),
(1005, 0, '库存管理', 1, NULL, '/inventory', NULL, 'ant-design:inbox-outlined',          5, 1, 1, NOW(), 0, 0),
(1006, 0, '销售管理', 1, NULL, '/sales',     NULL, 'ant-design:shopping-outlined',       6, 1, 1, NOW(), 0, 0),
(1007, 0, '物流管理', 1, NULL, '/logistics', NULL, 'ant-design:car-outlined',            7, 1, 1, NOW(), 0, 0);

-- ---------- 2. 菜单（type=2） ----------
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(1101, 1001, '用户管理',   2, NULL, '/system/user',              'system/user/index',                NULL, 1, 1, 1, NOW(), 0, 0),
(1102, 1001, '角色管理',   2, NULL, '/system/role',              'system/role/index',                NULL, 2, 1, 1, NOW(), 0, 0),
(1103, 1001, '菜单管理',   2, NULL, '/system/menu',              'system/menu/index',                NULL, 3, 1, 1, NOW(), 0, 0),
(1104, 1002, '品牌管理',   2, NULL, '/basedata/brand',           'basedata/brand/index',             NULL, 1, 1, 1, NOW(), 0, 0),
(1105, 1002, '供应商管理', 2, NULL, '/basedata/supplier',        'basedata/supplier/index',          NULL, 2, 1, 1, NOW(), 0, 0),
(1106, 1002, '分类管理',   2, NULL, '/basedata/category',        'basedata/category/index',          NULL, 3, 1, 1, NOW(), 0, 0),
(1107, 1002, '物流服务商', 2, NULL, '/basedata/logistics-provider', 'basedata/logistics-provider/index', NULL, 4, 1, 1, NOW(), 0, 0),
(1108, 1003, '商品 SPU',   2, NULL, '/product/spu',              'product/spu/index',                NULL, 1, 1, 1, NOW(), 0, 0),
(1109, 1003, '商品 SKU',   2, NULL, '/product/sku',              'product/sku/index',                NULL, 2, 1, 1, NOW(), 0, 0),
(1110, 1003, '供应商产品', 2, NULL, '/product/supplier-product', 'product/supplier-product/index',   NULL, 3, 1, 1, NOW(), 0, 0),
(1111, 1004, '采购计划',   2, NULL, '/purchase/plan',            'purchase/plan/index',              NULL, 1, 1, 1, NOW(), 0, 0),
(1112, 1004, '采购订单',   2, NULL, '/purchase/order',           'purchase/order/index',             NULL, 2, 1, 1, NOW(), 0, 0),
(1113, 1005, '库存列表',   2, NULL, '/inventory/stock',          'inventory/stock/index',            NULL, 1, 1, 1, NOW(), 0, 0),
(1114, 1006, '销售订单',   2, NULL, '/sales/order',              'sales/order/index',                NULL, 1, 1, 1, NOW(), 0, 0),
(1115, 1007, '物流跟踪',   2, NULL, '/logistics/track',          'logistics/track/index',            NULL, 1, 1, 1, NOW(), 0, 0);

-- ---------- 3. 按钮（type=3，携带 perm_code） ----------
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
-- 系统管理 / 用户
(2001, 1101, '查询用户',   3, 'system:user:list',     NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2002, 1101, '新增用户',   3, 'system:user:create',   NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2003, 1101, '编辑用户',   3, 'system:user:update',   NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2004, 1101, '删除用户',   3, 'system:user:delete',   NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
(2005, 1101, '重置密码',   3, 'system:user:resetPwd', NULL, NULL, NULL, 5, 1, 1, NOW(), 0, 0),
-- 系统管理 / 角色
(2006, 1102, '查询角色',   3, 'system:role:list',     NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2007, 1102, '新增角色',   3, 'system:role:create',   NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2008, 1102, '编辑角色',   3, 'system:role:update',   NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2009, 1102, '删除角色',   3, 'system:role:delete',   NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
-- 系统管理 / 菜单
(2010, 1103, '查询菜单',   3, 'system:menu:list',     NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2011, 1103, '新增菜单',   3, 'system:menu:create',   NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2012, 1103, '编辑菜单',   3, 'system:menu:update',   NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2013, 1103, '删除菜单',   3, 'system:menu:delete',   NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
-- 基础数据 / 品牌
(2014, 1104, '查询品牌',   3, 'brand:list',           NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2015, 1104, '新增品牌',   3, 'brand:create',         NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2016, 1104, '编辑品牌',   3, 'brand:update',         NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2017, 1104, '删除品牌',   3, 'brand:delete',         NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
-- 基础数据 / 供应商
(2018, 1105, '查询供应商', 3, 'supplier:list',        NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2019, 1105, '新增供应商', 3, 'supplier:create',      NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2020, 1105, '编辑供应商', 3, 'supplier:update',      NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2021, 1105, '删除供应商', 3, 'supplier:delete',      NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
-- 基础数据 / 分类
(2022, 1106, '查询分类',   3, 'category:list',        NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2023, 1106, '新增分类',   3, 'category:create',      NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2024, 1106, '编辑分类',   3, 'category:update',      NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2025, 1106, '删除分类',   3, 'category:delete',      NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
-- 基础数据 / 物流服务商
(2026, 1107, '查询物流服务商', 3, 'logisticsProvider:list',   NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2027, 1107, '新增物流服务商', 3, 'logisticsProvider:create', NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2028, 1107, '编辑物流服务商', 3, 'logisticsProvider:update', NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2029, 1107, '删除物流服务商', 3, 'logisticsProvider:delete', NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
-- 平台目录 / SPU
(2030, 1108, '查询SPU',    3, 'product:spu:list',     NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2031, 1108, '新增SPU',    3, 'product:spu:create',   NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2032, 1108, '编辑SPU',    3, 'product:spu:update',   NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2033, 1108, '删除SPU',    3, 'product:spu:delete',   NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
-- 平台目录 / SKU
(2034, 1109, '查询SKU',    3, 'product:sku:list',     NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2035, 1109, '新增SKU',    3, 'product:sku:create',   NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2036, 1109, '编辑SKU',    3, 'product:sku:update',   NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2037, 1109, '删除SKU',    3, 'product:sku:delete',   NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
-- 平台目录 / 供应商产品
(2038, 1110, '查询供应商产品', 3, 'supplierProduct:list',   NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2039, 1110, '新增供应商产品', 3, 'supplierProduct:create', NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2040, 1110, '编辑供应商产品', 3, 'supplierProduct:update', NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2041, 1110, '删除供应商产品', 3, 'supplierProduct:delete', NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
-- 采购管理 / 采购计划
(2042, 1111, '查询采购计划', 3, 'purchase:plan:list',   NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2043, 1111, '新增采购计划', 3, 'purchase:plan:create', NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2044, 1111, '编辑采购计划', 3, 'purchase:plan:update', NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2045, 1111, '删除采购计划', 3, 'purchase:plan:delete', NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
(2046, 1111, '提交采购计划', 3, 'purchase:plan:submit', NULL, NULL, NULL, 5, 1, 1, NOW(), 0, 0),
(2047, 1111, '审批采购计划', 3, 'purchase:plan:approve', NULL, NULL, NULL, 6, 1, 1, NOW(), 0, 0),
-- 采购管理 / 采购订单
(2048, 1112, '查询采购订单', 3, 'purchase:order:list',    NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2049, 1112, '订单付款',     3, 'purchase:order:pay',     NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2050, 1112, '确认到货',     3, 'purchase:order:confirm', NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
-- 库存管理
(2051, 1113, '查询库存',   3, 'inventory:list',    NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2052, 1113, '调整库存',   3, 'inventory:edit',    NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2053, 1113, '入库',       3, 'inventory:inbound', NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
-- 销售管理
(2054, 1114, '查询销售订单',   3, 'sales:order:list',     NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2055, 1114, '新增销售订单',   3, 'sales:order:create',   NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2056, 1114, '查看全部订单',   3, 'sales:order:list:all', NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
-- 物流管理
(2057, 1115, '派送',   3, 'logistics:dispatch', NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2058, 1115, '更新状态', 3, 'logistics:status',  NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2059, 1115, '拒收',   3, 'logistics:reject',   NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2060, 1115, '完成',   3, 'logistics:complete', NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0);

-- ---------- 4. 推荐角色模板 ----------
INSERT INTO sys_role (id, name, code, sort, status, remark, create_time, deleted, version) VALUES
(901, '采购员',     'BUYER',     1, 1, '供应商产品维护 + 采购计划/订单（不含审批）', NOW(), 0, 0),
(902, '审批主管',   'APPROVER',  2, 1, '采购计划审批 + 查看',                       NOW(), 0, 0),
(903, '仓库管理员', 'WAREHOUSE', 3, 1, '入库 + 库存查看/调整',                      NOW(), 0, 0),
(904, '销售员',     'SALES',     4, 1, '销售下单/查看（仅本人）',                   NOW(), 0, 0),
(905, '物流专员',   'LOGISTICS', 5, 1, '物流派送/状态/拒收/完成',                   NOW(), 0, 0);

-- ---------- 5. 角色—菜单绑定（按钮 + 祖先菜单/目录，role_menu.id = role_id*100000 + menu_id） ----------
-- 采购员：供应商产品(全) + 采购计划(除审批) + 采购订单(全)；职责分离故不含 purchase:plan:approve
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 901 * 100000 + m.id, 901, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    1003, 1004, 1110, 1111, 1112,
    2038, 2039, 2040, 2041,
    2042, 2043, 2044, 2045, 2046,
    2048, 2049, 2050
);

-- 审批主管：采购计划审批 + 计划/订单查看
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 902 * 100000 + m.id, 902, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    1004, 1111, 1112,
    2042, 2047, 2048
);

-- 仓库管理员：库存查看/调整/入库
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 903 * 100000 + m.id, 903, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    1005, 1113,
    2051, 2052, 2053
);

-- 销售员：销售下单/查看（不含 sales:order:list:all，故仅见本人）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 904 * 100000 + m.id, 904, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    1006, 1114,
    2054, 2055
);

-- 物流专员：物流派送/状态/拒收/完成
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 905 * 100000 + m.id, 905, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    1007, 1115,
    2057, 2058, 2059, 2060
);
