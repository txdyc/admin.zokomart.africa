package africa.zokomart.admin.module.dashboard.service.impl;

import africa.zokomart.admin.module.dashboard.mapper.DashboardMapper;
import africa.zokomart.admin.module.dashboard.service.DashboardService;
import africa.zokomart.admin.module.dashboard.vo.DailyTrendVO;
import africa.zokomart.admin.module.dashboard.vo.DashboardOverviewVO;
import africa.zokomart.admin.module.dashboard.vo.FinancialSummaryVO;
import africa.zokomart.admin.module.dashboard.vo.FulfillmentVO;
import africa.zokomart.admin.module.dashboard.vo.InventorySummaryVO;
import africa.zokomart.admin.module.dashboard.vo.OpenPoVO;
import africa.zokomart.admin.module.dashboard.vo.ReturnStatVO;
import africa.zokomart.admin.module.dashboard.vo.StatusCountVO;
import africa.zokomart.admin.module.sales.constant.SalesConst;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 仪表盘聚合：Mapper 取原始汇总，服务层计算派生指标（毛利/率/客单价/漏斗）。 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 默认区间天数（含今日）。 */
    private static final int DEFAULT_DAYS = 30;
    /** Top N 列表长度。 */
    private static final int TOP_N = 8;
    /** 热销产品列表长度。 */
    private static final int TOP_PRODUCTS = 10;
    /** 供应商支出列表长度。 */
    private static final int TOP_SUPPLIERS = 5;

    private final DashboardMapper mapper;

    @Override
    public DashboardOverviewVO overview(LocalDate from, LocalDate to) {
        LocalDate[] range = resolveRange(from, to);
        LocalDate f = range[0];
        LocalDate t = range[1];
        LocalDateTime fromDt = f.atStartOfDay();
        LocalDateTime toDt = t.plusDays(1).atStartOfDay();

        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setFrom(f);
        vo.setTo(t);
        vo.setFinancial(buildFinancial(fromDt, toDt));
        vo.setFulfillment(buildFulfillment(fromDt, toDt, f, t));
        vo.setInventory(buildInventory());
        vo.setRevenueByCategory(mapper.revenueByCategory(fromDt, toDt, TOP_N));
        vo.setRevenueByBrand(mapper.revenueByBrand(fromDt, toDt, TOP_N));
        vo.setTopSuppliers(mapper.topSuppliers(fromDt, toDt, TOP_SUPPLIERS));
        vo.setTopProducts(mapper.topProducts(fromDt, toDt, TOP_PRODUCTS));
        return vo;
    }

    @Override
    public List<DailyTrendVO> salesTrend(LocalDate from, LocalDate to) {
        LocalDate[] range = resolveRange(from, to);
        return mapper.salesTrend(range[0].atStartOfDay(), range[1].plusDays(1).atStartOfDay());
    }

    // ---------------------------------------------------------------------

    private FinancialSummaryVO buildFinancial(LocalDateTime fromDt, LocalDateTime toDt) {
        FinancialSummaryVO fin = mapper.financialRaw(fromDt, toDt);
        BigDecimal cogs = nz(mapper.cogs(fromDt, toDt));
        fin.setCogs(cogs);
        fin.setCodCollected(nz(fin.getNetRevenue()));

        BigDecimal netRevenue = nz(fin.getNetRevenue());
        BigDecimal grossProfit = netRevenue.subtract(cogs).subtract(nz(fin.getDeliveryFeeTotal()));
        fin.setGrossProfit(grossProfit);
        fin.setGrossMargin(ratio(grossProfit, netRevenue));

        long completed = fin.getCompletedOrders() == null ? 0 : fin.getCompletedOrders();
        fin.setAov(completed == 0 ? BigDecimal.ZERO
                : netRevenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP));
        return fin;
    }

    private FulfillmentVO buildFulfillment(LocalDateTime fromDt, LocalDateTime toDt,
                                           LocalDate f, LocalDate t) {
        Map<String, Long> counts = mapper.statusCounts(fromDt, toDt).stream()
                .collect(Collectors.toMap(StatusCountVO::getStatus, StatusCountVO::getCnt));

        FulfillmentVO ff = new FulfillmentVO();
        long pendingDispatch = counts.getOrDefault(SalesConst.PENDING_DISPATCH, 0L);
        long dispatching = counts.getOrDefault(SalesConst.DISPATCHING, 0L);
        long signed = counts.getOrDefault(SalesConst.SIGNED, 0L);
        long signedPaid = counts.getOrDefault(SalesConst.SIGNED_PAID, 0L);
        long unreachable = counts.getOrDefault(SalesConst.UNREACHABLE, 0L);
        long rejected = counts.getOrDefault(SalesConst.REJECTED, 0L);

        ff.setPendingDispatch(pendingDispatch);
        ff.setDispatching(dispatching);
        ff.setSigned(signed);
        ff.setSignedPaid(signedPaid);
        ff.setUnreachable(unreachable);
        ff.setRejected(rejected);

        long delivered = signed + signedPaid;
        long dispatchedTotal = delivered + rejected + unreachable + dispatching;
        long settled = delivered + rejected + unreachable; // 已出结果的派送单（不含仍在途）
        ff.setDelivered(delivered);
        ff.setDispatchedTotal(dispatchedTotal);
        ff.setDeliverySuccessRate(ratio(BigDecimal.valueOf(delivered), BigDecimal.valueOf(settled)));
        ff.setRejectionRate(ratio(BigDecimal.valueOf(rejected), BigDecimal.valueOf(settled)));

        ReturnStatVO rs = mapper.returnStat(fromDt, toDt);
        long rejectQty = rs == null || rs.getRejectQty() == null ? 0 : rs.getRejectQty();
        long totalQty = rs == null || rs.getTotalQty() == null ? 0 : rs.getTotalQty();
        ff.setReturnRate(ratio(BigDecimal.valueOf(rejectQty), BigDecimal.valueOf(totalQty)));

        ff.setAvgDeliveryHours(mapper.avgDeliveryHours(fromDt, toDt));
        ff.setPlaced(nz0(mapper.placedCount(f, t.plusDays(1))));
        return ff;
    }

    private InventorySummaryVO buildInventory() {
        InventorySummaryVO inv = mapper.inventorySummary();
        if (inv == null) {
            inv = new InventorySummaryVO();
        }
        OpenPoVO po = mapper.openPurchaseOrders();
        inv.setOpenPoCount(po == null ? 0L : nz0(po.getOpenPoCount()));
        inv.setOpenPoAmount(po == null ? BigDecimal.ZERO : nz(po.getOpenPoAmount()));
        return inv;
    }

    private LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now();
        LocalDate f = from != null ? from : t.minusDays(DEFAULT_DAYS - 1L);
        if (f.isAfter(t)) {
            LocalDate tmp = f;
            f = t;
            t = tmp;
        }
        return new LocalDate[]{f, t};
    }

    /** 比值，保留 4 位小数；分母 ≤ 0 返回 0。 */
    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return nz(numerator).divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static long nz0(Long v) {
        return v == null ? 0L : v;
    }
}
