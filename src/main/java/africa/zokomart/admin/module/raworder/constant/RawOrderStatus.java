package africa.zokomart.admin.module.raworder.constant;

import java.util.Set;

/** 原始订单状态：CSV 允许的取值，其它一律视为非法。 */
public final class RawOrderStatus {

    private RawOrderStatus() {
    }

    /** 未派送（前端 zh-CN 显示「未派送」）：导入时 status 列留空的默认值。 */
    public static final String NOT_DISPATCHED = "NOT_DISPATCHED";
    public static final String PAID = "PAID";
    public static final String RECIPIENT_REFUSED = "RECIPIENT_REFUSED";
    public static final String UNABLE_TO_CONTACT_RECIPIENT = "UNABLE_TO_CONTACT_RECIPIENT";
    public static final String RECIPIENT_UNABLE_TO_PAY = "RECIPIENT_UNABLE_TO_PAY";

    /** 导入时 status 列留空的默认状态。 */
    public static final String DEFAULT = NOT_DISPATCHED;

    public static final Set<String> ALL = Set.of(
            NOT_DISPATCHED, PAID, RECIPIENT_REFUSED, UNABLE_TO_CONTACT_RECIPIENT, RECIPIENT_UNABLE_TO_PAY);
}
