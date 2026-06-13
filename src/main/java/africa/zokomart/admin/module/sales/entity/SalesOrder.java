package africa.zokomart.admin.module.sales.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_order")
public class SalesOrder extends BaseEntity {
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
}
