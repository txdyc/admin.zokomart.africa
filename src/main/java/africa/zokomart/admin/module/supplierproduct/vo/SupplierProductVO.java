package africa.zokomart.admin.module.supplierproduct.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SupplierProductVO {
    private Long id;
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
    private LocalDateTime createTime;
}
