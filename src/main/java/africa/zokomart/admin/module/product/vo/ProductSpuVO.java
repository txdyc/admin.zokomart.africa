package africa.zokomart.admin.module.product.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductSpuVO {
    private Long id;
    private String name;
    private Long brandId;
    private Long categoryId;
    private String mainImage;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
}
