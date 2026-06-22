package africa.zokomart.admin.module.wcsync.service;

import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface WcSyncJobService {

    /** 建 RUNNING 任务行，返回持久化后的实体（含雪花 id）。 */
    WcSyncJob createRunning(Long supplierId, List<Long> brandIds, int total, String operator);

    /** 持久化任务当前进度/计数/状态。 */
    void save(WcSyncJob job);

    /** 查单个任务 VO；不存在返回 null。 */
    WcSyncJobVO getVO(Long jobId);

    /** 按供应商分页查历史任务（按 id 倒序）。 */
    IPage<WcSyncJobVO> page(Long supplierId, long current, long size);
}
