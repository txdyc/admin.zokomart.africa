package africa.zokomart.admin.module.inventory.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_stock")
public class InventoryStock extends BaseEntity {
    private Long supplierProductId;
    private Long supplierId;
    private Long brandId;
    private Long categoryId;
    private Integer quantity;
}
