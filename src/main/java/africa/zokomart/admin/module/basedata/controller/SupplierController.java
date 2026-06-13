package africa.zokomart.admin.module.basedata.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.basedata.vo.SupplierVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Tag(name = "供应商管理")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @SaCheckPermission("supplier:list")
    public Result<PageResult<SupplierVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(supplierService.pageSuppliers(keyword, status, current, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("supplier:list")
    public Result<SupplierVO> detail(@PathVariable Long id) {
        return Result.ok(supplierService.getDetail(id));
    }

    @PostMapping
    @SaCheckPermission("supplier:create")
    public Result<Long> create(@Valid @RequestBody SupplierSaveDTO dto) {
        return Result.ok(supplierService.createSupplier(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("supplier:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SupplierSaveDTO dto) {
        dto.setId(id);
        supplierService.updateSupplier(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("supplier:delete")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return Result.ok();
    }
}
