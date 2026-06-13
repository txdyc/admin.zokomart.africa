package africa.zokomart.admin.module.sales.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_order_item")
public class SalesOrderItem extends BaseEntity {
    private Long orderId;
    private Long supplierProductId;
    private String productName;
    private String productCode;
    private BigDecimal unitPrice;
    private Integer qty;
    private Integer rejectQty;
    private BigDecimal amount;
    private BigDecimal actualAmount;
}
