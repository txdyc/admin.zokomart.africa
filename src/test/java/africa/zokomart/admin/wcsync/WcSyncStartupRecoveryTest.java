package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.wcsync.config.WcSyncStartupRecovery;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class WcSyncStartupRecoveryTest {

    @Autowired WcSyncJobMapper jobMapper;
    @Autowired WcSyncStartupRecovery recovery;

    @Test
    void marks_dangling_running_as_interrupted() throws Exception {
        WcSyncJob job = new WcSyncJob();
        job.setSupplierId(1L);
        job.setStatus(WcSyncJobStatus.RUNNING);
        job.setTotal(5); job.setProcessed(2);
        job.setCreatedCount(0); job.setUpdatedCount(0);
        job.setDraftedCount(0); job.setFailedCount(0);
        jobMapper.insert(job);

        recovery.run(null);   // 模拟启动恢复

        assertEquals(WcSyncJobStatus.INTERRUPTED, jobMapper.selectById(job.getId()).getStatus());
    }
}
