package africa.zokomart.admin.module.wcsync.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 同步任务进度出参（前端轮询用）。 */
@Data
public class WcSyncJobVO {
    private Long jobId;
    private String status;          // RUNNING/SUCCESS/PARTIAL/FAILED/INTERRUPTED
    private int total;
    private int processed;
    private int created;
    private int updated;
    private int drafted;
    private int failed;
    private List<WcSyncRowError> failedItems;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
