package africa.zokomart.admin.module.raworder.constant;

import java.util.Set;

/** 原始订单状态：仅 CSV 允许的四个值，其它一律视为非法。 */
public final class RawOrderStatus {

    private RawOrderStatus() {
    }

    public static final String PAID = "PAID";
    public static final String RECIPIENT_REFUSED = "RECIPIENT_REFUSED";
    public static final String UNABLE_TO_CONTACT_RECIPIENT = "UNABLE_TO_CONTACT_RECIPIENT";
    public static final String RECIPIENT_UNABLE_TO_PAY = "RECIPIENT_UNABLE_TO_PAY";

    public static final Set<String> ALL = Set.of(
            PAID, RECIPIENT_REFUSED, UNABLE_TO_CONTACT_RECIPIENT, RECIPIENT_UNABLE_TO_PAY);
}
