package africa.zokomart.admin.module.product.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_spu")
public class ProductSpu extends BaseEntity {
    private String name;
    private Long brandId;
    private Long categoryId;
    private String mainImage;
    private String description;
    private Integer status;
}
