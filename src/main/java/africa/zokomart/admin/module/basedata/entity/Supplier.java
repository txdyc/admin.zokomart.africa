package africa.zokomart.admin.module.basedata.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("supplier")
public class Supplier extends BaseEntity {
    private String name;
    private String code;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private Integer status;
    private String remark;
}
