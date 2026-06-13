package africa.zokomart.admin.module.purchase.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.purchase.dto.PlanRejectDTO;
import africa.zokomart.admin.module.purchase.dto.PurchasePlanSaveDTO;
import africa.zokomart.admin.module.purchase.service.PurchaseApproveService;
import africa.zokomart.admin.module.purchase.service.PurchasePlanService;
import africa.zokomart.admin.module.purchase.vo.PurchasePlanVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-plans")
@RequiredArgsConstructor
@Tag(name = "采购计划")
public class PurchasePlanController {

    private final PurchasePlanService planService;
    private final PurchaseApproveService approveService;

    @GetMapping
    @SaCheckPermission("purchase:plan:list")
    public Result<PageResult<PurchasePlanVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(planService.page(status, supplierId, current, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("purchase:plan:list")
    public Result<PurchasePlanVO> detail(@PathVariable Long id) {
        return Result.ok(planService.getDetail(id));
    }

    @PostMapping
    @SaCheckPermission("purchase:plan:create")
    public Result<Long> create(@Valid @RequestBody PurchasePlanSaveDTO dto) {
        return Result.ok(planService.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("purchase:plan:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PurchasePlanSaveDTO dto) {
        dto.setId(id);
        planService.update(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("purchase:plan:delete")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/submit")
    @SaCheckPermission("purchase:plan:submit")
    public Result<Void> submit(@PathVariable Long id) {
        planService.submit(id);
        return Result.ok();
    }

    @PostMapping("/{id}/approve")
    @SaCheckPermission("purchase:plan:approve")
    public Result<Void> approve(@PathVariable Long id) {
        approveService.approve(id, StpUtil.getLoginIdAsLong());
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    @SaCheckPermission("purchase:plan:approve")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody PlanRejectDTO dto) {
        approveService.reject(id, StpUtil.getLoginIdAsLong(), dto.getReason());
        return Result.ok();
    }
}
