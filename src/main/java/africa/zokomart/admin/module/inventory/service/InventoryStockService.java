package africa.zokomart.admin.module.inventory.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.inventory.entity.InventoryStock;
import africa.zokomart.admin.module.inventory.vo.InventoryStockVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface InventoryStockService extends IService<InventoryStock> {

    /** 当前库存数量；无记录返回 0。 */
    int getQty(Long supplierProductId);

    /** 库存列表：按供应商/品牌/分类联动筛选 + 分页，VO 含名称。 */
    PageResult<InventoryStockVO> pageStocks(Long supplierId, Long brandId, Long categoryId,
                                            String keyword, long current, long size);

    /** 手工调整库存到目标数量（带原因）：写 MANUAL_ADJUST 流水(前后值)，数量不可为负，乐观锁防并发。 */
    void adjust(Long supplierProductId, Integer targetQuantity, String remark);

    /**
     * 库存增减的唯一入口：inventory_stock(乐观锁) + inventory_transaction(流水) 双写。
     * 无库存记录则按需创建（冗余 supplier/brand/category 取自 supplier_product）。
     * qtyChange 为正入库、为负出库；出库后不可为负，否则抛 INSUFFICIENT_STOCK。
     */
    void changeStock(Long supplierProductId, int qtyChange, String type,
                     String refType, Long refId, String refNo, String remark);
}
