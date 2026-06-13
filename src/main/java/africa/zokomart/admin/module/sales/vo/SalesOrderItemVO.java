package africa.zokomart.admin.module.sales.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesOrderItemVO {
    private Long id;
    private Long supplierProductId;
    private String productName;
    private String productCode;
    private BigDecimal unitPrice;
    private Integer qty;
    private Integer rejectQty;
    private BigDecimal amount;
    private BigDecimal actualAmount;
}
