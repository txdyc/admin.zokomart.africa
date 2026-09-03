package africa.zokomart.admin.module.ad.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 广告图在各站点的 WC media id 映射：一张广告图 × 一个站点一行。复合主键=(ad_image_id, site_code)。 */
@Data
@TableName("ad_image_site_media")
public class AdImageSiteMedia {
    /** 复合主键之一：ad_product_image.id。 */
    private Long adImageId;
    /** 复合主键之一：目标站点 code。 */
    private String siteCode;
    private Long wcMediaId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
