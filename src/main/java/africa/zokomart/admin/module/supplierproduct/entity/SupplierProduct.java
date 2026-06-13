package africa.zokomart.admin.module.supplierproduct.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("supplier_product")
public class SupplierProduct extends BaseEntity {
    private Long supplierId;
    private String name;
    private Long brandId;
    private Long categoryId;
    private String productCode;
    private BigDecimal wholesalePrice;
    private BigDecimal retailPrice;
    private String imageUrl;
    private Integer minPurchaseQty;
    private Long skuId;
    private Integer status;
    private String remark;
}
