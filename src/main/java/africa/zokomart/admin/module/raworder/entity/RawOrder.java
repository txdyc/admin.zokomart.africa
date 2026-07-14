package africa.zokomart.admin.module.raworder.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("raw_order")
public class RawOrder extends BaseEntity {
    private LocalDate orderDate;
    private String brand;
    private BigDecimal price;
    private String customerName;
    private String city;
    private String address;
    private String telephone;
    private String productName;
    private String productCode;
    private Integer quantity;
    private String status;
    private BigDecimal cod;
    private BigDecimal freight;
    private BigDecimal balance;
}
