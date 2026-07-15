-- ===========================================================================
-- V18: 修复「销售支持(904)」在 供应商产品页(/product/supplier-product) 无法按
--      供应商/品牌/分类筛选的问题。该页与 CascadeFilter 组件在加载时会拉取
--      供应商/品牌/分类下拉数据，分别需要 supplier:list / brand:list / category:list，
--      而销售支持此前只有 supplierProduct:list，导致这些下拉接口 403、筛选不可用。
--
--   授予三个只读权限码（按钮 type=3）：
--     2014 brand:list / 2018 supplier:list / 2022 category:list
--   这些按钮不绑定其父菜单(1104/1105/1106)与「基础数据」目录(1002)，故只授予
--   权限码、不进入左侧导航（前端 buildRoutes / BasicLayout 过滤 type=3），
--   销售支持左侧导航仍为 平台目录/库存管理/销售管理/物流管理 四组不变。
--   复用既有约定：role_menu.id = role_id*100000 + menu_id。幂等：先删后插。
-- ===========================================================================

DELETE FROM sys_role_menu WHERE role_id = 904 AND menu_id IN (2014, 2018, 2022);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 904 * 100000 + m.id, 904, m.id, NOW() FROM sys_menu m WHERE m.id IN (
    2014, 2018, 2022
);
