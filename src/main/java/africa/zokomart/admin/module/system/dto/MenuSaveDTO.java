package africa.zokomart.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MenuSaveDTO {
    private Long id;
    private Long parentId;
    @NotBlank(message = "菜单名不能为空")
    private String name;
    @NotNull(message = "菜单类型不能为空")
    private Integer type;
    private String permCode;
    private String routePath;
    private String component;
    private String icon;
    private Integer sort;
    private Integer visible;
    private Integer status;
}
