package africa.zokomart.admin.inventory;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.module.basedata.entity.Supplier;
import africa.zokomart.admin.module.basedata.mapper.SupplierMapper;
import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** changeStock 的 allowNegative：销售出库可为负（缺货欠货）；其它调用仍禁止负库存。 */
@SpringBootTest
class StockBackorderServiceTest {

    @Autowired
    InventoryStockService stockService;
    @Autowired
    SupplierProductMapper supplierProductMapper;
    @Autowired
    SupplierMapper supplierMapper;

    private Long newProduct(String tag) {
        Supplier sup = new Supplier();
        sup.setName("BOSup_" + tag);
        sup.setStatus(1);
        supplierMapper.insert(sup);
        SupplierProduct sp = new SupplierProduct();
        sp.setSupplierId(sup.getId());
        sp.setName("BO_" + tag);
        sp.setProductCode("BOC_" + tag);
        sp.setWholesalePrice(new BigDecimal("100"));
        sp.setRetailPrice(new BigDecimal("200"));
        sp.setMinPurchaseQty(1);
        sp.setStatus(1);
        supplierProductMapper.insert(sp);
        return sp.getId();
    }

    @Test
    void allowNegative_true_lets_sales_out_go_below_zero() {
        long ts = System.nanoTime();
        Long spId = newProduct("neg" + ts);
        // no stock row yet -> sell 3 with allowNegative -> quantity = -3
        stockService.changeStock(spId, -3, InventoryConst.TYPE_SALES_OUT,
                InventoryConst.REF_SALES_ORDER, 999L, "SO-" + ts, "backorder", true);
        assertThat(stockService.getQty(spId)).isEqualTo(-3);
    }

    @Test
    void allowNegative_false_still_rejects_going_negative() {
        long ts = System.nanoTime();
        Long spId = newProduct("blk" + ts);
        assertThatThrownBy(() -> stockService.changeStock(spId, -1, InventoryConst.TYPE_MANUAL_ADJUST,
                InventoryConst.REF_MANUAL, null, null, "manual", false))
                .isInstanceOf(BusinessException.class);
    }
}
