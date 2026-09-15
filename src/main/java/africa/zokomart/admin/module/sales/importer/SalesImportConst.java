package africa.zokomart.admin.module.sales.importer;

import java.time.LocalDate;
import java.util.List;

/** 销售订单 Excel 导入的外部数据契约：列名与上限。列名是对外契约，不可随意改。 */
public final class SalesImportConst {

    private SalesImportConst() {
    }

    public static final int MAX_ROWS = 1000;

    /** Excel 序列号 1 对应 1900-01-01，因 1900 闰年 bug，基准取 1899-12-30。 */
    public static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    public static final String COL_ORDER_ID = "Order ID";
    public static final String COL_SALE_PRICE = "Sale Price";
    public static final String COL_CUSTOMER_NAME = "Customer Name";
    public static final String COL_CITY = "City";
    public static final String COL_ADDRESS = "Shipping Address";
    public static final String COL_PHONE = "Phone Number";
    public static final String COL_PRODUCT_NAME = "Product Name";
    public static final String COL_PRODUCT_CODE = "Product Code";
    public static final String COL_QUANTITY = "Quantity";
    public static final String COL_ORDER_DATE = "Order Date";
    /** 可选列 "Status"：不在 REQUIRED_COLUMNS 里即为可选，存在时其值被忽略
     *（系统状态一律 PENDING_DISPATCH），没有代码需要引用这个列名，故不声明常量。 */

    public static final List<String> REQUIRED_COLUMNS = List.of(
            COL_ORDER_ID, COL_SALE_PRICE, COL_CUSTOMER_NAME, COL_CITY, COL_ADDRESS,
            COL_PHONE, COL_PRODUCT_NAME, COL_PRODUCT_CODE, COL_QUANTITY, COL_ORDER_DATE);

    /** 列名匹配：trim + 折叠内部空白 + 转小写。 */
    public static String normalizeHeader(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
