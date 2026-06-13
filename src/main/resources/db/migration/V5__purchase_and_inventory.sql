-- ===========================================================================
-- V5: 采购链 + 库存
--   采购计划 → 审批生单（按供应商拆分）→ 付款 → 实际采购单 → 入库（库存↑ + 流水）
--   字段见 PRD §4.6 / §4.7。金额一律 DECIMAL(12,2)，状态用 VARCHAR。
--   库存增减一律经 inventory_stock(乐观锁) + inventory_transaction(流水) 双写。
-- ===========================================================================

-- 采购计划 ----------------------------------------------------------------
CREATE TABLE purchase_plan (
    id             BIGINT         NOT NULL COMMENT '主键',
    plan_no        VARCHAR(64)    NOT NULL COMMENT '计划单号（唯一）',
    status         VARCHAR(20)    NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED',
    submit_time    DATETIME                DEFAULT NULL COMMENT '提交时间',
    approver_id    BIGINT                  DEFAULT NULL COMMENT '审批人',
    approve_time   DATETIME                DEFAULT NULL COMMENT '审批时间',
    approve_remark VARCHAR(255)            DEFAULT NULL COMMENT '退回原因',
    total_qty      INT            NOT NULL DEFAULT 0 COMMENT '汇总数量',
    total_amount   DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '汇总金额',
    remark         VARCHAR(255)            DEFAULT NULL,
    create_time    DATETIME                DEFAULT NULL,
    update_time    DATETIME                DEFAULT NULL,
    create_by      BIGINT                  DEFAULT NULL,
    update_by      BIGINT                  DEFAULT NULL,
    deleted        TINYINT        NOT NULL DEFAULT 0,
    version        INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_no (plan_no),
    KEY idx_plan_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购计划';

CREATE TABLE purchase_plan_item (
    id                  BIGINT         NOT NULL COMMENT '主键',
    plan_id             BIGINT         NOT NULL COMMENT '所属计划',
    supplier_id         BIGINT         NOT NULL COMMENT '供应商',
    supplier_product_id BIGINT         NOT NULL COMMENT '供应商产品',
    brand_id            BIGINT                  DEFAULT NULL COMMENT '品牌',
    category_id         BIGINT                  DEFAULT NULL COMMENT '分类',
    product_name        VARCHAR(255)   NOT NULL COMMENT '产品名快照',
    product_code        VARCHAR(64)    NOT NULL COMMENT '产品编码快照',
    wholesale_price     DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '批发价快照',
    min_purchase_qty    INT            NOT NULL DEFAULT 1 COMMENT 'MOQ 快照',
    purchase_qty        INT            NOT NULL DEFAULT 0 COMMENT '采购数量（>=MOQ，0 不采购）',
    amount              DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '=wholesale_price*qty',
    create_time         DATETIME                DEFAULT NULL,
    update_time         DATETIME                DEFAULT NULL,
    create_by           BIGINT                  DEFAULT NULL,
    update_by           BIGINT                  DEFAULT NULL,
    deleted             TINYINT        NOT NULL DEFAULT 0,
    version             INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_plan_item_plan (plan_id),
    KEY idx_plan_item_supplier (supplier_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购计划明细';

-- 采购订单（审批按供应商拆分生成）---------------------------------------
CREATE TABLE purchase_order (
    id           BIGINT         NOT NULL COMMENT '主键',
    order_no     VARCHAR(64)    NOT NULL COMMENT '订单号（唯一）',
    plan_id      BIGINT         NOT NULL COMMENT '来源计划',
    supplier_id  BIGINT         NOT NULL COMMENT '供应商',
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT 'PENDING_PAYMENT/CONFIRMED',
    total_qty    INT            NOT NULL DEFAULT 0,
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    paid_amount  DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '已付款金额',
    remark       VARCHAR(255)            DEFAULT NULL,
    create_time  DATETIME                DEFAULT NULL,
    update_time  DATETIME                DEFAULT NULL,
    create_by    BIGINT                  DEFAULT NULL,
    update_by    BIGINT                  DEFAULT NULL,
    deleted      TINYINT        NOT NULL DEFAULT 0,
    version      INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_plan (plan_id),
    KEY idx_order_supplier (supplier_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购订单';

CREATE TABLE purchase_order_item (
    id                  BIGINT         NOT NULL COMMENT '主键',
    order_id            BIGINT         NOT NULL COMMENT '所属订单',
    supplier_product_id BIGINT         NOT NULL COMMENT '供应商产品',
    product_name        VARCHAR(255)   NOT NULL COMMENT '产品名快照',
    product_code        VARCHAR(64)    NOT NULL COMMENT '产品编码快照',
    wholesale_price     DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    qty                 INT            NOT NULL DEFAULT 0,
    amount              DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    payment_status      VARCHAR(20)    NOT NULL DEFAULT 'UNSET' COMMENT 'UNSET待付款/PAID已付款/UNPAID未付款',
    create_time         DATETIME                DEFAULT NULL,
    update_time         DATETIME                DEFAULT NULL,
    create_by           BIGINT                  DEFAULT NULL,
    update_by           BIGINT                  DEFAULT NULL,
    deleted             TINYINT        NOT NULL DEFAULT 0,
    version             INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购订单明细';

-- 实际采购单（取已付款明细生成）-----------------------------------------
CREATE TABLE actual_purchase_order (
    id                BIGINT         NOT NULL COMMENT '主键',
    actual_no         VARCHAR(64)    NOT NULL COMMENT '实际采购单号（唯一）',
    purchase_order_id BIGINT         NOT NULL COMMENT '来源采购订单',
    supplier_id       BIGINT         NOT NULL COMMENT '供应商',
    total_qty         INT            NOT NULL DEFAULT 0,
    total_amount      DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING_INBOUND' COMMENT 'PENDING_INBOUND/INBOUND_DONE',
    remark            VARCHAR(255)            DEFAULT NULL,
    create_time       DATETIME                DEFAULT NULL,
    update_time       DATETIME                DEFAULT NULL,
    create_by         BIGINT                  DEFAULT NULL,
    update_by         BIGINT                  DEFAULT NULL,
    deleted           TINYINT        NOT NULL DEFAULT 0,
    version           INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_actual_no (actual_no),
    KEY idx_actual_order (purchase_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '实际采购单';

CREATE TABLE actual_purchase_order_item (
    id                    BIGINT         NOT NULL COMMENT '主键',
    actual_order_id       BIGINT         NOT NULL COMMENT '所属实际采购单',
    purchase_order_item_id BIGINT        NOT NULL COMMENT '来源采购订单明细',
    supplier_product_id   BIGINT         NOT NULL COMMENT '供应商产品',
    product_name          VARCHAR(255)   NOT NULL,
    qty                   INT            NOT NULL DEFAULT 0,
    wholesale_price       DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    amount                DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    inbound_status        VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待入库/DONE已入库',
    inbound_qty           INT            NOT NULL DEFAULT 0,
    inbound_time          DATETIME                DEFAULT NULL,
    create_time           DATETIME                DEFAULT NULL,
    update_time           DATETIME                DEFAULT NULL,
    create_by             BIGINT                  DEFAULT NULL,
    update_by             BIGINT                  DEFAULT NULL,
    deleted               TINYINT        NOT NULL DEFAULT 0,
    version               INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_actual_item_order (actual_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '实际采购单明细';

-- 库存 -------------------------------------------------------------------
CREATE TABLE inventory_stock (
    id                  BIGINT      NOT NULL COMMENT '主键',
    supplier_product_id BIGINT      NOT NULL COMMENT '供应商产品（唯一记账）',
    supplier_id         BIGINT               DEFAULT NULL COMMENT '冗余，支持联动筛选',
    brand_id            BIGINT               DEFAULT NULL,
    category_id         BIGINT               DEFAULT NULL,
    quantity            INT         NOT NULL DEFAULT 0 COMMENT '当前库存（>=0）',
    create_time         DATETIME             DEFAULT NULL,
    update_time         DATETIME             DEFAULT NULL,
    create_by           BIGINT               DEFAULT NULL,
    update_by           BIGINT               DEFAULT NULL,
    deleted             TINYINT     NOT NULL DEFAULT 0,
    version             INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_supplier_product (supplier_product_id),
    KEY idx_stock_supplier (supplier_id),
    KEY idx_stock_brand (brand_id),
    KEY idx_stock_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存（按供应商产品唯一记账）';

CREATE TABLE inventory_transaction (
    id                  BIGINT      NOT NULL COMMENT '主键',
    supplier_product_id BIGINT      NOT NULL COMMENT '供应商产品',
    type                VARCHAR(20) NOT NULL COMMENT 'PURCHASE_IN/SALES_OUT/REJECT_RETURN/MANUAL_ADJUST',
    qty_change          INT         NOT NULL COMMENT '数量变化（带正负）',
    before_qty          INT         NOT NULL,
    after_qty           INT         NOT NULL,
    ref_type            VARCHAR(30)          DEFAULT NULL COMMENT 'ACTUAL_PURCHASE_ORDER/SALES_ORDER/MANUAL',
    ref_id              BIGINT               DEFAULT NULL,
    ref_no              VARCHAR(64)          DEFAULT NULL,
    operator_id         BIGINT               DEFAULT NULL,
    remark              VARCHAR(255)         DEFAULT NULL,
    create_time         DATETIME             DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_tx_supplier_product (supplier_product_id),
    KEY idx_tx_ref (ref_type, ref_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '出入库流水（只增不改）';
