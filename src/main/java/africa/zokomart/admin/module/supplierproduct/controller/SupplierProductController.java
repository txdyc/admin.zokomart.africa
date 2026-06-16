package africa.zokomart.admin.module.supplierproduct.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.basedata.vo.BrandVO;
import africa.zokomart.admin.module.basedata.vo.CategoryVO;
import africa.zokomart.admin.module.supplierproduct.dto.ImportScrapedRequest;
import africa.zokomart.admin.module.supplierproduct.dto.ScrapeRequest;
import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
import africa.zokomart.admin.module.supplierproduct.dto.SupplierProductSaveDTO;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductImportService;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductScrapeService;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductService;
import africa.zokomart.admin.module.supplierproduct.vo.SupplierProductImportResultVO;
import africa.zokomart.admin.module.supplierproduct.vo.SupplierProductVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "供应商产品")
public class SupplierProductController {

    private final SupplierProductService supplierProductService;
    private final SupplierProductImportService supplierProductImportService;
    private final SupplierProductScrapeService supplierProductScrapeService;
    private final africa.zokomart.admin.module.basedata.service.SupplierBrandService supplierBrandService;

    @GetMapping("/api/supplier-products")
    @SaCheckPermission("supplierProduct:list")
    public Result<PageResult<SupplierProductVO>> page(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(supplierProductService.pageSupplierProducts(
                supplierId, brandId, categoryId, keyword, status, current, size));
    }

    @GetMapping("/api/supplier-products/{id}")
    @SaCheckPermission("supplierProduct:list")
    public Result<SupplierProductVO> detail(@PathVariable Long id) {
        return Result.ok(supplierProductService.getDetail(id));
    }

    @PostMapping("/api/supplier-products")
    @SaCheckPermission("supplierProduct:create")
    public Result<Long> create(@Valid @RequestBody SupplierProductSaveDTO dto) {
        return Result.ok(supplierProductService.createSupplierProduct(dto));
    }

    @PostMapping("/api/supplier-products/import")
    @SaCheckPermission("supplierProduct:import")
    public Result<SupplierProductImportResultVO> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("supplierId") Long supplierId,
            @RequestParam("brandId") Long brandId,
            @RequestParam(value = "mode", defaultValue = "skip") String mode) {
        return Result.ok(supplierProductImportService.importCsv(supplierId, brandId, mode, file));
    }

    @PostMapping("/api/supplier-products/scrape")
    @SaCheckPermission("supplierProduct:import")
    public Result<java.util.List<ScrapedProductRow>> scrape(@Valid @RequestBody ScrapeRequest req) {
        return Result.ok(supplierProductScrapeService.scrape(req.getUrl()));
    }

    @PostMapping("/api/supplier-products/import-scraped")
    @SaCheckPermission("supplierProduct:import")
    public Result<SupplierProductImportResultVO> importScraped(@Valid @RequestBody ImportScrapedRequest req) {
        return Result.ok(supplierProductImportService.importScrapedRows(
                req.getSupplierId(), req.getBrandId(), req.getMode(), req.getRows()));
    }

    @PutMapping("/api/supplier-products/{id}")
    @SaCheckPermission("supplierProduct:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SupplierProductSaveDTO dto) {
        dto.setId(id);
        supplierProductService.updateSupplierProduct(dto);
        return Result.ok();
    }

    @DeleteMapping("/api/supplier-products/{id}")
    @SaCheckPermission("supplierProduct:delete")
    public Result<Void> delete(@PathVariable Long id) {
        supplierProductService.deleteSupplierProduct(id);
        return Result.ok();
    }

    // --- 采购页联动筛选：基于该供应商已有产品 distinct ---

    @GetMapping("/api/suppliers/{id}/brands")
    @SaCheckPermission("supplierProduct:list")
    public Result<List<BrandVO>> brandsBySupplier(@PathVariable Long id) {
        return Result.ok(supplierBrandService.listAuthorizedBrands(id));
    }

    @GetMapping("/api/suppliers/{id}/categories")
    @SaCheckPermission("supplierProduct:list")
    public Result<List<CategoryVO>> categoriesBySupplier(@PathVariable Long id) {
        return Result.ok(supplierProductService.listCategoriesBySupplier(id));
    }
}
