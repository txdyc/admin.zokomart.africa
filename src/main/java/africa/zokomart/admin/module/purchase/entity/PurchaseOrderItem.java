package africa.zokomart.admin.module.purchase.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_order_item")
public class PurchaseOrderItem extends BaseEntity {
    private Long orderId;
    private Long supplierProductId;
    private String productName;
    private String productCode;
    private BigDecimal wholesalePrice;
    private Integer qty;
    private BigDecimal amount;
    private String paymentStatus;
}
