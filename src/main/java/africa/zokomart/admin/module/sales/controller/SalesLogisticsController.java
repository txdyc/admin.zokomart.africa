package africa.zokomart.admin.module.sales.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.sales.dto.DispatchDTO;
import africa.zokomart.admin.module.sales.dto.RejectDTO;
import africa.zokomart.admin.module.sales.dto.StatusUpdateDTO;
import africa.zokomart.admin.module.sales.service.SalesLogisticsService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales-orders")
@RequiredArgsConstructor
public class SalesLogisticsController {

    private final SalesLogisticsService logisticsService;

    @PostMapping("/{id}/dispatch")
    @SaCheckPermission("logistics:dispatch")
    public Result<Void> dispatch(@PathVariable Long id, @Valid @RequestBody DispatchDTO dto) {
        logisticsService.dispatch(id, dto.getLogisticsProviderId(), dto.getDeliveryFee());
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission("logistics:status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        logisticsService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    @PutMapping("/{id}/items/reject")
    @SaCheckPermission("logistics:reject")
    public Result<Void> markReject(@PathVariable Long id, @Valid @RequestBody RejectDTO dto) {
        logisticsService.markReject(id, dto.getItemId(), dto.getRejectQty());
        return Result.ok();
    }

    @PostMapping("/{id}/complete")
    @SaCheckPermission("logistics:complete")
    public Result<Void> complete(@PathVariable Long id) {
        logisticsService.complete(id);
        return Result.ok();
    }
}
