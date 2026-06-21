package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.wcsync.service.WcSyncLock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WcSyncLockTest {

    @Test
    void single_flight_second_acquire_fails_until_release() {
        WcSyncLock lock = new WcSyncLock();
        assertTrue(lock.tryAcquire());
        assertFalse(lock.tryAcquire());   // 已持有 → 第二次失败
        lock.release();
        assertTrue(lock.tryAcquire());     // 释放后可再获取
        lock.release();
    }
}
