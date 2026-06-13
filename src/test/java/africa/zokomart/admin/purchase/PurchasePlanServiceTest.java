package africa.zokomart.admin.purchase;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.purchase.constant.PurchaseConst;
import africa.zokomart.admin.module.purchase.dto.PurchasePlanSaveDTO;
import africa.zokomart.admin.module.purchase.service.PurchasePlanService;
import africa.zokomart.admin.module.purchase.vo.PurchasePlanVO;
import africa.zokomart.admin.module.supplierproduct.dto.SupplierProductSaveDTO;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 Task 5.2：采购计划创建/提交。MOQ 校验、快照、金额汇总、状态机。
 */
@SpringBootTest
class PurchasePlanServiceTest {

    @Autowired
    PurchasePlanService planService;
    @Autowired
    SupplierProductService supplierProductService;
    @Autowired
    SupplierService supplierService;

    /** 建一个供应商产品，返回其 id。wholesale=100, MOQ=5。 */
    private long newSupplierProduct(int moq, String wholesale) {
        long ts = System.nanoTime();
        SupplierSaveDTO sup = new SupplierSaveDTO();
        sup.setName("PP_Sup_" + ts);
        sup.setStatus(1);
        Long supplierId = supplierService.createSupplier(sup);

        SupplierProductSaveDTO sp = new SupplierProductSaveDTO();
        sp.setSupplierId(supplierId);
        sp.setName("PP_Prod_" + ts);
        sp.setProductCode("PPC_" + ts);
        sp.setWholesalePrice(new BigDecimal(wholesale));
        sp.setRetailPrice(new BigDecimal("200"));
        sp.setMinPurchaseQty(moq);
        sp.setStatus(1);
        return supplierProductService.createSupplierProduct(sp);
    }

    private PurchasePlanSaveDTO planWith(long supplierProductId, int qty) {
        PurchasePlanSaveDTO dto = new PurchasePlanSaveDTO();
        PurchasePlanSaveDTO.Item item = new PurchasePlanSaveDTO.Item();
        item.setSupplierProductId(supplierProductId);
        item.setPurchaseQty(qty);
        dto.setItems(List.of(item));
        return dto;
    }

    @Test
    void create_rejects_qty_below_moq() {
        long spId = newSupplierProduct(5, "100");
        assertThatThrownBy(() -> planService.create(planWith(spId, 3)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.BELOW_MIN_PURCHASE_QTY.getCode());
    }

    @Test
    void create_persists_snapshot_total_and_draft_status() {
        long spId = newSupplierProduct(5, "100");
        Long id = planService.create(planWith(spId, 5));

        PurchasePlanVO p = planService.getDetail(id);
        assertThat(p.getStatus()).isEqualTo(PurchaseConst.PLAN_DRAFT);
        assertThat(p.getTotalQty()).isEqualTo(5);
        assertThat(p.getTotalAmount()).isEqualByComparingTo("500.00");
        assertThat(p.getItems()).hasSize(1);
        assertThat(p.getItems().get(0).getWholesalePrice()).isEqualByComparingTo("100");
        assertThat(p.getItems().get(0).getMinPurchaseQty()).isEqualTo(5);
        assertThat(p.getPlanNo()).startsWith(PurchaseConst.NO_PLAN);
    }

    @Test
    void zero_qty_lines_skipped_but_require_at_least_one() {
        long spId = newSupplierProduct(1, "10");
        // 仅一条 qty=0 -> 无有效明细，应拒绝
        assertThatThrownBy(() -> planService.create(planWith(spId, 0)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void submit_moves_draft_to_pending() {
        long spId = newSupplierProduct(1, "10");
        Long id = planService.create(planWith(spId, 2));
        planService.submit(id);
        assertThat(planService.getDetail(id).getStatus()).isEqualTo(PurchaseConst.PLAN_PENDING);
    }

    @Test
    void submit_rejects_non_draft() {
        long spId = newSupplierProduct(1, "10");
        Long id = planService.create(planWith(spId, 2));
        planService.submit(id);
        // 已是 PENDING，再次提交应抛非法流转
        assertThatThrownBy(() -> planService.submit(id))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.INVALID_STATUS_TRANSITION.getCode());
    }
}
