package africa.zokomart.admin.module.sales.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 面单专用出参：只含贴纸所需字段。 */
@Data
public class SalesOrderLabelVO {
    private Long id;
    private String orderNo;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private Integer totalQty;
    private BigDecimal totalAmount;
}
