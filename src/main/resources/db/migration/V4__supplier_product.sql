-- ===========================================================================
-- V4: 供应商产品 supplier_product（核心操作单元；采购/入库/库存/销售均以此为对象）
-- 字段见 PRD §4.5，唯一约束 (supplier_id, product_code)。
-- ===========================================================================

CREATE TABLE supplier_product (
    id               BIGINT         NOT NULL COMMENT '主键',
    supplier_id      BIGINT         NOT NULL COMMENT '所属供应商',
    name             VARCHAR(255)   NOT NULL COMMENT '产品名称',
    brand_id         BIGINT                  DEFAULT NULL COMMENT '品牌',
    category_id      BIGINT                  DEFAULT NULL COMMENT '分类',
    product_code     VARCHAR(64)    NOT NULL COMMENT '产品编码（供应商内唯一）',
    wholesale_price  DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '批发价（采购成本价）',
    retail_price     DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '零售价（销售默认单价）',
    image_url        VARCHAR(512)            DEFAULT NULL COMMENT '产品图片',
    min_purchase_qty INT            NOT NULL DEFAULT 1 COMMENT '最小采购量(MOQ)，>=1',
    sku_id           BIGINT                  DEFAULT NULL COMMENT '可空，关联平台 SKU（不参与流转）',
    status           TINYINT        NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    remark           VARCHAR(255)            DEFAULT NULL COMMENT '备注',
    create_time      DATETIME                DEFAULT NULL,
    update_time      DATETIME                DEFAULT NULL,
    create_by        BIGINT                  DEFAULT NULL,
    update_by        BIGINT                  DEFAULT NULL,
    deleted          TINYINT        NOT NULL DEFAULT 0,
    version          INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_product_code (supplier_id, product_code),
    KEY idx_sp_supplier (supplier_id),
    KEY idx_sp_brand (brand_id),
    KEY idx_sp_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商产品';
