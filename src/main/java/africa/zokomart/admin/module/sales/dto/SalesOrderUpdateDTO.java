package africa.zokomart.admin.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 管理员修正销售订单。客户信息 / 城市 / 日期 / 备注任何状态都可改；
 * items 非 null 时视为整体替换明细，仅未派送（PENDING_DISPATCH）订单允许。
 */
@Data
public class SalesOrderUpdateDTO {

    @NotBlank(message = "客户姓名不能为空")
    @Size(max = 128, message = "客户姓名长度不能超过 128")
    private String customerName;

    @NotBlank(message = "客户手机号不能为空")
    @Size(max = 32, message = "客户手机号长度不能超过 32")
    private String customerPhone;

    @NotBlank(message = "客户地址不能为空")
    @Size(max = 512, message = "客户地址长度不能超过 512")
    private String customerAddress;

    @Size(max = 128, message = "城市长度不能超过 128")
    private String city;

    /** 可空：为空则保留原订单日期。 */
    private LocalDate orderDate;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 可空：null = 不修改明细；非 null 时不能为空列表。 */
    @Valid
    @Size(min = 1, message = "销售明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        /** 可空：已有明细的 id（保留行、就地更新）；为空则新增一行。 */
        private Long id;

        @NotNull(message = "供应商产品不能为空")
        private Long supplierProductId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量不能小于 1")
        private Integer qty;

        @NotNull(message = "单价不能为空")
        @DecimalMin(value = "0", message = "单价不能为负")
        private BigDecimal unitPrice;

        /** 可空：行金额，为空则按 unitPrice * qty。 */
        @DecimalMin(value = "0", message = "行金额不能为负")
        private BigDecimal amount;
    }
}
