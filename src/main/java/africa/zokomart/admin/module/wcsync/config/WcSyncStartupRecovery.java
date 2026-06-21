package africa.zokomart.admin.module.wcsync.config;

import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 启动恢复：单机重启时，任何残留 RUNNING 的任务即视为已死 → 标 INTERRUPTED。 */
@Component
@RequiredArgsConstructor
public class WcSyncStartupRecovery implements ApplicationRunner {

    private final WcSyncJobMapper jobMapper;

    @Override
    public void run(ApplicationArguments args) {
        WcSyncJob upd = new WcSyncJob();
        upd.setStatus(WcSyncJobStatus.INTERRUPTED);
        upd.setEndTime(LocalDateTime.now());
        jobMapper.update(upd, Wrappers.<WcSyncJob>lambdaUpdate()
                .eq(WcSyncJob::getStatus, WcSyncJobStatus.RUNNING));
    }
}
