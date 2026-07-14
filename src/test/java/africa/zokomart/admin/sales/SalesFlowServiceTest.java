package africa.zokomart.admin.sales;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.sales.constant.SalesConst;
import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.entity.SalesOrderItem;
import africa.zokomart.admin.module.sales.mapper.SalesOrderItemMapper;
import africa.zokomart.admin.module.sales.service.SalesLogisticsService;
import africa.zokomart.admin.module.sales.service.SalesOrderService;
import africa.zokomart.admin.module.sales.vo.SalesOrderVO;
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
 * Phase 7 Task 7.2/7.4/7.5：下单扣库存防超卖、派送状态机、拒收回补、完成结算。
 */
@SpringBootTest
class SalesFlowServiceTest {

    @Autowired
    SalesOrderService salesService;
    @Autowired
    SalesLogisticsService logisticsService;
    @Autowired
    InventoryStockService stockService;
    @Autowired
    SupplierProductService supplierProductService;
    @Autowired
    SupplierService supplierService;
    @Autowired
    SalesOrderItemMapper itemMapper;

    /** 建供应商产品并把库存调到 stock，retail 为零售价。返回 spId。 */
    private long productWithStock(int stock, String retail) {
        long ts = System.nanoTime();
        SupplierSaveDTO sup = new SupplierSaveDTO();
        sup.setName("SALE_Sup_" + ts);
        sup.setStatus(1);
        long supplierId = supplierService.createSupplier(sup);

        SupplierProductSaveDTO sp = new SupplierProductSaveDTO();
        sp.setSupplierId(supplierId);
        sp.setName("SALE_Prod_" + ts);
        sp.setProductCode("SALEC_" + ts);
        sp.setWholesalePrice(new BigDecimal("100"));
        sp.setRetailPrice(new BigDecimal(retail));
        sp.setMinPurchaseQty(1);
        sp.setStatus(1);
        long spId = supplierProductService.createSupplierProduct(sp);
        stockService.adjust(spId, stock, "建账");
        return spId;
    }

    private SalesOrderCreateDTO orderDto(long spId, int qty) {
        SalesOrderCreateDTO dto = new SalesOrderCreateDTO();
        dto.setCustomerName("Kofi");
        dto.setCustomerPhone("024000000");
        dto.setCustomerAddress("Accra");
        SalesOrderCreateDTO.Item it = new SalesOrderCreateDTO.Item();
        it.setSupplierProductId(spId);
        it.setQty(qty);
        dto.setItems(List.of(it));
        return dto;
    }

    private long firstItemId(long orderId) {
        return itemMapper.selectOne(Wrappers.<SalesOrderItem>lambdaQuery()
                .eq(SalesOrderItem::getOrderId, orderId)).getId();
    }

    @Test
    void create_deducts_stock_and_sets_pending_status_and_total() {
        long spId = productWithStock(10, "200");
        Long id = salesService.create(orderDto(spId, 3));

        assertThat(stockService.getQty(spId)).isEqualTo(7);
        SalesOrderVO o = salesService.getDetail(id);
        assertThat(o.getStatus()).isEqualTo(SalesConst.PENDING_DISPATCH);
        assertThat(o.getCompleted()).isEqualTo(0);
        assertThat(o.getTotalAmount()).isEqualByComparingTo("600.00");
        assertThat(o.getItems().get(0).getUnitPrice()).isEqualByComparingTo("200"); // 默认带出零售价
        assertThat(o.getOrderNo()).startsWith(SalesConst.NO_SALES);
    }

    @Test
    void create_rejects_when_stock_insufficient() {
        long spId = productWithStock(10, "200");
        assertThatThrownBy(() -> salesService.create(orderDto(spId, 999)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.INSUFFICIENT_STOCK.getCode());
    }

    @Test
    void dispatch_moves_pending_to_dispatching() {
        long spId = productWithStock(10, "200");
        Long id = salesService.create(orderDto(spId, 1));
        logisticsService.dispatch(id, 1L, new BigDecimal("15"));
        SalesOrderVO o = salesService.getDetail(id);
        assertThat(o.getStatus()).isEqualTo(SalesConst.DISPATCHING);
        assertThat(o.getDispatchTime()).isNotNull();
    }

    @Test
    void status_rejects_skipping_dispatch() {
        long spId = productWithStock(10, "200");
        Long id = salesService.create(orderDto(spId, 1));
        // PENDING_DISPATCH 直接置 SIGNED 非法
        assertThatThrownBy(() -> logisticsService.updateStatus(id, SalesConst.SIGNED, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.INVALID_STATUS_TRANSITION.getCode());
    }

    @Test
    void reject_only_allowed_in_signed_states() {
        long spId = productWithStock(10, "200");
        Long id = salesService.create(orderDto(spId, 3));
        logisticsService.dispatch(id, 1L, new BigDecimal("15")); // DISPATCHING
        long itemId = firstItemId(id);
        assertThatThrownBy(() -> logisticsService.markReject(id, itemId, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reject_returns_stock_and_completion_excludes_rejected() {
        long spId = productWithStock(10, "200");
        Long id = salesService.create(orderDto(spId, 3)); // stock 7
        long itemId = firstItemId(id);
        logisticsService.dispatch(id, 1L, new BigDecimal("15"));
        logisticsService.updateStatus(id, SalesConst.SIGNED, null);

        logisticsService.markReject(id, itemId, 1); // 回补 1
        assertThat(stockService.getQty(spId)).isEqualTo(8);

        logisticsService.complete(id);
        SalesOrderVO o = salesService.getDetail(id);
        assertThat(o.getActualAmount()).isEqualByComparingTo("400.00"); // 200*(3-1)
        assertThat(o.getCompleted()).isEqualTo(1);
        assertThat(o.getItems().get(0).getRejectQty()).isEqualTo(1);
    }

    @Test
    void rejected_status_auto_completes_zero_actual_and_restocks() {
        long spId = productWithStock(10, "200");
        Long id = salesService.create(orderDto(spId, 3)); // stock 7
        logisticsService.dispatch(id, 1L, new BigDecimal("15"));
        logisticsService.updateStatus(id, SalesConst.REJECTED, null);

        assertThat(stockService.getQty(spId)).isEqualTo(10); // 全部回补
        SalesOrderVO o = salesService.getDetail(id);
        assertThat(o.getStatus()).isEqualTo(SalesConst.REJECTED);
        assertThat(o.getCompleted()).isEqualTo(1);
        assertThat(o.getActualAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void completed_order_is_read_only() {
        long spId = productWithStock(10, "200");
        Long id = salesService.create(orderDto(spId, 2));
        logisticsService.dispatch(id, 1L, new BigDecimal("15"));
        logisticsService.updateStatus(id, SalesConst.SIGNED_PAID, null);
        logisticsService.complete(id);

        assertThatThrownBy(() -> logisticsService.complete(id))
                .isInstanceOf(BusinessException.class);
    }
}
