package africa.zokomart.admin.module.purchase.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_plan_item")
public class PurchasePlanItem extends BaseEntity {
    private Long planId;
    private Long supplierId;
    private Long supplierProductId;
    private Long brandId;
    private Long categoryId;
    private String productName;
    private String productCode;
    private BigDecimal wholesalePrice;
    private Integer minPurchaseQty;
    private Integer purchaseQty;
    private BigDecimal amount;
}
