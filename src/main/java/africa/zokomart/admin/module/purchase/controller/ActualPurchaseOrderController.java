package africa.zokomart.admin.module.purchase.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.inventory.dto.InboundDTO;
import africa.zokomart.admin.module.inventory.service.InboundService;
import africa.zokomart.admin.module.purchase.service.ActualPurchaseOrderService;
import africa.zokomart.admin.module.purchase.vo.ActualPurchaseOrderVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/actual-purchase-orders")
@RequiredArgsConstructor
@Tag(name = "实际采购单")
public class ActualPurchaseOrderController {

    private final ActualPurchaseOrderService actualService;
    private final InboundService inboundService;

    @GetMapping
    @SaCheckPermission("purchase:order:list")
    public Result<PageResult<ActualPurchaseOrderVO>> page(
            @RequestParam(required = false) Long purchaseOrderId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(actualService.page(purchaseOrderId, status, current, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("purchase:order:list")
    public Result<ActualPurchaseOrderVO> detail(@PathVariable Long id) {
        return Result.ok(actualService.getDetail(id));
    }

    @PostMapping("/{id}/inbound")
    @SaCheckPermission("inventory:inbound")
    public Result<Void> inbound(@PathVariable Long id, @RequestBody(required = false) InboundDTO dto) {
        inboundService.inbound(id, dto == null ? null : dto.getItemIds());
        return Result.ok();
    }
}
