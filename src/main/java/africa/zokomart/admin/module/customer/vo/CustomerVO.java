package africa.zokomart.admin.module.customer.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客户列表项：由 sales_order 按手机号聚合得出。 */
@Data
public class CustomerVO {
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private Long orderCount;
    private BigDecimal totalAmount;
    private LocalDateTime lastOrderTime;
}
