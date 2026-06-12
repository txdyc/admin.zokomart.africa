package africa.zokomart.admin.module.basedata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupplierSaveDTO {
    private Long id;
    @NotBlank(message = "供应商名不能为空")
    private String name;
    private String code;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private Integer status;
    private String remark;
}
