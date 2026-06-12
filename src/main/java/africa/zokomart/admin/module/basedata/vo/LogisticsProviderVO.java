package africa.zokomart.admin.module.basedata.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LogisticsProviderVO {
    private Long id;
    private String name;
    private String code;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
