package africa.zokomart.admin.module.purchase.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_order")
public class PurchaseOrder extends BaseEntity {
    private String orderNo;
    private Long planId;
    private Long supplierId;
    private String status;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private String remark;
}
