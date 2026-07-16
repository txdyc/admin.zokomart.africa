package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** CEO 概览：一次性返回仪表盘各板块。区间 [from, to]（闭区间，天）。 */
@Data
public class DashboardOverviewVO {
    private LocalDate from;
    private LocalDate to;

    private FinancialSummaryVO financial;
    private FulfillmentVO fulfillment;
    private InventorySummaryVO inventory;

    private List<NamedAmountVO> revenueByCategory;
    private List<NamedAmountVO> revenueByBrand;
    private List<NamedAmountVO> topSuppliers;
    private List<TopProductVO> topProducts;
}
