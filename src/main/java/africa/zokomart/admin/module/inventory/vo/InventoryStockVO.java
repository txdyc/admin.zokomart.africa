package africa.zokomart.admin.module.inventory.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryStockVO {
    private Long id;
    private Long supplierProductId;
    private String productName;
    private String productCode;
    private Long supplierId;
    private String supplierName;
    private Long brandId;
    private String brandName;
    private Long categoryId;
    private String categoryName;
    private Integer quantity;
    private LocalDateTime updateTime;
}
