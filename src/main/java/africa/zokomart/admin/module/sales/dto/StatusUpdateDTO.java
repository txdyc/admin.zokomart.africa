package africa.zokomart.admin.module.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StatusUpdateDTO {
    @NotBlank(message = "目标状态不能为空")
    private String status;

    /** 可空：签收/拒签等 outcome 时顺带补录或修正派送费；null 保留原值。 */
    @DecimalMin(value = "0", message = "派送费不能为负")
    private BigDecimal deliveryFee;
}
