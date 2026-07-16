package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

/**
 * 履约与交付面板：订单漏斗计数 + 交付率。
 * 漏斗顶端 placed 来自 raw_order（导入的原始下单量），其余基于 sales_order 状态。
 */
@Data
public class FulfillmentVO {
    /** 顶端：原始订单导入量（raw_order，按 order_date 区间）。 */
    private Long placed;

    // ----- sales_order 各状态计数 -----
    private Long pendingDispatch;
    private Long dispatching;
    private Long signed;
    private Long signedPaid;
    private Long unreachable;
    private Long rejected;

    /** 已妥投 = signed + signedPaid。 */
    private Long delivered;
    /** 已派送（离仓）总量 = delivered + rejected + unreachable + dispatching。 */
    private Long dispatchedTotal;

    /** 妥投率 = delivered / (delivered + rejected + unreachable)（已出结果的派送单）。 */
    private java.math.BigDecimal deliverySuccessRate;
    /** 拒收率 = rejected / (delivered + rejected + unreachable)。 */
    private java.math.BigDecimal rejectionRate;
    /** 退货率 = Σreject_qty / Σqty（明细件数口径）。 */
    private java.math.BigDecimal returnRate;
    /** 平均妥投时长（小时）= AVG(sign_time - dispatch_time)。 */
    private Double avgDeliveryHours;
}
