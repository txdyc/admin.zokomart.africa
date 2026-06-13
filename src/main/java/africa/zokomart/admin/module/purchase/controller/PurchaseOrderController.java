package africa.zokomart.admin.module.purchase.controller;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.purchase.dto.PaymentMarkDTO;
import africa.zokomart.admin.module.purchase.service.PurchaseOrderPaymentService;
import africa.zokomart.admin.module.purchase.service.PurchaseOrderService;
import africa.zokomart.admin.module.purchase.vo.PurchaseOrderVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService orderService;
    private final PurchaseOrderPaymentService paymentService;

    @GetMapping
    @SaCheckPermission("purchase:order:list")
    public Result<PageResult<PurchaseOrderVO>> page(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.page(planId, supplierId, status, current, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("purchase:order:list")
    public Result<PurchaseOrderVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.getDetail(id));
    }

    @PutMapping("/{id}/items/payment")
    @SaCheckPermission("purchase:order:pay")
    public Result<Void> markPayment(@PathVariable Long id, @Valid @RequestBody PaymentMarkDTO dto) {
        paymentService.mark(id, dto.getItemIds(), dto.getPaymentStatus());
        return Result.ok();
    }

    @PostMapping("/{id}/confirm")
    @SaCheckPermission("purchase:order:confirm")
    public Result<Long> confirm(@PathVariable Long id) {
        return Result.ok(paymentService.confirm(id));
    }
}
