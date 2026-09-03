package africa.zokomart.admin.module.ad.controller;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.ad.dto.AdAiModelSaveDTO;
import africa.zokomart.admin.module.ad.service.AdAiModelService;
import africa.zokomart.admin.module.ad.vo.AdAiModelVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ad/models")
@RequiredArgsConstructor
@Tag(name = "广告-模型管理")
public class AdAiModelController {

    private final AdAiModelService service;

    @GetMapping
    @SaCheckPermission("ad:model:list")
    public Result<PageResult<AdAiModelVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(service.page(keyword, enabled, current, size));
    }

    /** 生图页下拉：持生图权限即可读，无需模型管理权限。 */
    @GetMapping("/enabled")
    @SaCheckPermission("ad:image:generate")
    public Result<List<AdAiModelVO>> listEnabled() {
        return Result.ok(service.listEnabled());
    }

    @PostMapping
    @SaCheckPermission("ad:model:create")
    public Result<Long> create(@Valid @RequestBody AdAiModelSaveDTO dto) {
        return Result.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("ad:model:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AdAiModelSaveDTO dto) {
        dto.setId(id);
        service.update(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ad:model:delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
