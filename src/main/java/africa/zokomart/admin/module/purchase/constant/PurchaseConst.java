package africa.zokomart.admin.module.purchase.constant;

/** 采购链状态常量（与 PRD §4.6 / §5 状态机一致）。 */
public final class PurchaseConst {

    private PurchaseConst() {
    }

    // 采购计划状态
    public static final String PLAN_DRAFT = "DRAFT";
    public static final String PLAN_PENDING = "PENDING";
    public static final String PLAN_APPROVED = "APPROVED";
    public static final String PLAN_REJECTED = "REJECTED";

    // 采购订单状态
    public static final String ORDER_PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String ORDER_CONFIRMED = "CONFIRMED";

    // 采购订单明细付款状态
    public static final String PAY_UNSET = "UNSET";
    public static final String PAY_PAID = "PAID";
    public static final String PAY_UNPAID = "UNPAID";

    // 实际采购单状态
    public static final String ACTUAL_PENDING_INBOUND = "PENDING_INBOUND";
    public static final String ACTUAL_INBOUND_DONE = "INBOUND_DONE";

    // 实际采购单明细入库状态
    public static final String INBOUND_PENDING = "PENDING";
    public static final String INBOUND_DONE = "DONE";

    // 业务单号前缀
    public static final String NO_PLAN = "PP";
    public static final String NO_ORDER = "PO";
    public static final String NO_ACTUAL = "AP";
}
