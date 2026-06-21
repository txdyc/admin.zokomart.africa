package africa.zokomart.admin.module.wcsync.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** WooCommerce 同步任务：一次同步的进度/计数/失败明细。继承 BaseEntity（雪花主键+审计+乐观锁）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wc_sync_job")
public class WcSyncJob extends BaseEntity {
    private Long supplierId;
    private String brandIds;        // JSON 数组字符串
    private String operator;
    private String status;          // 见 WcSyncJobStatus
    private Integer total;
    private Integer processed;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer draftedCount;
    private Integer failedCount;
    private String failedItems;     // JSON: List<WcSyncRowError>
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
