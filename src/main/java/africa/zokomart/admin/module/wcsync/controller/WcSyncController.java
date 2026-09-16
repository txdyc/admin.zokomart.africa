package africa.zokomart.admin.module.wcsync.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.wcsync.dto.WcSyncRequest;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import africa.zokomart.admin.module.wcsync.vo.WcSyncSiteVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "独立站同步")
public class WcSyncController {

    private final WcSyncService wcSyncService;

    /** 启动同步（每站一个 job），立即返回 jobIds（与所选站点一一对应）。 */
    @PostMapping("/api/wc-sync/supplier-brands")
    @SaCheckPermission("wc:sync")
    public Result<Map<String, List<Long>>> sync(@Valid @RequestBody WcSyncRequest req) {
        List<Long> jobIds = wcSyncService.startSync(req.getSupplierId(), req.getBrandIds(),
                req.getSiteCodes());
        return Result.ok(Map.of("jobIds", jobIds));
    }

    /** 目标站点列表（弹框选择用）。 */
    @GetMapping("/api/wc-sync/sites")
    @SaCheckPermission("wc:sync")
    public Result<List<WcSyncSiteVO>> sites() {
        return Result.ok(wcSyncService.listSites());
    }

    /** 查任务进度。 */
    @GetMapping("/api/wc-sync/jobs/{id}")
    @SaCheckPermission("wc:sync")
    public Result<WcSyncJobVO> job(@PathVariable("id") Long id) {
        return Result.ok(wcSyncService.getJob(id));
    }

    /** 历史任务分页。 */
    @GetMapping("/api/wc-sync/jobs")
    @SaCheckPermission("wc:sync")
    public Result<IPage<WcSyncJobVO>> jobs(@RequestParam(required = false) Long supplierId,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(wcSyncService.listJobs(supplierId, current, size));
    }
}
