package africa.zokomart.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/编辑入参。id 为空表示新增（此时 password 必填，由 service 校验）。
 */
@Data
public class UserSaveDTO {
    private Long id;
    @NotBlank(message = "用户名不能为空")
    private String username;
    /** 新增必填；编辑留空表示不改密码 */
    private String password;
    private String nickname;
    private String phone;
    private String email;
    private Integer status;
    private String remark;
    private List<Long> roleIds;
}
