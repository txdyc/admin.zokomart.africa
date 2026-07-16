package africa.zokomart.admin.module.dashboard.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.dashboard.service.DashboardService;
import africa.zokomart.admin.module.dashboard.vo.DailyTrendVO;
import africa.zokomart.admin.module.dashboard.vo.DashboardOverviewVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** CEO 数据仪表盘（只读聚合）。区间参数 from/to 缺省为最近 30 天。 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "数据仪表盘")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @SaCheckPermission("dashboard:view")
    public Result<DashboardOverviewVO> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(dashboardService.overview(from, to));
    }

    @GetMapping("/sales-trend")
    @SaCheckPermission("dashboard:view")
    public Result<List<DailyTrendVO>> salesTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(dashboardService.salesTrend(from, to));
    }
}
