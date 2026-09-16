package africa.zokomart.admin.module.sales.importer;

import java.time.LocalDate;

/**
 * 订单分组键 = 归一化电话 + 归一化姓名 + 归一化地址 + 订单日期。
 * 归一化只在内存里用于分组与查重，入库仍写 Excel 原值。
 */
public record SalesImportKey(String phone, String name, String address, LocalDate orderDate) {

    public static SalesImportKey of(String phone, String name, String address, LocalDate orderDate) {
        return new SalesImportKey(normalizePhone(phone), normalizeText(name),
                normalizeText(address), orderDate);
    }

    /**
     * 加纳号码国际区号归一：0244239227 ≡ 233244239227 ≡ +233 244 239 227 ≡ 244239227。
     * 归一不出 9 位时原样保留数字串——仍可作键，只是不跨格式归并。
     */
    public static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        String d = raw.replaceAll("\\D", "");
        if (d.startsWith("00233")) {
            return d.substring(5);
        }
        if (d.startsWith("233") && d.length() == 12) {
            return d.substring(3);
        }
        if (d.startsWith("0") && d.length() == 10) {
            return d.substring(1);
        }
        return d;
    }

    /** trim + 折叠内部连续空白 + 转小写。刻意不做全半角转换。 */
    public static String normalizeText(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
