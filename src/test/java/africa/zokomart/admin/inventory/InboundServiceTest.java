package africa.zokomart.admin.inventory;

import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.entity.InventoryTransaction;
import africa.zokomart.admin.module.inventory.mapper.InventoryTransactionMapper;
import africa.zokomart.admin.module.inventory.service.InboundService;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.purchase.constant.PurchaseConst;
import africa.zokomart.admin.module.purchase.dto.PurchasePlanSaveDTO;
import africa.zokomart.admin.module.purchase.entity.ActualPurchaseOrder;
import africa.zokomart.admin.module.purchase.entity.ActualPurchaseOrderItem;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrder;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrderItem;
import africa.zokomart.admin.module.purchase.mapper.ActualPurchaseOrderItemMapper;
import africa.zokomart.admin.module.purchase.mapper.ActualPurchaseOrderMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderItemMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderMapper;
import africa.zokomart.admin.module.purchase.service.PurchaseApproveService;
import africa.zokomart.admin.module.purchase.service.PurchaseOrderPaymentService;
import africa.zokomart.admin.module.purchase.service.PurchasePlanService;
import africa.zokomart.admin.module.supplierproduct.dto.SupplierProductSaveDTO;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 Task 5.5：入库增加库存 + 写流水；重复入库幂等（已 DONE 跳过，库存不翻倍）。
 */
@SpringBootTest
class InboundServiceTest {

    @Autowired
    InboundService inboundService;
    @Autowired
    InventoryStockService stockService;
    @Autowired
    InventoryTransactionMapper txMapper;
    @Autowired
    PurchasePlanService planService;
    @Autowired
    PurchaseApproveService approveService;
    @Autowired
    PurchaseOrderPaymentService paymentService;
    @Autowired
    SupplierProductService supplierProductService;
    @Autowired
    SupplierService supplierService;
    @Autowired
    PurchaseOrderMapper orderMapper;
    @Autowired
    PurchaseOrderItemMapper orderItemMapper;
    @Autowired
    ActualPurchaseOrderMapper actualMapper;
    @Autowired
    ActualPurchaseOrderItemMapper actualItemMapper;

    private long supplierProductId;

    /** 跑通采购链直到生成 PENDING_INBOUND 的实际采购单（含 1 条 qty=5 的明细），返回实际采购单 id。 */
    private long actualOrderReadyToInbound() {
        long ts = System.nanoTime();
        SupplierSaveDTO sup = new SupplierSaveDTO();
        sup.setName("IN_Sup_" + ts);
        sup.setStatus(1);
        long supplierId = supplierService.createSupplier(sup);

        SupplierProductSaveDTO sp = new SupplierProductSaveDTO();
        sp.setSupplierId(supplierId);
        sp.setName("IN_Prod_" + ts);
        sp.setProductCode("INC_" + ts);
        sp.setWholesalePrice(new BigDecimal("100"));
        sp.setRetailPrice(new BigDecimal("200"));
        sp.setMinPurchaseQty(1);
        sp.setStatus(1);
        supplierProductId = supplierProductService.createSupplierProduct(sp);

        PurchasePlanSaveDTO dto = new PurchasePlanSaveDTO();
        PurchasePlanSaveDTO.Item i = new PurchasePlanSaveDTO.Item();
        i.setSupplierProductId(supplierProductId);
        i.setPurchaseQty(5);
        dto.setItems(List.of(i));
        Long planId = planService.create(dto);
        planService.submit(planId);
        approveService.approve(planId, 1L);

        PurchaseOrder order = orderMapper.selectOne(
                Wrappers.<PurchaseOrder>lambdaQuery().eq(PurchaseOrder::getPlanId, planId));
        PurchaseOrderItem item = orderItemMapper.selectOne(
                Wrappers.<PurchaseOrderItem>lambdaQuery().eq(PurchaseOrderItem::getOrderId, order.getId()));
        paymentService.mark(order.getId(), List.of(item.getId()), PurchaseConst.PAY_PAID);
        return paymentService.confirm(order.getId());
    }

    private long actualItemId(long actualOrderId) {
        return actualItemMapper.selectOne(Wrappers.<ActualPurchaseOrderItem>lambdaQuery()
                .eq(ActualPurchaseOrderItem::getActualOrderId, actualOrderId)).getId();
    }

    @Test
    void inbound_increments_stock_and_writes_transaction() {
        long actualId = actualOrderReadyToInbound();
        long itemId = actualItemId(actualId);

        inboundService.inbound(actualId, List.of(itemId));

        assertThat(stockService.getQty(supplierProductId)).isEqualTo(5);
        List<InventoryTransaction> txs = txMapper.selectList(Wrappers.<InventoryTransaction>lambdaQuery()
                .eq(InventoryTransaction::getSupplierProductId, supplierProductId));
        assertThat(txs).anySatisfy(t -> {
            assertThat(t.getType()).isEqualTo(InventoryConst.TYPE_PURCHASE_IN);
            assertThat(t.getQtyChange()).isEqualTo(5);
            assertThat(t.getAfterQty()).isEqualTo(5);
        });

        // 整单入库后实际采购单 INBOUND_DONE
        ActualPurchaseOrder actual = actualMapper.selectById(actualId);
        assertThat(actual.getStatus()).isEqualTo(PurchaseConst.ACTUAL_INBOUND_DONE);
        ActualPurchaseOrderItem it = actualItemMapper.selectById(itemId);
        assertThat(it.getInboundStatus()).isEqualTo(PurchaseConst.INBOUND_DONE);
    }

    @Test
    void inbound_is_idempotent_for_done_items() {
        long actualId = actualOrderReadyToInbound();
        long itemId = actualItemId(actualId);

        inboundService.inbound(actualId, List.of(itemId));
        inboundService.inbound(actualId, List.of(itemId)); // 再次入库

        assertThat(stockService.getQty(supplierProductId)).isEqualTo(5); // 不翻倍
    }
}
