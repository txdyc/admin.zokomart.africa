-- ===========================================================================
-- V8: 供应商-品牌授权（显式 M:N）。无逻辑删除，解绑即物理删除。
--     + 菜单按钮 supplier:brand:list / supplier:brand:assign（挂供应商管理 1105）
--     + 采购员 BUYER(901) 补绑：基础数据目录1002/供应商菜单1105/查询供应商2018/新增2061-2062
-- ===========================================================================
CREATE TABLE supplier_brand (
    id          BIGINT       NOT NULL COMMENT '主键',
    supplier_id BIGINT       NOT NULL COMMENT '供应商',
    brand_id    BIGINT       NOT NULL COMMENT '品牌',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark      VARCHAR(255)          DEFAULT NULL,
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    create_by   BIGINT                DEFAULT NULL,
    update_by   BIGINT                DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_brand (supplier_id, brand_id),
    KEY idx_sb_supplier (supplier_id),
    KEY idx_sb_brand (brand_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商-品牌授权';

INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2061, 1105, '查询品牌授权', 3, 'supplier:brand:list',   NULL, NULL, NULL, 5, 1, 1, NOW(), 0, 0),
(2062, 1105, '分配品牌授权', 3, 'supplier:brand:assign', NULL, NULL, NULL, 6, 1, 1, NOW(), 0, 0);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 901 * 100000 + m.id, 901, m.id, NOW() FROM sys_menu m WHERE m.id IN (1002, 1105, 2018, 2061, 2062);
