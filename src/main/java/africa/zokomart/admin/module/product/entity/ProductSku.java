package africa.zokomart.admin.module.product.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class ProductSku extends BaseEntity {
    private Long spuId;
    private String skuCode;
    private String spec;
    private String image;
    private BigDecimal price;
    private Integer status;
}
