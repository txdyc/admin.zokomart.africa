package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 待付款采购订单聚合（合并进 InventorySummaryVO）。 */
@Data
public class OpenPoVO {
    private Long openPoCount;
    private BigDecimal openPoAmount;
}
