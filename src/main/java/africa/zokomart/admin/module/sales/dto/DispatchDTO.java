package africa.zokomart.admin.module.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DispatchDTO {
    @NotNull(message = "物流服务商不能为空")
    private Long logisticsProviderId;

    /** 派送费可空：区域惯例是送达后站点才告知，NULL=未知（区别于 0=免费）。 */
    @DecimalMin(value = "0", message = "派送费不能为负")
    private BigDecimal deliveryFee;
}
