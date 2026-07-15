package africa.zokomart.admin.module.sales.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderableProductVO {
    private Long supplierProductId;
    private String productName;
    private String productCode;
    private String supplierName;
    private Integer quantity;      // COALESCE(inventory_stock.quantity, 0)
    private BigDecimal retailPrice;
}
