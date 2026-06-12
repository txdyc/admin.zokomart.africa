package africa.zokomart.admin.module.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductSkuVO {
    private Long id;
    private Long spuId;
    private String skuCode;
    private String spec;
    private String image;
    private BigDecimal price;
    private Integer status;
    private LocalDateTime createTime;
}
