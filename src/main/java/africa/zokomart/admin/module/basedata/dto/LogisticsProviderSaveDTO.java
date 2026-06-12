package africa.zokomart.admin.module.basedata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogisticsProviderSaveDTO {
    private Long id;
    @NotBlank(message = "物流服务商名不能为空")
    private String name;
    private String code;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private String remark;
}
