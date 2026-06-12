package africa.zokomart.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPwdDTO {
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
