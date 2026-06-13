package africa.zokomart.admin.module.sales.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StatusUpdateDTO {
    @NotBlank(message = "目标状态不能为空")
    private String status;
}
