package africa.zokomart.admin.module.ad.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ad_product_image")
public class AdProductImage extends BaseEntity {
    private Long supplierProductId;
    private String fileUrl;
    private String prompt;
    private Long modelId;
    private Long wcMediaId;
    private Integer sort;
}
