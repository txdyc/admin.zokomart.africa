-- ===========================================================================
-- V20: 广告管理（AI 生图）。
--   表 ad_ai_model（聚合平台模型配置）+ ad_product_image（已保留的广告图）。
--   菜单：目录 1011 广告管理(/ad) / 菜单 1119 AI生图 / 1120 模型管理
--        / 按钮 2070-2077。默认仅超管（通配）可见，沿用 V19 约定不绑角色。
-- ===========================================================================
CREATE TABLE ad_ai_model (
    id          BIGINT       NOT NULL COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '展示名，如 NanoBanana Pro',
    base_url    VARCHAR(255) NOT NULL COMMENT '聚合平台 OpenAI 兼容 base url，如 https://xxx/v1',
    api_key     VARCHAR(255) NOT NULL COMMENT 'API Key（接口出参一律脱敏）',
    model_code  VARCHAR(128) NOT NULL COMMENT '聚合平台模型标识',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    sort        INT          NOT NULL DEFAULT 0,
    remark      VARCHAR(255)          DEFAULT NULL,
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    create_by   BIGINT                DEFAULT NULL,
    update_by   BIGINT                DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 生图模型配置';

CREATE TABLE ad_product_image (
    id                  BIGINT       NOT NULL COMMENT '主键',
    supplier_product_id BIGINT       NOT NULL COMMENT '关联供应商产品',
    file_url            VARCHAR(255) NOT NULL COMMENT '本地相对路径 /files/ad/{uuid}.png',
    prompt              TEXT                  DEFAULT NULL COMMENT '生成时的 prompt',
    model_id            BIGINT                DEFAULT NULL COMMENT '生成所用模型',
    wc_media_id         BIGINT                DEFAULT NULL COMMENT '已同步的 WC media id（幂等）',
    sort                INT          NOT NULL DEFAULT 0,
    create_time         DATETIME              DEFAULT NULL,
    update_time         DATETIME              DEFAULT NULL,
    create_by           BIGINT                DEFAULT NULL,
    update_by           BIGINT                DEFAULT NULL,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_api_product (supplier_product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 生图保留的广告图';

INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(1011, 0,    '广告管理', 1, NULL,                '/ad',                  NULL,                        'ant-design:picture-outlined', 9, 1, 1, NOW(), 0, 0),
(1119, 1011, 'AI生图',   2, NULL,                '/ad/image-generation', 'ad/image-generation/index', NULL, 1, 1, 1, NOW(), 0, 0),
(1120, 1011, '模型管理', 2, NULL,                '/ad/model',            'ad/model/index',            NULL, 2, 1, 1, NOW(), 0, 0),
(2070, 1119, '生成图片',   3, 'ad:image:generate', NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2071, 1119, '保留/丢弃图片', 3, 'ad:image:keep', NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2072, 1119, '查询广告图', 3, 'ad:image:list',     NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2073, 1119, '删除广告图', 3, 'ad:image:delete',   NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0),
(2074, 1120, '查询模型',   3, 'ad:model:list',     NULL, NULL, NULL, 1, 1, 1, NOW(), 0, 0),
(2075, 1120, '新增模型',   3, 'ad:model:create',   NULL, NULL, NULL, 2, 1, 1, NOW(), 0, 0),
(2076, 1120, '编辑模型',   3, 'ad:model:update',   NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0),
(2077, 1120, '删除模型',   3, 'ad:model:delete',   NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0);
