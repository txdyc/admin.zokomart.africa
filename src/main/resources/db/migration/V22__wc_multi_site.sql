-- ===========================================================================
-- V22: 多站点 WooCommerce 同步。
-- 1) wc_sync_record 增加站点维度：主键改为 (supplier_product_id, site_code)，
--    既有行回填 site_code='zokomart'，原映射原样保留。
-- 2) wc_sync_job 增加 site_code：一次运行 × 一个站点 = 一个 job。
-- 3) 广告图 media id 按站点存：新建 ad_image_site_media，
--    迁移 ad_product_image.wc_media_id → (ad_image_id, 'zokomart') 行后删除原列。
-- 无新增菜单/权限，沿用 wc:sync。
-- ===========================================================================

-- 1) 产品映射：每站一行
ALTER TABLE wc_sync_record
    ADD COLUMN site_code VARCHAR(32) NOT NULL DEFAULT 'zokomart' COMMENT '目标站点 code',
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (supplier_product_id, site_code);
ALTER TABLE wc_sync_record ALTER COLUMN site_code DROP DEFAULT;

-- 2) 同步任务：一次运行 × 一个站点 = 一个 job
ALTER TABLE wc_sync_job
    ADD COLUMN site_code VARCHAR(32) NOT NULL DEFAULT 'zokomart' COMMENT '目标站点 code';
ALTER TABLE wc_sync_job ALTER COLUMN site_code DROP DEFAULT;

-- 3) 广告图 media id：每站一行
CREATE TABLE ad_image_site_media (
    ad_image_id BIGINT      NOT NULL COMMENT 'ad_product_image.id',
    site_code   VARCHAR(32) NOT NULL COMMENT '目标站点 code',
    wc_media_id BIGINT      NOT NULL COMMENT '该站点上的 media 附件 id',
    create_time DATETIME             DEFAULT NULL,
    update_time DATETIME             DEFAULT NULL,
    PRIMARY KEY (ad_image_id, site_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '广告图在各站点的 media id 映射';

INSERT INTO ad_image_site_media (ad_image_id, site_code, wc_media_id, create_time)
SELECT id, 'zokomart', wc_media_id, NOW() FROM ad_product_image WHERE wc_media_id IS NOT NULL;

ALTER TABLE ad_product_image DROP COLUMN wc_media_id;
