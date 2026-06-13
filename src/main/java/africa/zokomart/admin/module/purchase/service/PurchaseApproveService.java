package africa.zokomart.admin.module.purchase.service;

public interface PurchaseApproveService {

    /** 通过：PENDING→APPROVED，按供应商分组生成采购订单。 */
    void approve(Long planId, Long approverId);

    /** 退回：PENDING→REJECTED，带退回原因。 */
    void reject(Long planId, Long approverId, String reason);
}
