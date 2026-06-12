package africa.zokomart.admin.module.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProductSpuSaveDTO {
    private Long id;
    @NotBlank(message = "SPU 名称不能为空")
    private String name;
    private Long brandId;
    private Long categoryId;
    private String mainImage;
    private String description;
    private Integer status;
}
