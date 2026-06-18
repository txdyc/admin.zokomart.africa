-- ===========================================================================
-- V13: WooCommerce 同步记录表 + 同步权限按钮 wc:sync（挂"供应商产品"菜单 1110）。
-- ===========================================================================
CREATE TABLE wc_sync_record (
    supplier_product_id BIGINT       NOT NULL COMMENT '后台供应商产品(主键关联)',
    wc_product_id       BIGINT                DEFAULT NULL COMMENT 'WooCommerce 商品 id',
    sku                 VARCHAR(64)           DEFAULT NULL,
    last_status         VARCHAR(32)           DEFAULT NULL COMMENT 'CREATED/UPDATED/DRAFTED/FAILED',
    last_synced_time    DATETIME              DEFAULT NULL,
    last_error          VARCHAR(512)          DEFAULT NULL,
    PRIMARY KEY (supplier_product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'WooCommerce 同步记录';

INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2065, 1110, '同步到独立站', 3, 'wc:sync', NULL, NULL, NULL, 5, 1, 1, NOW(), 0, 0);
