package africa.zokomart.admin.module.inventory.service;

import africa.zokomart.admin.module.inventory.entity.InventoryStock;
import com.baomidou.mybatisplus.extension.service.IService;

public interface InventoryStockService extends IService<InventoryStock> {

    /** 当前库存数量；无记录返回 0。 */
    int getQty(Long supplierProductId);

    /**
     * 库存增减的唯一入口：inventory_stock(乐观锁) + inventory_transaction(流水) 双写。
     * 无库存记录则按需创建（冗余 supplier/brand/category 取自 supplier_product）。
     * qtyChange 为正入库、为负出库；出库后不可为负，否则抛 INSUFFICIENT_STOCK。
     */
    void changeStock(Long supplierProductId, int qtyChange, String type,
                     String refType, Long refId, String refNo, String remark);
}
