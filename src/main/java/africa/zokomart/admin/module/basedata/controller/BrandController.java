package africa.zokomart.admin.module.basedata.controller;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.basedata.dto.BrandSaveDTO;
import africa.zokomart.admin.module.basedata.service.BrandService;
import africa.zokomart.admin.module.basedata.vo.BrandVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    @SaCheckPermission("brand:list")
    public Result<PageResult<BrandVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(brandService.pageBrands(keyword, status, current, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("brand:list")
    public Result<BrandVO> detail(@PathVariable Long id) {
        return Result.ok(brandService.getDetail(id));
    }

    @PostMapping
    @SaCheckPermission("brand:create")
    public Result<Long> create(@Valid @RequestBody BrandSaveDTO dto) {
        return Result.ok(brandService.createBrand(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("brand:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BrandSaveDTO dto) {
        dto.setId(id);
        brandService.updateBrand(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("brand:delete")
    public Result<Void> delete(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return Result.ok();
    }
}
