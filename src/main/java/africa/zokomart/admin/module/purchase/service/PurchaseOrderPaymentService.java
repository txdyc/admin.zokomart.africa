package africa.zokomart.admin.module.purchase.service;

import java.util.List;

public interface PurchaseOrderPaymentService {

    /** 批量标记采购订单明细付款状态（PAID/UNPAID），并刷新订单已付款金额。 */
    void mark(Long orderId, List<Long> itemIds, String paymentStatus);

    /** 确认：取已付款明细生成实际采购单（PENDING_INBOUND），订单置 CONFIRMED。返回实际采购单 id。 */
    Long confirm(Long orderId);
}
