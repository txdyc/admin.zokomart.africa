package africa.zokomart.admin.module.basedata.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("logistics_provider")
public class LogisticsProvider extends BaseEntity {
    private String name;
    private String code;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private String remark;
}
