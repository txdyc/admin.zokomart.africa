package africa.zokomart.admin.module.purchase.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actual_purchase_order_item")
public class ActualPurchaseOrderItem extends BaseEntity {
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
