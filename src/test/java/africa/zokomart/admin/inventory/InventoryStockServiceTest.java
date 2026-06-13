package africa.zokomart.admin.inventory;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.basedata.dto.BrandSaveDTO;
import africa.zokomart.admin.module.basedata.dto.CategorySaveDTO;
import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.service.BrandService;
import africa.zokomart.admin.module.basedata.service.CategoryService;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.entity.InventoryTransaction;
import africa.zokomart.admin.module.inventory.mapper.InventoryTransactionMapper;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.inventory.vo.InventoryStockVO;
import africa.zokomart.admin.module.supplierproduct.dto.SupplierProductSaveDTO;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 6 Task 6.1：库存手工调整（写 MANUAL_ADJUST 流水、负数拒绝）+ 列表联动筛选带名称。
 */
@SpringBootTest
class InventoryStockServiceTest {

    @Autowired
    InventoryStockService stockService;
    @Autowired
    InventoryTransactionMapper txMapper;
    @Autowired
    SupplierService supplierService;
    @Autowired
    BrandService brandService;
    @Autowired
    CategoryService categoryService;
    @Autowired
    SupplierProductService supplierProductService;

    private long supplierId;
    private long brandId;
    private long categoryId;
    private String supplierName;

    private long seedSupplierProduct() {
        long ts = System.nanoTime();
        supplierName = "INV_Sup_" + ts;
        SupplierSaveDTO sup = new SupplierSaveDTO();
        sup.setName(supplierName);
        sup.setStatus(1);
        supplierId = supplierService.createSupplier(sup);

        BrandSaveDTO brand = new BrandSaveDTO();
        brand.setName("INV_Brand_" + ts);
        brand.setStatus(1);
        brandId = brandService.createBrand(brand);

        CategorySaveDTO cat = new CategorySaveDTO();
        cat.setName("INV_Cat_" + ts);
        cat.setParentId(0L);
        cat.setStatus(1);
        categoryId = categoryService.createCategory(cat);

        SupplierProductSaveDTO sp = new SupplierProductSaveDTO();
        sp.setSupplierId(supplierId);
        sp.setBrandId(brandId);
        sp.setCategoryId(categoryId);
        sp.setName("INV_Prod_" + ts);
        sp.setProductCode("INVC_" + ts);
        sp.setWholesalePrice(new BigDecimal("100"));
        sp.setRetailPrice(new BigDecimal("200"));
        sp.setMinPurchaseQty(1);
        sp.setStatus(1);
        return supplierProductService.createSupplierProduct(sp);
    }

    private List<InventoryTransaction> manualTxs(long spId) {
        return txMapper.selectList(Wrappers.<InventoryTransaction>lambdaQuery()
                .eq(InventoryTransaction::getSupplierProductId, spId)
                .eq(InventoryTransaction::getType, InventoryConst.TYPE_MANUAL_ADJUST)
                .orderByAsc(InventoryTransaction::getId));
    }

    @Test
    void adjust_updates_qty_and_writes_ledger_with_before_after() {
        long spId = seedSupplierProduct();

        stockService.adjust(spId, 10, "初次盘点");
        assertThat(stockService.getQty(spId)).isEqualTo(10);

        stockService.adjust(spId, 4, "复盘");
        assertThat(stockService.getQty(spId)).isEqualTo(4);

        List<InventoryTransaction> txs = manualTxs(spId);
        assertThat(txs).hasSize(2);
        assertThat(txs.get(0).getBeforeQty()).isEqualTo(0);
        assertThat(txs.get(0).getAfterQty()).isEqualTo(10);
        assertThat(txs.get(1).getBeforeQty()).isEqualTo(10);
        assertThat(txs.get(1).getAfterQty()).isEqualTo(4);
        assertThat(txs.get(1).getQtyChange()).isEqualTo(-6);
        assertThat(txs.get(1).getRefType()).isEqualTo(InventoryConst.REF_MANUAL);
    }

    @Test
    void adjust_rejects_negative_quantity() {
        long spId = seedSupplierProduct();
        assertThatThrownBy(() -> stockService.adjust(spId, -1, "x"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void page_stocks_filters_by_supplier_and_carries_names() {
        long spId = seedSupplierProduct();
        stockService.adjust(spId, 7, "建账");

        PageResult<InventoryStockVO> page = stockService.pageStocks(supplierId, null, null, null, 1, 10);
        assertThat(page.getRecords()).hasSize(1);
        InventoryStockVO vo = page.getRecords().get(0);
        assertThat(vo.getSupplierProductId()).isEqualTo(spId);
        assertThat(vo.getQuantity()).isEqualTo(7);
        assertThat(vo.getSupplierName()).isEqualTo(supplierName);
        assertThat(vo.getBrandName()).startsWith("INV_Brand_");
        assertThat(vo.getCategoryName()).startsWith("INV_Cat_");
        assertThat(vo.getProductName()).startsWith("INV_Prod_");
    }
}
