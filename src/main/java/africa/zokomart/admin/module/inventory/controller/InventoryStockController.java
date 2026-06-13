package africa.zokomart.admin.module.inventory.controller;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.inventory.dto.StockAdjustDTO;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.inventory.vo.InventoryStockVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/stocks")
@RequiredArgsConstructor
public class InventoryStockController {

    private final InventoryStockService stockService;

    @GetMapping
    @SaCheckPermission("inventory:list")
    public Result<PageResult<InventoryStockVO>> page(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(stockService.pageStocks(supplierId, brandId, categoryId, keyword, current, size));
    }

    @PutMapping("/{supplierProductId}")
    @SaCheckPermission("inventory:edit")
    public Result<Void> adjust(@PathVariable Long supplierProductId, @Valid @RequestBody StockAdjustDTO dto) {
        stockService.adjust(supplierProductId, dto.getQuantity(), dto.getRemark());
        return Result.ok();
    }
}
