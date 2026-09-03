package africa.zokomart.admin.module.wcsync.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 异步调度壳：把 runSync 放到独立线程跑。
 * 单独成 bean 是为了让 @Async 通过 Spring 代理生效（同类自调用不走代理）。
 * wcSyncExecutor 为单线程：多站点 job 顺序执行，对 WC 更友好。
 */
@Component
@RequiredArgsConstructor
public class WcSyncRunner {

    private final WcSyncService wcSyncService;

    @Async("wcSyncExecutor")
    public void run(Long jobId, Long supplierId, List<Long> brandIds, String siteCode) {
        wcSyncService.runSync(jobId, supplierId, brandIds, siteCode);
    }
}
