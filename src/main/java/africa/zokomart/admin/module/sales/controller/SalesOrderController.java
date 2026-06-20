package africa.zokomart.admin.module.sales.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.sales.constant.SalesConst;
import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.service.SalesOrderService;
import africa.zokomart.admin.module.sales.vo.SalesOrderLabelVO;
import africa.zokomart.admin.module.sales.vo.SalesOrderVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales-orders")
@RequiredArgsConstructor
@Tag(name = "销售订单")
public class SalesOrderController {

    /** 拥有此权限的角色可查看全部销售订单；否则仅本人。 */
    private static final String PERM_VIEW_ALL = "sales:order:list:all";

    private final SalesOrderService salesOrderService;

    @PostMapping
    @SaCheckPermission("sales:order:create")
    public Result<Long> create(@Valid @RequestBody SalesOrderCreateDTO dto) {
        return Result.ok(salesOrderService.create(dto));
    }

    @GetMapping
    @SaCheckPermission("sales:order:list")
    public Result<PageResult<SalesOrderVO>> page(
            @RequestParam(required = false) Boolean completed,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        // 非全局查看权限者强制本人范围（#15）
        Long salespersonId = StpUtil.hasPermission(PERM_VIEW_ALL) ? null : StpUtil.getLoginIdAsLong();
        return Result.ok(salesOrderService.page(salespersonId, completed, current, size));
    }

    @GetMapping("/labels")
    @SaCheckPermission("sales:order:list")
    public Result<List<SalesOrderLabelVO>> labels(
            @RequestParam(defaultValue = SalesConst.PENDING_DISPATCH) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long salespersonId = StpUtil.hasPermission(PERM_VIEW_ALL) ? null : StpUtil.getLoginIdAsLong();
        return Result.ok(salesOrderService.labels(salespersonId, status, date));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("sales:order:list")
    public Result<SalesOrderVO> detail(@PathVariable Long id) {
        return Result.ok(salesOrderService.getDetail(id));
    }
}
