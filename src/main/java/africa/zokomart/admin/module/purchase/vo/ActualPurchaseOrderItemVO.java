package africa.zokomart.admin.module.purchase.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ActualPurchaseOrderItemVO {
    private Long id;
    private Long actualOrderId;
    private Long purchaseOrderItemId;
    private Long supplierProductId;
    private String productName;
    private Integer qty;
    private BigDecimal wholesalePrice;
    private BigDecimal amount;
    private String inboundStatus;
    private Integer inboundQty;
    private LocalDateTime inboundTime;
}
