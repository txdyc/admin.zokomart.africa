package africa.zokomart.admin.module.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlanRejectDTO {
    @NotBlank(message = "退回原因不能为空")
    private String reason;
}
