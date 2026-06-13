package africa.zokomart.admin.purchase;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.purchase.constant.PurchaseConst;
import africa.zokomart.admin.module.purchase.dto.PurchasePlanSaveDTO;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrder;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrderItem;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderItemMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderMapper;
import africa.zokomart.admin.module.purchase.service.PurchaseApproveService;
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
 * Phase 5 Task 5.3：审批生单。通过→按供应商拆分生成采购订单 + 计划 APPROVED；退回→REJECTED+原因。
 */
@SpringBootTest
class PurchaseApproveServiceTest {

    @Autowired
    PurchaseApproveService approveService;
    @Autowired
    PurchasePlanService planService;
    @Autowired
    SupplierProductService supplierProductService;
    @Autowired
    SupplierService supplierService;
    @Autowired
    PurchaseOrderMapper orderMapper;
    @Autowired
    PurchaseOrderItemMapper orderItemMapper;

    private long newSupplierProduct(long supplierId, String wholesale) {
        long ts = System.nanoTime();
        SupplierProductSaveDTO sp = new SupplierProductSaveDTO();
        sp.setSupplierId(supplierId);
        sp.setName("AP_Prod_" + ts);
        sp.setProductCode("APC_" + ts);
        sp.setWholesalePrice(new BigDecimal(wholesale));
        sp.setRetailPrice(new BigDecimal("200"));
        sp.setMinPurchaseQty(1);
        sp.setStatus(1);
        return supplierProductService.createSupplierProduct(sp);
    }

    private long newSupplier() {
        SupplierSaveDTO sup = new SupplierSaveDTO();
        sup.setName("AP_Sup_" + System.nanoTime());
        sup.setStatus(1);
        return supplierService.createSupplier(sup);
    }

    /** 计划含 供应商A 2 条、供应商B 1 条，提交后返回 planId。 */
    private long pendingPlanTwoSuppliers() {
        long supA = newSupplier();
        long supB = newSupplier();
        long a1 = newSupplierProduct(supA, "100");
        long a2 = newSupplierProduct(supA, "50");
        long b1 = newSupplierProduct(supB, "10");

        PurchasePlanSaveDTO dto = new PurchasePlanSaveDTO();
        dto.setItems(List.of(item(a1, 2), item(a2, 3), item(b1, 5)));
        Long planId = planService.create(dto);
        planService.submit(planId);
        return planId;
    }

    private PurchasePlanSaveDTO.Item item(long spId, int qty) {
        PurchasePlanSaveDTO.Item i = new PurchasePlanSaveDTO.Item();
        i.setSupplierProductId(spId);
        i.setPurchaseQty(qty);
        return i;
    }

    @Test
    void approve_generates_one_order_per_supplier() {
        long planId = pendingPlanTwoSuppliers();
        approveService.approve(planId, 1L);

        List<PurchaseOrder> orders = orderMapper.selectList(
                Wrappers.<PurchaseOrder>lambdaQuery().eq(PurchaseOrder::getPlanId, planId));
        assertThat(orders).hasSize(2); // 按供应商拆分
        assertThat(orders).allMatch(o -> PurchaseConst.ORDER_PENDING_PAYMENT.equals(o.getStatus()));
        assertThat(planService.getDetail(planId).getStatus()).isEqualTo(PurchaseConst.PLAN_APPROVED);

        // 供应商A 的订单含 2 条明细，金额 100*2 + 50*3 = 350
        PurchaseOrder big = orders.stream().max(java.util.Comparator.comparing(PurchaseOrder::getTotalAmount)).orElseThrow();
        List<PurchaseOrderItem> bigItems = orderItemMapper.selectList(
                Wrappers.<PurchaseOrderItem>lambdaQuery().eq(PurchaseOrderItem::getOrderId, big.getId()));
        assertThat(bigItems).hasSize(2);
        assertThat(big.getTotalAmount()).isEqualByComparingTo("350");
        assertThat(bigItems).allMatch(it -> PurchaseConst.PAY_UNSET.equals(it.getPaymentStatus()));
        assertThat(big.getOrderNo()).startsWith(PurchaseConst.NO_ORDER);
    }

    @Test
    void reject_sets_status_and_reason() {
        long planId = pendingPlanTwoSuppliers();
        approveService.reject(planId, 1L, "价格过高");
        var p = planService.getDetail(planId);
        assertThat(p.getStatus()).isEqualTo(PurchaseConst.PLAN_REJECTED);
        assertThat(p.getApproveRemark()).isEqualTo("价格过高");
    }

    @Test
    void approve_rejects_non_pending() {
        long planId = pendingPlanTwoSuppliers();
        approveService.approve(planId, 1L); // 已 APPROVED
        assertThatThrownBy(() -> approveService.approve(planId, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.INVALID_STATUS_TRANSITION.getCode());
    }
}
