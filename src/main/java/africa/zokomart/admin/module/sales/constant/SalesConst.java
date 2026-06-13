package africa.zokomart.admin.module.sales.constant;

import java.util.Map;
import java.util.Set;

/** 销售订单状态与状态机（PRD §5.13）。 */
public final class SalesConst {

    private SalesConst() {
    }

    public static final String PENDING_DISPATCH = "PENDING_DISPATCH";
    public static final String DISPATCHING = "DISPATCHING";
    public static final String SIGNED = "SIGNED";
    public static final String SIGNED_PAID = "SIGNED_PAID";
    public static final String UNREACHABLE = "UNREACHABLE";
    public static final String REJECTED = "REJECTED";

    public static final String NO_SALES = "SO";

    /** 已签收类（可拒收、可完成）。 */
    public static final Set<String> SIGNED_STATES = Set.of(SIGNED, SIGNED_PAID);

    /** 合法状态流转：当前状态 -> 允许的目标状态集合（不含派送动作，dispatch 单列）。 */
    public static final Map<String, Set<String>> TRANSITIONS = Map.of(
            DISPATCHING, Set.of(SIGNED, SIGNED_PAID, UNREACHABLE, REJECTED),
            UNREACHABLE, Set.of(DISPATCHING, REJECTED),
            SIGNED, Set.of(SIGNED_PAID, UNREACHABLE, REJECTED),
            SIGNED_PAID, Set.of(REJECTED)
    );
}
