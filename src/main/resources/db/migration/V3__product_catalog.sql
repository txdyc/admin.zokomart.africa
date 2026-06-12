-- ===========================================================================
-- V3: 平台自有商品目录 SPU / SKU（面向独立站前台展示，与采购-销售流转解耦）
-- ===========================================================================

CREATE TABLE product_spu (
    id          BIGINT       NOT NULL COMMENT '主键',
    name        VARCHAR(255) NOT NULL COMMENT 'SPU 名称',
    brand_id    BIGINT                DEFAULT NULL COMMENT '品牌',
    category_id BIGINT                DEFAULT NULL COMMENT '分类',
    main_image  VARCHAR(512)          DEFAULT NULL COMMENT '主图',
    description  TEXT                 DEFAULT NULL COMMENT '描述',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    create_by   BIGINT                DEFAULT NULL,
    update_by   BIGINT                DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_spu_brand (brand_id),
    KEY idx_spu_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台商品 SPU';

CREATE TABLE product_sku (
    id          BIGINT         NOT NULL COMMENT '主键',
    spu_id      BIGINT         NOT NULL COMMENT '所属 SPU',
    sku_code    VARCHAR(64)    NOT NULL COMMENT 'SKU 编码',
    spec        VARCHAR(255)            DEFAULT NULL COMMENT '规格',
    image       VARCHAR(512)            DEFAULT NULL,
    price       DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '售价',
    status      TINYINT        NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    create_time DATETIME                DEFAULT NULL,
    update_time DATETIME                DEFAULT NULL,
    create_by   BIGINT                  DEFAULT NULL,
    update_by   BIGINT                  DEFAULT NULL,
    deleted     TINYINT        NOT NULL DEFAULT 0,
    version     INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code),
    KEY idx_sku_spu (spu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台商品 SKU';
