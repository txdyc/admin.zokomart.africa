package africa.zokomart.admin.module.raworder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 原始订单行编辑入参：规则与 CSV 导入一致；status/freight/balance 可空并回落默认值。 */
@Data
public class RawOrderUpdateDTO {

    @NotNull
    private LocalDate orderDate;

    @NotBlank
    @Size(max = 128)
    private String brand;

    @NotNull
    @DecimalMin("0")
    private BigDecimal price;

    @NotBlank
    @Size(max = 128)
    private String customerName;

    @NotBlank
    @Size(max = 128)
    private String city;

    @NotBlank
    @Size(max = 512)
    private String address;

    @NotBlank
    @Size(max = 32)
    private String telephone;

    @NotBlank
    @Size(max = 255)
    private String productName;

    @NotBlank
    @Size(max = 64)
    private String productCode;

    @NotNull
    @Min(1)
    private Integer quantity;

    /** 可空：留空按导入约定回落默认状态 NOT_DISPATCHED；非空须为合法状态。 */
    private String status;

    @NotNull
    @DecimalMin("0")
    private BigDecimal cod;

    /** 可空：留空默认 0.00；非空须非负。 */
    @DecimalMin("0")
    private BigDecimal freight;

    /** 可空：留空默认 0.00；非空须非负。 */
    @DecimalMin("0")
    private BigDecimal balance;
}
