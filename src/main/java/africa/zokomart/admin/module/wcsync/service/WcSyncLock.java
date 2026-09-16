package africa.zokomart.admin.module.wcsync.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** 进程内按站点单飞锁：同一站点任意时刻只允许一个同步任务，不同站点可并行。单机单 jar 运行，无需分布式锁。 */
@Component
public class WcSyncLock {

    private final Map<String, AtomicBoolean> siteLocks = new ConcurrentHashMap<>();

    /** 获取站点锁；该站点已被持有返回 false。 */
    public boolean tryAcquire(String siteCode) {
        return siteLocks.computeIfAbsent(siteCode, k -> new AtomicBoolean(false))
                .compareAndSet(false, true);
    }

    /** 释放站点锁（幂等：未持有时调用无副作用）。 */
    public void release(String siteCode) {
        AtomicBoolean lock = siteLocks.get(siteCode);
        if (lock != null) lock.set(false);
    }
}
