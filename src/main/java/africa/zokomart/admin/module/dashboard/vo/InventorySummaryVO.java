package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 库存与采购快照（即时值，不按时间区间）。 */
@Data
public class InventorySummaryVO {
    /** 在手库存货值 = Σ quantity × wholesale_price。 */
    private BigDecimal inventoryValue;
    /** 有库存记录的 SKU 数。 */
    private Long skuCount;
    /** 在手总件数。 */
    private Long totalUnits;
    /** 待付款采购订单数（status=PENDING_PAYMENT）。 */
    private Long openPoCount;
    /** 待付款采购订单金额。 */
    private BigDecimal openPoAmount;
}
