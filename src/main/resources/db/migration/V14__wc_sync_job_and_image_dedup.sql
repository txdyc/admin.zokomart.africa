-- ===========================================================================
-- V14: WooCommerce 同步任务表 wc_sync_job + wc_sync_record 图片幂等两列。
-- 无新增菜单/权限，沿用 wc:sync（菜单 2065）。
-- ===========================================================================
CREATE TABLE wc_sync_job (
    id             BIGINT       NOT NULL COMMENT '雪花主键',
    supplier_id    BIGINT       NOT NULL COMMENT '供应商 id',
    brand_ids      VARCHAR(255)          DEFAULT NULL COMMENT '选中品牌 id JSON 数组，如 [1,2]',
    operator       VARCHAR(64)           DEFAULT NULL COMMENT '触发人 loginId',
    status         VARCHAR(16)  NOT NULL COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED/INTERRUPTED',
    total          INT          NOT NULL DEFAULT 0,
    processed      INT          NOT NULL DEFAULT 0,
    created_count  INT          NOT NULL DEFAULT 0,
    updated_count  INT          NOT NULL DEFAULT 0,
    drafted_count  INT          NOT NULL DEFAULT 0,
    failed_count   INT          NOT NULL DEFAULT 0,
    failed_items   TEXT                  DEFAULT NULL COMMENT '失败明细 JSON，封顶 200 条',
    start_time     DATETIME              DEFAULT NULL,
    end_time       DATETIME              DEFAULT NULL,
    create_time    DATETIME              DEFAULT NULL,
    update_time    DATETIME              DEFAULT NULL,
    create_by      BIGINT                DEFAULT NULL,
    update_by      BIGINT                DEFAULT NULL,
    deleted        INT          NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_supplier (supplier_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'WooCommerce 同步任务';

ALTER TABLE wc_sync_record
    ADD COLUMN wc_image_id      BIGINT       DEFAULT NULL COMMENT 'WC 主图 media 附件 id',
    ADD COLUMN synced_image_url VARCHAR(512) DEFAULT NULL COMMENT '上次 sideload 的源图 URL';
