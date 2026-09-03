package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.wcsync.service.WcSyncLock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WcSyncLockTest {

    @Test
    void per_site_single_flight() {
        WcSyncLock lock = new WcSyncLock();
        // 同一站点：已持有 → 第二次失败
        assertTrue(lock.tryAcquire("zokomart"));
        assertFalse(lock.tryAcquire("zokomart"));
        // 不同站点：互不影响，可并行
        assertTrue(lock.tryAcquire("kianosmart"));
        // 释放后可再获取；未持有的站点 release 幂等无副作用
        lock.release("zokomart");
        assertTrue(lock.tryAcquire("zokomart"));
        lock.release("not-exist");
        lock.release("zokomart");
        lock.release("kianosmart");
    }
}
