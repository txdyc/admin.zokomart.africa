package africa.zokomart.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class RoleSaveDTO {
    private Long id;
    @NotBlank(message = "角色名不能为空")
    private String name;
    @NotBlank(message = "角色编码不能为空")
    private String code;
    private Integer sort;
    private Integer status;
    private String remark;
    private List<Long> menuIds;
}
