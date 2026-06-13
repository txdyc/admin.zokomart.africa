package africa.zokomart.admin.module.sales.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SalesOrderVO {
    private Long id;
    private String orderNo;
    private String status;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private Long salespersonId;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private BigDecimal actualAmount;
    private Long logisticsProviderId;
    private BigDecimal deliveryFee;
    private LocalDateTime dispatchTime;
    private LocalDateTime signTime;
    private LocalDateTime completeTime;
    private Integer completed;
    private String remark;
    private LocalDateTime createTime;
    private List<SalesOrderItemVO> items;
}
