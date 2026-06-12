package africa.zokomart.admin.module.basedata.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("brand")
public class Brand extends BaseEntity {
    private String name;
    private String code;
    private String logoUrl;
    private Integer sort;
    private Integer status;
    private String remark;
}
