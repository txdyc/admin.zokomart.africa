package africa.zokomart.admin.module.wcsync.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.wcsync.dto.WcSyncRequest;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncResultVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "独立站同步")
public class WcSyncController {

    private final WcSyncService wcSyncService;

    @PostMapping("/api/wc-sync/supplier-brands")
    @SaCheckPermission("wc:sync")
    public Result<WcSyncResultVO> sync(@Valid @RequestBody WcSyncRequest req) {
        return Result.ok(wcSyncService.syncSupplierBrands(req.getSupplierId(), req.getBrandIds()));
    }
}
