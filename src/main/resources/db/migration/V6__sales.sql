-- ===========================================================================
-- V6: 销售 / 物流
--   销售下单(扣库存) → 派送 → 签收/拒收(回补) → 完成结算(实收)。
--   状态机见 PRD §5.13；金额 DECIMAL(12,2)，状态 VARCHAR。
-- ===========================================================================

CREATE TABLE sales_order (
    id                    BIGINT         NOT NULL COMMENT '主键',
    order_no              VARCHAR(64)    NOT NULL COMMENT '订单号（唯一）',
    status                VARCHAR(20)    NOT NULL DEFAULT 'PENDING_DISPATCH'
        COMMENT 'PENDING_DISPATCH/DISPATCHING/SIGNED/SIGNED_PAID/UNREACHABLE/REJECTED',
    customer_name         VARCHAR(128)   NOT NULL COMMENT '客户姓名',
    customer_phone        VARCHAR(32)    NOT NULL COMMENT '客户手机号',
    customer_address      VARCHAR(512)   NOT NULL COMMENT '详细地址',
    salesperson_id        BIGINT                  DEFAULT NULL COMMENT '销售人员(=制单人)',
    total_qty             INT            NOT NULL DEFAULT 0 COMMENT '总数量',
    total_amount          DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '应收货款 Σ(unit_price*qty)',
    actual_amount         DECIMAL(12, 2)          DEFAULT NULL COMMENT '实收金额，完成时计算',
    logistics_provider_id BIGINT                  DEFAULT NULL COMMENT '物流服务商(派送时填)',
    delivery_fee          DECIMAL(12, 2)          DEFAULT NULL COMMENT '派送费(成本，不计入实收)',
    dispatch_time         DATETIME                DEFAULT NULL,
    sign_time             DATETIME                DEFAULT NULL,
    complete_time         DATETIME                DEFAULT NULL,
    completed             TINYINT        NOT NULL DEFAULT 0 COMMENT '0未完成/1已完成',
    remark                VARCHAR(255)            DEFAULT NULL,
    create_time           DATETIME                DEFAULT NULL,
    update_time           DATETIME                DEFAULT NULL,
    create_by             BIGINT                  DEFAULT NULL,
    update_by             BIGINT                  DEFAULT NULL,
    deleted               TINYINT        NOT NULL DEFAULT 0,
    version               INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_no (order_no),
    KEY idx_sales_salesperson (salesperson_id),
    KEY idx_sales_completed (completed)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售订单';

CREATE TABLE sales_order_item (
    id                  BIGINT         NOT NULL COMMENT '主键',
    order_id            BIGINT         NOT NULL COMMENT '所属订单',
    supplier_product_id BIGINT         NOT NULL COMMENT '供应商产品',
    product_name        VARCHAR(255)   NOT NULL COMMENT '产品名快照',
    product_code        VARCHAR(64)             DEFAULT NULL COMMENT '产品编码快照',
    unit_price          DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '单价(默认零售价,可改)',
    qty                 INT            NOT NULL DEFAULT 0,
    reject_qty          INT            NOT NULL DEFAULT 0 COMMENT '拒收数量',
    amount              DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '=unit_price*qty',
    actual_amount       DECIMAL(12, 2)          DEFAULT NULL COMMENT '=unit_price*(qty-reject_qty)',
    create_time         DATETIME                DEFAULT NULL,
    update_time         DATETIME                DEFAULT NULL,
    create_by           BIGINT                  DEFAULT NULL,
    update_by           BIGINT                  DEFAULT NULL,
    deleted             TINYINT        NOT NULL DEFAULT 0,
    version             INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sales_item_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售订单明细';
