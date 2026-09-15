package africa.zokomart.admin.sales;

import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.entity.SalesOrderItem;
import africa.zokomart.admin.module.sales.mapper.SalesOrderItemMapper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.sales.service.SalesOrderService;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** create() 的新字段语义：city / orderDate / amount 覆盖 / externalOrderId / create_time 归到业务日期。 */
@SpringBootTest
class SalesOrderCreateFieldsTest {

    @Autowired
    SalesOrderService salesOrderService;
    @Autowired
    SalesOrderMapper orderMapper;
    @Autowired
    SalesOrderItemMapper itemMapper;
    @Autowired
    SupplierProductMapper supplierProductMapper;

    /** 取库里任意一个可用的供应商产品，避免测试依赖特定种子数据。 */
    private Long anySupplierProductId() {
        List<SupplierProduct> all = supplierProductMapper.selectList(
                new LambdaQueryWrapper<SupplierProduct>().last("limit 1"));
        assertFalse(all.isEmpty(), "dev 库需要至少一个 supplier_product 才能跑本测试");
        return all.get(0).getId();
    }

    private SalesOrderCreateDTO dto(String phone) {
        SalesOrderCreateDTO d = new SalesOrderCreateDTO();
        d.setCustomerName("Fields Test");
        d.setCustomerPhone(phone);
        d.setCustomerAddress("test addr");
        SalesOrderCreateDTO.Item it = new SalesOrderCreateDTO.Item();
        it.setSupplierProductId(anySupplierProductId());
        it.setQty(3);
        it.setUnitPrice(new BigDecimal("233.33"));
        d.setItems(List.of(it));
        return d;
    }

    private void cleanup(Long orderId) {
        itemMapper.delete(new LambdaQueryWrapper<SalesOrderItem>().eq(SalesOrderItem::getOrderId, orderId));
        orderMapper.deleteById(orderId);
    }

    @Test
    void manual_create_defaults_order_date_to_today_and_amount_to_price_times_qty() {
        Long id = salesOrderService.create(dto("0550000001"));
        try {
            SalesOrder o = orderMapper.selectById(id);
            assertEquals(LocalDate.now(), o.getOrderDate(), "手工下单 orderDate 默认今天");
            assertNull(o.getCity(), "未传 city 应为 null");
            assertEquals(LocalDate.now(), o.getCreateTime().toLocalDate(), "手工下单 create_time 仍是此刻");

            SalesOrderItem item = itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItem>()
                    .eq(SalesOrderItem::getOrderId, id)).get(0);
            assertEquals(0, new BigDecimal("699.99").compareTo(item.getAmount()),
                    "未传 amount 时仍按 unitPrice * qty");
            assertNull(item.getExternalOrderId());
        } finally {
            cleanup(id);
        }
    }

    @Test
    void import_style_create_honours_city_order_date_amount_and_external_id() {
        LocalDate past = LocalDate.of(2026, 9, 15);
        SalesOrderCreateDTO d = dto("0550000002");
        d.setCity("Accra");
        d.setOrderDate(past);
        d.getItems().get(0).setAmount(new BigDecimal("700.00"));
        d.getItems().get(0).setExternalOrderId("SSK202609151503");

        Long id = salesOrderService.create(d);
        try {
            SalesOrder o = orderMapper.selectById(id);
            assertEquals("Accra", o.getCity());
            assertEquals(past, o.getOrderDate());
            assertEquals(past, o.getCreateTime().toLocalDate(),
                    "orderDate 非空时 create_time 归到业务日期，仪表盘才能按日统计");
            assertNotNull(o.getUpdateTime(), "update_time 仍是真实导入时刻");
            assertEquals(LocalDate.now(), o.getUpdateTime().toLocalDate());
            assertEquals(0, new BigDecimal("700.00").compareTo(o.getTotalAmount()),
                    "订单总额用传入的 amount 原值，不回乘（233.33*3=699.99 会漂移）");

            SalesOrderItem item = itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItem>()
                    .eq(SalesOrderItem::getOrderId, id)).get(0);
            assertEquals(0, new BigDecimal("700.00").compareTo(item.getAmount()));
            assertEquals("SSK202609151503", item.getExternalOrderId());
        } finally {
            cleanup(id);
        }
    }
}
