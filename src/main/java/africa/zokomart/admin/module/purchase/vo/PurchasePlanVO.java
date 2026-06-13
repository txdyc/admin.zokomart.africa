package africa.zokomart.admin.module.purchase.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchasePlanVO {
    private Long id;
    private String planNo;
    private String status;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private LocalDateTime submitTime;
    private Long approverId;
    private LocalDateTime approveTime;
    private String approveRemark;
    private String remark;
    private LocalDateTime createTime;
    private List<PurchasePlanItemVO> items;
}
