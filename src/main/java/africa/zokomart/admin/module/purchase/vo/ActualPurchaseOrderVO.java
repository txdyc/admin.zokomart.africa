package africa.zokomart.admin.module.purchase.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ActualPurchaseOrderVO {
    private Long id;
    private String actualNo;
    private Long purchaseOrderId;
    private Long supplierId;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private List<ActualPurchaseOrderItemVO> items;
}
