package africa.zokomart.admin.module.sales.importer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Excel 一行的解析结果。
 * error != null 表示该行字段非法，其余字段可能为 null；调用方据此让整个订单失败。
 */
public record SalesImportRow(
        int rowNum,
        String externalOrderId,
        BigDecimal salePrice,
        String customerName,
        String city,
        String address,
        String phone,
        String productName,
        String productCode,
        Integer quantity,
        LocalDate orderDate,
        String error) {

    public static SalesImportRow error(int rowNum, String customerName, String phone,
                                       String address, LocalDate orderDate, String reason) {
        return new SalesImportRow(rowNum, null, null, customerName, null, address, phone,
                null, null, null, orderDate, reason);
    }
}
