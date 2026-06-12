package africa.zokomart.admin.module.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductSkuSaveDTO {
    private Long id;
    @NotNull(message = "所属 SPU 不能为空")
    private Long spuId;
    @NotBlank(message = "SKU 编码不能为空")
    private String skuCode;
    private String spec;
    private String image;
    @DecimalMin(value = "0", message = "售价不能为负")
    private BigDecimal price;
    private Integer status;
}
