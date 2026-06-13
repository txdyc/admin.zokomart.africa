package africa.zokomart.admin.module.product.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.product.dto.ProductSpuSaveDTO;
import africa.zokomart.admin.module.product.service.ProductSkuService;
import africa.zokomart.admin.module.product.service.ProductSpuService;
import africa.zokomart.admin.module.product.vo.ProductSkuVO;
import africa.zokomart.admin.module.product.vo.ProductSpuVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-spus")
@RequiredArgsConstructor
@Tag(name = "商品 SPU")
public class ProductSpuController {

    private final ProductSpuService spuService;
    private final ProductSkuService skuService;

    @GetMapping
    @SaCheckPermission("product:spu:list")
    public Result<PageResult<ProductSpuVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(spuService.pageSpus(keyword, brandId, categoryId, status, current, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("product:spu:list")
    public Result<ProductSpuVO> detail(@PathVariable Long id) {
        return Result.ok(spuService.getDetail(id));
    }

    @GetMapping("/{id}/skus")
    @SaCheckPermission("product:sku:list")
    public Result<List<ProductSkuVO>> skus(@PathVariable Long id) {
        return Result.ok(skuService.listBySpu(id));
    }

    @PostMapping
    @SaCheckPermission("product:spu:create")
    public Result<Long> create(@Valid @RequestBody ProductSpuSaveDTO dto) {
        return Result.ok(spuService.createSpu(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("product:spu:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSpuSaveDTO dto) {
        dto.setId(id);
        spuService.updateSpu(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("product:spu:delete")
    public Result<Void> delete(@PathVariable Long id) {
        spuService.deleteSpu(id);
        return Result.ok();
    }
}
