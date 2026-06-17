package africa.zokomart.admin.module.supplierproduct.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierProductSaveDTO {
    private Long id;

    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    @NotBlank(message = "产品名称不能为空")
    private String name;

    private Long brandId;
    private Long categoryId;

    @NotBlank(message = "产品编码不能为空")
    private String productCode;

    @DecimalMin(value = "0", message = "批发价不能为负")
    private BigDecimal wholesalePrice;

    @DecimalMin(value = "0", message = "零售价不能为负")
    private BigDecimal retailPrice;

    private String imageUrl;

    @Min(value = 1, message = "最小采购量不能小于 1")
    private Integer minPurchaseQty;

    private Long skuId;
    private Integer status;
    private String remark;
    private Integer qtyPerBox;
    private BigDecimal boxPrice;
    private String stockStatus;
}
