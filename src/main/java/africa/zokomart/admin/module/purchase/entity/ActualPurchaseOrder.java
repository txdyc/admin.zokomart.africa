package africa.zokomart.admin.module.purchase.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actual_purchase_order")
public class ActualPurchaseOrder extends BaseEntity {
    private String actualNo;
    private Long purchaseOrderId;
    private Long supplierId;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private String status;
    private String remark;
}
