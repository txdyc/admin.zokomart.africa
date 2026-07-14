package africa.zokomart.admin.module.sales.service;

import java.math.BigDecimal;

public interface SalesLogisticsService {

    /** 派送：PENDING_DISPATCH→DISPATCHING，记录物流服务商与派送费。 */
    void dispatch(Long orderId, Long logisticsProviderId, BigDecimal deliveryFee);

    /** 更新签收类状态（状态机校验）。REJECTED 全拒签自动回补库存并完成、实收=0。 */
    void updateStatus(Long orderId, String targetStatus, BigDecimal deliveryFee);

    /** 标记明细拒收量（仅 SIGNED/SIGNED_PAID）：回补库存 + 写 REJECT_RETURN 流水。 */
    void markReject(Long orderId, Long itemId, Integer rejectQty);

    /** 确认完成（可从 SIGNED/SIGNED_PAID）：结算实收金额 actual_amount，completed=1，完成后只读。 */
    void complete(Long orderId);
}
