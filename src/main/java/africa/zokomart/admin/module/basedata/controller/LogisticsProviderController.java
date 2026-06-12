package africa.zokomart.admin.module.basedata.controller;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.basedata.dto.LogisticsProviderSaveDTO;
import africa.zokomart.admin.module.basedata.service.LogisticsProviderService;
import africa.zokomart.admin.module.basedata.vo.LogisticsProviderVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logistics-providers")
@RequiredArgsConstructor
public class LogisticsProviderController {

    private final LogisticsProviderService providerService;

    @GetMapping
    @SaCheckPermission("logisticsProvider:list")
    public Result<PageResult<LogisticsProviderVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(providerService.pageProviders(keyword, status, current, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("logisticsProvider:list")
    public Result<LogisticsProviderVO> detail(@PathVariable Long id) {
        return Result.ok(providerService.getDetail(id));
    }

    @PostMapping
    @SaCheckPermission("logisticsProvider:create")
    public Result<Long> create(@Valid @RequestBody LogisticsProviderSaveDTO dto) {
        return Result.ok(providerService.createProvider(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("logisticsProvider:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody LogisticsProviderSaveDTO dto) {
        dto.setId(id);
        providerService.updateProvider(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("logisticsProvider:delete")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.deleteProvider(id);
        return Result.ok();
    }
}
