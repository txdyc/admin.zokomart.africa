package africa.zokomart.admin.module.wcsync.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** WooCommerce 同步记录：后台产品 × 站点 ↔ WC 商品 id 映射 + 最近同步状态。复合主键=(supplier_product_id, site_code)。 */
@Data
@TableName("wc_sync_record")
public class WcSyncRecord {
    /** 复合主键之一；MP 不支持复合 @TableId，改用条件构造器读写。 */
    private Long supplierProductId;
    /** 复合主键之一：目标站点 code。 */
    private String siteCode;
    private Long wcProductId;
    private String sku;
    private String lastStatus;       // CREATED/UPDATED/DRAFTED/FAILED
    private LocalDateTime lastSyncedTime;
    private String lastError;
    private Long wcImageId;          // WC 主图 media 附件 id（站点私有）
    private String syncedImageUrl;   // 上次 sideload 的源图 URL（站点私有）
}
