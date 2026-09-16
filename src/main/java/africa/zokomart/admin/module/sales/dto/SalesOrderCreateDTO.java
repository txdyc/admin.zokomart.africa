package africa.zokomart.admin.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class SalesOrderCreateDTO {

    @NotBlank(message = "客户姓名不能为空")
    private String customerName;

    @NotBlank(message = "客户手机号不能为空")
    private String customerPhone;

    @NotBlank(message = "客户地址不能为空")
    private String customerAddress;

    private String remark;

    /** 可空：导入时来自 Excel 的 City；手工下单不传。 */
    @Size(max = 128, message = "城市长度不能超过 128")
    private String city;

    /** 可空：业务订单日期。为空则取今天（手工下单）。 */
    private LocalDate orderDate;

    @NotEmpty(message = "销售明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "供应商产品不能为空")
        private Long supplierProductId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量不能小于 1")
        private Integer qty;

        /** 可空：默认带出供应商产品零售价。 */
        private BigDecimal unitPrice;

        /** 可空：行金额。为空则按 unitPrice * qty。导入时传 Excel 的 Sale Price 原值，避免除不尽漂移。 */
        @DecimalMin(value = "0", message = "行金额不能为负")
        private BigDecimal amount;

        /** 可空：来源 Excel 的 Order ID，仅溯源。 */
        @Size(max = 64, message = "外部单号长度不能超过 64")
        private String externalOrderId;
    }
}
