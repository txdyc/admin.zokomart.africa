package africa.zokomart.admin.module.purchase.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseOrderVO {
    private Long id;
    private String orderNo;
    private Long planId;
    private Long supplierId;
    private String status;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private String remark;
    private LocalDateTime createTime;
    private List<PurchaseOrderItemVO> items;
}
