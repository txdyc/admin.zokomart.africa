package africa.zokomart.admin.module.ad.support;

import africa.zokomart.admin.common.file.FileStorageService;
import africa.zokomart.admin.module.ad.service.impl.AdImageServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/** 兜底清理：每天 04:00 删除 ad-temp 下超过 24h 的残留文件（用户关页未丢弃）。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdTempCleanupJob {

    private final FileStorageService storage;

    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanup() {
        try {
            Path dir = storage.resolvePublicUrl(
                    "/files/" + AdImageServiceImpl.TEMP_CATEGORY + "/x").getParent();
            if (dir == null || !Files.isDirectory(dir)) return;
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> {
                    try { return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff); }
                    catch (Exception e) { return false; }
                }).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                });
            }
        } catch (Exception e) {
            log.warn("ad-temp cleanup failed", e);
        }
    }
}
