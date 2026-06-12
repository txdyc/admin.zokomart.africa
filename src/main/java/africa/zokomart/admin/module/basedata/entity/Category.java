package africa.zokomart.admin.module.basedata.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("category")
public class Category extends BaseEntity {
    private Long parentId;
    private String name;
    private Integer sort;
    private Integer status;
}
