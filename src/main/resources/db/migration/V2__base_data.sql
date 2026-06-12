-- ===========================================================================
-- V2: 基础数据 —— 品牌 / 供应商 / 商品分类(树) / 物流服务商
-- ===========================================================================

CREATE TABLE brand (
    id          BIGINT       NOT NULL COMMENT '主键',
    name        VARCHAR(128) NOT NULL COMMENT '品牌名',
    code        VARCHAR(64)           DEFAULT NULL COMMENT '品牌编码',
    logo_url    VARCHAR(512)          DEFAULT NULL COMMENT 'LOGO',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    remark      VARCHAR(255)          DEFAULT NULL,
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    create_by   BIGINT                DEFAULT NULL,
    update_by   BIGINT                DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_brand_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '品牌';

CREATE TABLE supplier (
    id             BIGINT       NOT NULL COMMENT '主键',
    name           VARCHAR(128) NOT NULL COMMENT '供应商名',
    code           VARCHAR(64)           DEFAULT NULL COMMENT '编码',
    contact_person VARCHAR(64)           DEFAULT NULL COMMENT '联系人',
    contact_phone  VARCHAR(32)           DEFAULT NULL COMMENT '联系电话',
    address        VARCHAR(255)          DEFAULT NULL COMMENT '地址',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    remark         VARCHAR(255)          DEFAULT NULL,
    create_time    DATETIME              DEFAULT NULL,
    update_time    DATETIME              DEFAULT NULL,
    create_by      BIGINT                DEFAULT NULL,
    update_by      BIGINT                DEFAULT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商';

CREATE TABLE category (
    id          BIGINT       NOT NULL COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点，0为根',
    name        VARCHAR(128) NOT NULL COMMENT '分类名',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    create_by   BIGINT                DEFAULT NULL,
    update_by   BIGINT                DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_category_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类';

CREATE TABLE logistics_provider (
    id             BIGINT       NOT NULL COMMENT '主键',
    name           VARCHAR(128) NOT NULL COMMENT '物流服务商名',
    code           VARCHAR(64)           DEFAULT NULL COMMENT '编码',
    contact_person VARCHAR(64)           DEFAULT NULL,
    contact_phone  VARCHAR(32)           DEFAULT NULL,
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    remark         VARCHAR(255)          DEFAULT NULL,
    create_time    DATETIME              DEFAULT NULL,
    update_time    DATETIME              DEFAULT NULL,
    create_by      BIGINT                DEFAULT NULL,
    update_by      BIGINT                DEFAULT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lp_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物流服务商';
