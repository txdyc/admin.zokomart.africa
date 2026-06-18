package africa.zokomart.admin.module.wcsync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** WooCommerce 同步记录：后台产品 ↔ WC 商品 id 映射 + 最近同步状态。主键=供应商产品id。 */
@Data
@TableName("wc_sync_record")
public class WcSyncRecord {
    @TableId(type = IdType.INPUT)
    private Long supplierProductId;
    private Long wcProductId;
    private String sku;
    private String lastStatus;       // CREATED/UPDATED/DRAFTED/FAILED
    private LocalDateTime lastSyncedTime;
    private String lastError;
}
