package africa.zokomart.admin.module.basedata.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.basedata.dto.SupplierBrandAssignDTO;
import africa.zokomart.admin.module.basedata.service.SupplierBrandService;
import africa.zokomart.admin.module.basedata.vo.SupplierBrandVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "供应商品牌授权")
public class SupplierBrandController {

    private final SupplierBrandService supplierBrandService;

    @GetMapping("/api/suppliers/{id}/authorized-brands")
    @SaCheckPermission("supplier:brand:list")
    public Result<List<SupplierBrandVO>> list(@PathVariable Long id) {
        return Result.ok(supplierBrandService.listBySupplier(id));
    }

    @PutMapping("/api/suppliers/{id}/authorized-brands")
    @SaCheckPermission("supplier:brand:assign")
    public Result<Void> assign(@PathVariable Long id, @RequestBody SupplierBrandAssignDTO dto) {
        supplierBrandService.assign(id, dto.getBrandIds());
        return Result.ok();
    }
}
