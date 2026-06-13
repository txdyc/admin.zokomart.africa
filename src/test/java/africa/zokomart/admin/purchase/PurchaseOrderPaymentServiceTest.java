package africa.zokomart.admin.purchase;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.service.SupplierService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 Task 5.4：付款标记 + 生成实际采购单（仅已付款明细入单；至少一条 PAID）。
 */
@SpringBootTest
class PurchaseOrderPaymentServiceTest {

    @Autowired
    PurchaseOrderPaymentService paymentService;
    @Autowired
    PurchasePlanService planService;
    @Autowired
    PurchaseApproveService approveService;
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

    private long supplierProduct(long supplierId, String wholesale) {
        long ts = System.nanoTime();
        SupplierProductSaveDTO sp = new SupplierProductSaveDTO();
        sp.setSupplierId(supplierId);
        sp.setName("PAY_Prod_" + ts);
        sp.setProductCode("PAYC_" + ts);
        sp.setWholesalePrice(new BigDecimal(wholesale));
        sp.setRetailPrice(new BigDecimal("200"));
        sp.setMinPurchaseQty(1);
        sp.setStatus(1);
        return supplierProductService.createSupplierProduct(sp);
    }

    /** 单供应商 2 明细的已审批采购订单，返回该订单。 */
    private PurchaseOrder approvedSingleOrder() {
        SupplierSaveDTO sup = new SupplierSaveDTO();
        sup.setName("PAY_Sup_" + System.nanoTime());
        sup.setStatus(1);
        long supplierId = supplierService.createSupplier(sup);
        long p1 = supplierProduct(supplierId, "100");
        long p2 = supplierProduct(supplierId, "50");

        PurchasePlanSaveDTO dto = new PurchasePlanSaveDTO();
        PurchasePlanSaveDTO.Item i1 = new PurchasePlanSaveDTO.Item();
        i1.setSupplierProductId(p1);
        i1.setPurchaseQty(2); // amount 200
        PurchasePlanSaveDTO.Item i2 = new PurchasePlanSaveDTO.Item();
        i2.setSupplierProductId(p2);
        i2.setPurchaseQty(3); // amount 150
        dto.setItems(List.of(i1, i2));
        Long planId = planService.create(dto);
        planService.submit(planId);
        approveService.approve(planId, 1L);

        return orderMapper.selectOne(Wrappers.<PurchaseOrder>lambdaQuery().eq(PurchaseOrder::getPlanId, planId));
    }

    private List<PurchaseOrderItem> itemsOf(long orderId) {
        return orderItemMapper.selectList(
                Wrappers.<PurchaseOrderItem>lambdaQuery().eq(PurchaseOrderItem::getOrderId, orderId));
    }

    @Test
    void confirm_builds_actual_order_from_paid_items_only() {
        PurchaseOrder order = approvedSingleOrder();
        List<PurchaseOrderItem> its = itemsOf(order.getId());
        long item1 = its.get(0).getId();
        long item2 = its.get(1).getId();

        paymentService.mark(order.getId(), List.of(item1), PurchaseConst.PAY_PAID);
        paymentService.mark(order.getId(), List.of(item2), PurchaseConst.PAY_UNPAID);

        Long actualId = paymentService.confirm(order.getId());

        List<ActualPurchaseOrderItem> actualItems = actualItemMapper.selectList(
                Wrappers.<ActualPurchaseOrderItem>lambdaQuery()
                        .eq(ActualPurchaseOrderItem::getActualOrderId, actualId));
        assertThat(actualItems).extracting(ActualPurchaseOrderItem::getPurchaseOrderItemId)
                .containsExactly(item1);

        PurchaseOrder reloaded = orderMapper.selectById(order.getId());
        assertThat(reloaded.getStatus()).isEqualTo(PurchaseConst.ORDER_CONFIRMED);
        // 已付款金额 = item1 金额
        BigDecimal paidAmount = its.get(0).getAmount();
        assertThat(reloaded.getPaidAmount()).isEqualByComparingTo(paidAmount);

        ActualPurchaseOrder actual = actualMapper.selectById(actualId);
        assertThat(actual.getStatus()).isEqualTo(PurchaseConst.ACTUAL_PENDING_INBOUND);
        assertThat(actual.getActualNo()).startsWith(PurchaseConst.NO_ACTUAL);
        assertThat(actualItems.get(0).getInboundStatus()).isEqualTo(PurchaseConst.INBOUND_PENDING);
    }

    @Test
    void confirm_requires_at_least_one_paid() {
        PurchaseOrder order = approvedSingleOrder();
        assertThatThrownBy(() -> paymentService.confirm(order.getId()))
                .isInstanceOf(BusinessException.class);
    }
}
