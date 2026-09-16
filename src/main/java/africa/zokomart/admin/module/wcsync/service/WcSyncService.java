package africa.zokomart.admin.module.wcsync.service;

import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import africa.zokomart.admin.module.wcsync.vo.WcSyncSiteVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface WcSyncService {

    /**
     * 启动同步：校验+按站点抢锁+每站建一个任务+异步派发，立即返回 jobIds（与站点一一对应）。
     * 任一站点锁被占抛 WC_SYNC_RUNNING；站点未配置抛 WC_NOT_CONFIGURED。
     * siteCodes 为空 = 全部已配置站点。
     */
    List<Long> startSync(Long supplierId, List<Long> brandIds, List<String> siteCodes);

    /** 同步主循环（同步执行，供异步壳与测试直接调用）。结束置终态并释放该站点锁。 */
    void runSync(Long jobId, Long supplierId, List<Long> brandIds, String siteCode);

    /** 查任务进度。 */
    WcSyncJobVO getJob(Long jobId);

    /** 历史任务分页。 */
    IPage<WcSyncJobVO> listJobs(Long supplierId, long current, long size);

    /** 目标站点列表（供前端选择）。 */
    List<WcSyncSiteVO> listSites();
}
