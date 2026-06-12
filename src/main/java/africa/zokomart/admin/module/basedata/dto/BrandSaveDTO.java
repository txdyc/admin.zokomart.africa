package africa.zokomart.admin.module.basedata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BrandSaveDTO {
    private Long id;
    @NotBlank(message = "品牌名不能为空")
    private String name;
    private String code;
    private String logoUrl;
    private Integer sort;
    private Integer status;
    private String remark;
}
