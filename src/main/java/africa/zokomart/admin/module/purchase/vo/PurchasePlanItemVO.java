package africa.zokomart.admin.module.purchase.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchasePlanItemVO {
    private Long id;
    private Long supplierProductId;
    private Long supplierId;
    private Long brandId;
    private Long categoryId;
    private String productName;
    private String productCode;
    private BigDecimal wholesalePrice;
    private Integer minPurchaseQty;
    private Integer purchaseQty;
    private BigDecimal amount;
}
