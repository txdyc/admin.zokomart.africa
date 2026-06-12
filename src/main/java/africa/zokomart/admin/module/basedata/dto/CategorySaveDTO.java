package africa.zokomart.admin.module.basedata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategorySaveDTO {
    private Long id;
    private Long parentId;
    @NotBlank(message = "分类名不能为空")
    private String name;
    private Integer sort;
    private Integer status;
}
