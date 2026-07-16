package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 财务概览（基于 sales_order，按下单时间 create_time 落在区间内的订单口径）。
 * 说明：净收入/COD 实收 = 已完成订单(completed=1)的 actual_amount；
 * COGS 取已完成订单实收货量 (qty - reject_qty) × 供应商批发价。
 */
@Data
public class FinancialSummaryVO {
    /** 下单口径成交额 = Σ total_amount（含未完成、未派送）。 */
    private BigDecimal gmv;
    /** 净收入 = 已完成订单 Σ actual_amount。 */
    private BigDecimal netRevenue;
    /** COD 已收现（= netRevenue，COD 模式下完成即到账）。 */
    private BigDecimal codCollected;
    /** 在途待收 = 派送中(DISPATCHING) 订单 Σ total_amount。 */
    private BigDecimal codOutstanding;
    /** 已售商品成本 = Σ (qty - reject_qty) × wholesale_price（仅已完成订单）。 */
    private BigDecimal cogs;
    /** 派送费总支出 = Σ delivery_fee（含被拒收的无效支出）。 */
    private BigDecimal deliveryFeeTotal;
    /** 拒收损耗 = 被拒收(REJECTED) 订单 Σ delivery_fee（deliveryFeeTotal 的子集）。 */
    private BigDecimal rejectionCost;
    /** 毛利 = netRevenue - cogs - deliveryFeeTotal。 */
    private BigDecimal grossProfit;
    /** 毛利率 = grossProfit / netRevenue（0~1，netRevenue=0 时为 0）。 */
    private BigDecimal grossMargin;
    /** 客单价 = netRevenue / completedOrders（无完成单为 0）。 */
    private BigDecimal aov;
    /** 已完成订单数（用于 AOV 分母）。 */
    private Long completedOrders;
}
