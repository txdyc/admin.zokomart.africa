package africa.zokomart.admin.module.purchase.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PaymentMarkDTO {
    @NotEmpty(message = "未选择明细")
    private List<Long> itemIds;

    @NotNull(message = "付款状态不能为空")
    private String paymentStatus;
}
