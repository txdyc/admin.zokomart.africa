package africa.zokomart.admin.module.purchase.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_plan")
public class PurchasePlan extends BaseEntity {
    private String planNo;
    private String status;
    private LocalDateTime submitTime;
    private Long approverId;
    private LocalDateTime approveTime;
    private String approveRemark;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private String remark;
}
