package africa.zokomart.admin.module.raworder.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RawOrderVO {
    private Long id;
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
    private LocalDateTime createTime;
}
