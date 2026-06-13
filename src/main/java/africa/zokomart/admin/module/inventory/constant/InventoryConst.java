package africa.zokomart.admin.module.inventory.constant;

/** 库存流水类型 / 引用来源常量（与 PRD §4.7 一致）。 */
public final class InventoryConst {

    private InventoryConst() {
    }

    // 流水类型
    public static final String TYPE_PURCHASE_IN = "PURCHASE_IN";
    public static final String TYPE_SALES_OUT = "SALES_OUT";
    public static final String TYPE_REJECT_RETURN = "REJECT_RETURN";
    public static final String TYPE_MANUAL_ADJUST = "MANUAL_ADJUST";

    // 引用来源
    public static final String REF_ACTUAL_PURCHASE_ORDER = "ACTUAL_PURCHASE_ORDER";
    public static final String REF_SALES_ORDER = "SALES_ORDER";
    public static final String REF_MANUAL = "MANUAL";
}
