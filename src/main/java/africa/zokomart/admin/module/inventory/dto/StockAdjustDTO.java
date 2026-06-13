package africa.zokomart.admin.module.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockAdjustDTO {
    @NotNull(message = "库存数量不能为空")
    @Min(value = 0, message = "库存数量不能为负")
    private Integer quantity;

    private String remark;
}
