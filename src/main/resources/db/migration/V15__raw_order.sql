-- ===========================================================================
-- V15: 原始订单（Raw Order）CSV 导入。
--   独立于 sales_order：按 CSV 原始字段存储，状态仅 PAID / RECIPIENT_REFUSED /
--   UNABLE_TO_CONTACT_RECIPIENT / RECIPIENT_UNABLE_TO_PAY 四种。
--   菜单：目录 1009 订单管理 / 页面 1117 原始订单 / 按钮 2066 raw-order:list、2067 raw-order:import。
--   superadmin 通配自动可见；其它角色后续在角色管理里授权。
-- ===========================================================================

CREATE TABLE raw_order (
    id            BIGINT         NOT NULL COMMENT '主键',
    order_date    DATE           NOT NULL COMMENT '订单日期（CSV date 列）',
    brand         VARCHAR(128)   NOT NULL COMMENT '品牌（原始文本，不关联 brand 表）',
    price         DECIMAL(12, 2) NOT NULL COMMENT '价格',
    customer_name VARCHAR(128)   NOT NULL COMMENT '客户姓名',
    city          VARCHAR(128)   NOT NULL COMMENT '城市',
    address       VARCHAR(512)   NOT NULL COMMENT '地址',
    telephone     VARCHAR(32)    NOT NULL COMMENT '电话',
    product_name  VARCHAR(255)   NOT NULL COMMENT '产品名称',
    product_code  VARCHAR(64)    NOT NULL COMMENT '产品编码',
    quantity      INT            NOT NULL COMMENT '数量',
    status        VARCHAR(40)    NOT NULL
        COMMENT 'PAID/RECIPIENT_REFUSED/UNABLE_TO_CONTACT_RECIPIENT/RECIPIENT_UNABLE_TO_PAY',
    cod           DECIMAL(12, 2) NOT NULL COMMENT '代收货款 COD',
    freight       DECIMAL(12, 2) NOT NULL COMMENT '运费',
    balance       DECIMAL(12, 2) NOT NULL COMMENT '结余',
    create_time   DATETIME                DEFAULT NULL,
    update_time   DATETIME                DEFAULT NULL,
    create_by     BIGINT                  DEFAULT NULL,
    update_by     BIGINT                  DEFAULT NULL,
    deleted       TINYINT        NOT NULL DEFAULT 0,
    version       INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_raw_order_date (order_date),
    KEY idx_raw_order_status (status),
    KEY idx_raw_order_telephone (telephone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '原始订单（CSV 导入）';

INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(1009, 0,    '订单管理',     1, NULL,               '/order',     NULL,              'ant-design:shopping-outlined', 9, 1, 1, NOW(), 0, 0),
(1117, 1009, '原始订单',     2, NULL,               '/order/raw', 'order/raw/index', NULL,                           1, 1, 1, NOW(), 0, 0),
(2066, 1117, '查询原始订单', 3, 'raw-order:list',   NULL,         NULL,              NULL,                           1, 1, 1, NOW(), 0, 0),
(2067, 1117, '导入原始订单', 3, 'raw-order:import', NULL,         NULL,              NULL,                           2, 1, 1, NOW(), 0, 0);
