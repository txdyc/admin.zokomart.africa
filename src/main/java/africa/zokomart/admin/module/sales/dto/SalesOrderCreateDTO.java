package africa.zokomart.admin.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
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
    }
}
