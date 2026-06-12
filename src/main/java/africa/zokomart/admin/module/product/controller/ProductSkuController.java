package africa.zokomart.admin.module.product.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.product.dto.ProductSkuSaveDTO;
import africa.zokomart.admin.module.product.service.ProductSkuService;
import africa.zokomart.admin.module.product.vo.ProductSkuVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product-skus")
@RequiredArgsConstructor
public class ProductSkuController {

    private final ProductSkuService skuService;

    @GetMapping("/{id}")
    @SaCheckPermission("product:sku:list")
    public Result<ProductSkuVO> detail(@PathVariable Long id) {
        return Result.ok(skuService.getDetail(id));
    }

    @PostMapping
    @SaCheckPermission("product:sku:create")
    public Result<Long> create(@Valid @RequestBody ProductSkuSaveDTO dto) {
        return Result.ok(skuService.createSku(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("product:sku:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSkuSaveDTO dto) {
        dto.setId(id);
        skuService.updateSku(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("product:sku:delete")
    public Result<Void> delete(@PathVariable Long id) {
        skuService.deleteSku(id);
        return Result.ok();
    }
}
