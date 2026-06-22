package africa.zokomart.admin.module.wcsync.service;

import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface WcSyncService {

    /** 启动同步：校验+抢锁+建任务+异步派发，立即返回 jobId。锁被占抛 WC_SYNC_RUNNING。 */
    Long startSync(Long supplierId, List<Long> brandIds);

    /** 同步主循环（同步执行，供异步壳与测试直接调用）。结束置终态并释放锁。 */
    void runSync(Long jobId, Long supplierId, List<Long> brandIds);

    /** 查任务进度。 */
    WcSyncJobVO getJob(Long jobId);

    /** 历史任务分页。 */
    IPage<WcSyncJobVO> listJobs(Long supplierId, long current, long size);
}
