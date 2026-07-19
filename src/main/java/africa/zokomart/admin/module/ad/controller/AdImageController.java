package africa.zokomart.admin.module.ad.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.ad.dto.AdDiscardDTO;
import africa.zokomart.admin.module.ad.dto.AdGenerateDTO;
import africa.zokomart.admin.module.ad.dto.AdKeepDTO;
import africa.zokomart.admin.module.ad.service.AdImageService;
import africa.zokomart.admin.module.ad.vo.AdGenerateVO;
import africa.zokomart.admin.module.ad.vo.AdProductImageVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ad/images")
@RequiredArgsConstructor
@Tag(name = "广告-AI 生图")
public class AdImageController {

    private final AdImageService service;

    @PostMapping("/generate")
    @SaCheckPermission("ad:image:generate")
    public Result<AdGenerateVO> generate(@Valid @RequestBody AdGenerateDTO dto) {
        return Result.ok(service.generate(dto));
    }

    @PostMapping("/keep")
    @SaCheckPermission("ad:image:keep")
    public Result<List<Long>> keep(@Valid @RequestBody AdKeepDTO dto) {
        return Result.ok(service.keep(dto));
    }

    @PostMapping("/discard")
    @SaCheckPermission("ad:image:keep")
    public Result<Void> discard(@Valid @RequestBody AdDiscardDTO dto) {
        service.discard(dto);
        return Result.ok();
    }

    @GetMapping
    @SaCheckPermission("ad:image:list")
    public Result<List<AdProductImageVO>> list(@RequestParam Long supplierProductId) {
        return Result.ok(service.listByProduct(supplierProductId));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ad:image:delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
