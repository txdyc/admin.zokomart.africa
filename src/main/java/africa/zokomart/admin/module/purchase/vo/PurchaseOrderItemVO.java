package africa.zokomart.admin.module.purchase.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseOrderItemVO {
    private Long id;
    private Long orderId;
    private Long supplierProductId;
    private String productName;
    private String productCode;
    private BigDecimal wholesalePrice;
    private Integer qty;
    private BigDecimal amount;
    private String paymentStatus;
}
