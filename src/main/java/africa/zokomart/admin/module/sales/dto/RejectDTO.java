package africa.zokomart.admin.module.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RejectDTO {
    @NotNull(message = "明细不能为空")
    private Long itemId;

    @NotNull(message = "拒收数量不能为空")
    @Min(value = 1, message = "拒收数量不能小于 1")
    private Integer rejectQty;
}
