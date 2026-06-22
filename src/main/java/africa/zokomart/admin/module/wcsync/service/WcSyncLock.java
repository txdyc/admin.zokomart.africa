package africa.zokomart.admin.module.wcsync.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** 进程内全局单飞锁：任意时刻只允许一个同步任务。单机单 jar 运行，无需分布式锁。 */
@Component
public class WcSyncLock {

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 获取锁；已被持有返回 false。 */
    public boolean tryAcquire() {
        return running.compareAndSet(false, true);
    }

    /** 释放锁（幂等：未持有时调用无副作用）。 */
    public void release() {
        running.set(false);
    }
}
