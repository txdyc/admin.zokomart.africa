package africa.zokomart.admin.sales;

import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.entity.InventoryStock;
import africa.zokomart.admin.module.inventory.entity.InventoryTransaction;
import africa.zokomart.admin.module.inventory.mapper.InventoryStockMapper;
import africa.zokomart.admin.module.inventory.mapper.InventoryTransactionMapper;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.entity.SalesOrderItem;
import africa.zokomart.admin.module.sales.mapper.SalesOrderItemMapper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.sales.service.SalesOrderImportService;
import africa.zokomart.admin.module.sales.vo.SalesOrderImportResultVO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 导入服务集成测试。跑的是真实 dev 库，因此：
 *  - 用带时间戳的唯一电话号标识本次测试数据
 *  - @AfterEach 物理清理订单与明细、删除本次产生的库存流水、并把库存数量复原到测试前快照
 *    （import 走真实 create()，会真实扣减 inventory_stock 并写 inventory_transaction，
 *    不清理会像 33e100f 修复前那样永久污染 dev 库）
 */
@SpringBootTest
class SalesOrderImportServiceTest {

    static final String[] HEADER = {
            "Order ID", "Sale Price", "Customer Name", "City", "Shipping Address",
            "Phone Number", "Product Name", "Product Code", "Quantity", "Status", "Order Date"};
    /** 46280 = 2026-09-15。 */
    static final int SERIAL_D1 = 46280;
    static final int SERIAL_D2 = 46281;

    @Autowired
    SalesOrderImportService importService;
    @Autowired
    SalesOrderMapper orderMapper;
    @Autowired
    SalesOrderItemMapper itemMapper;
    @Autowired
    SupplierProductMapper supplierProductMapper;
    @Autowired
    InventoryStockMapper stockMapper;
    @Autowired
    InventoryTransactionMapper txMapper;
    @Autowired
    JdbcTemplate jdbc;

    final List<String> usedPhones = new ArrayList<>();
    /** supplierProductId -> quantity 测试开始前的快照（null = 当时无库存记录）；cleanup 按快照绝对值复原。 */
    final Map<Long, Integer> stockSnapshots = new HashMap<>();
    /** 测试内临时建的 supplier_product 夹具（如歧义编码用例），cleanup 时物理删除。 */
    final List<Long> fixtureProductIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String phone : usedPhones) {
            List<SalesOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<SalesOrder>().eq(SalesOrder::getCustomerPhone, phone));
            for (SalesOrder o : orders) {
                txMapper.delete(new LambdaQueryWrapper<InventoryTransaction>()
                        .eq(InventoryTransaction::getRefId, o.getId())
                        .eq(InventoryTransaction::getRefType, InventoryConst.REF_SALES_ORDER));
                itemMapper.delete(new LambdaQueryWrapper<SalesOrderItem>()
                        .eq(SalesOrderItem::getOrderId, o.getId()));
                orderMapper.deleteById(o.getId());
            }
        }
        usedPhones.clear();

        for (Map.Entry<Long, Integer> e : stockSnapshots.entrySet()) {
            if (e.getValue() == null) {
                // 测试前该产品没有库存记录：changeStock 会新建一行，这里必须物理删除恢复"无库存记录"状态。
                // 不能用 stockMapper.delete()：InventoryStock 继承 BaseEntity 的 @TableLogic 逻辑删除，
                // mapper 层 delete 只会置 deleted=1，物理行仍占着 (supplier_product_id) 唯一键，
                // 下次测试对同一产品建库存会撞唯一键冲突，MAX_RETRY 耗尽后 changeStock 报「库存更新冲突」——
                // 这就是本文件最初实现踩过的坑，用原生 JDBC 绕开逻辑删除拦截器做真正的物理删除。
                jdbc.update("DELETE FROM inventory_stock WHERE supplier_product_id = ?", e.getKey());
            } else {
                stockMapper.update(null, new LambdaUpdateWrapper<InventoryStock>()
                        .eq(InventoryStock::getSupplierProductId, e.getKey())
                        .set(InventoryStock::getQuantity, e.getValue()));
            }
        }
        stockSnapshots.clear();

        // supplier_product 也继承 BaseEntity 的 @TableLogic：mapper 层 delete 只会置 deleted=1，
        // 物理行仍占着 (supplier_id, product_code) 唯一键。夹具产品编码是本测试临时造的，
        // 必须物理删除，否则下次跑测试用同样前缀生成的编码理论上还能再撞（虽然带纳秒后缀概率极低，
        // 但留着一条没有业务意义的幽灵行本身就是需要避免的污染）。
        for (Long id : fixtureProductIds) {
            jdbc.update("DELETE FROM supplier_product WHERE id = ?", id);
        }
        fixtureProductIds.clear();
    }

    /**
     * 生成唯一电话。必须**恰好 10 位**（0 + 9 位）：normalizePhone 只在长度为 10 时
     * 剥前导 0，位数不对会让 international_phone_formats_merge_into_one_order 偶发失败。
     */
    private String uniquePhone(String prefix) {
        String p = prefix + String.format("%06d", System.nanoTime() % 1000000L);
        assertEquals(10, p.length(), "测试电话必须是 10 位");
        usedPhones.add(p);
        return p;
    }

    private SupplierProduct anyProduct(int skip) {
        List<SupplierProduct> all = supplierProductMapper.selectList(
                new LambdaQueryWrapper<SupplierProduct>().last("limit " + (skip + 1)));
        assertTrue(all.size() > skip, "dev 库需要至少 " + (skip + 1) + " 个 supplier_product");
        SupplierProduct p = all.get(skip);
        stockSnapshots.putIfAbsent(p.getId(), snapshotQty(p.getId()));
        return p;
    }

    private Integer snapshotQty(Long supplierProductId) {
        InventoryStock s = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSupplierProductId, supplierProductId));
        return s == null ? null : s.getQuantity();
    }

    /** 建一个 supplier_product 夹具，登记进 fixtureProductIds 供 cleanup 物理删除。 */
    private void fixtureProduct(Long supplierId, String productCode) {
        SupplierProduct sp = new SupplierProduct();
        sp.setSupplierId(supplierId);
        sp.setName("Import Test Fixture");
        sp.setProductCode(productCode);
        supplierProductMapper.insert(sp);
        fixtureProductIds.add(sp.getId());
    }

    private MockMultipartFile xlsx(Object[]... rows) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Workbook wb = new Workbook(os, "test", "1.0")) {
            Worksheet ws = wb.newWorksheet("Sheet1");
            for (int c = 0; c < HEADER.length; c++) {
                ws.value(0, c, HEADER[c]);
            }
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v == null) continue;
                    if (v instanceof Number n) ws.value(r + 1, c, n);
                    else ws.value(r + 1, c, String.valueOf(v));
                }
            }
        }
        return new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", os.toByteArray());
    }

    private Object[] row(String oid, Number price, String name, String phone,
                         String addr, String code, Number qty, Number dateSerial) {
        return new Object[]{oid, price, name, "Accra", addr, phone, "Any", code, qty, "未发货", dateSerial};
    }

    @Test
    void groups_rows_by_customer_and_sums_amount() throws Exception {
        SupplierProduct p1 = anyProduct(0);
        SupplierProduct p2 = anyProduct(1);
        String phone = uniquePhone("0551");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("A1", 320, "Baaba Maison", phone, "Pantang junction", p1.getProductCode(), 1, SERIAL_D1),
                row("A2", 430, "Baaba Maison", phone, "Pantang junction", p2.getProductCode(), 1, SERIAL_D1)));

        assertEquals(2, res.getTotalRows());
        assertEquals(1, res.getOrderCount(), "同客户同日两行归为一单");
        assertEquals(1, res.getSuccess());
        assertEquals(0, res.getFailed());

        SalesOrder o = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).get(0);
        assertEquals(0, new BigDecimal("750.00").compareTo(o.getTotalAmount()));
        assertEquals(2, o.getTotalQty());
        assertEquals("PENDING_DISPATCH", o.getStatus(), "Status 列被忽略，一律待派送");
        assertEquals("Accra", o.getCity());
        assertEquals(LocalDate.of(2026, 9, 15), o.getOrderDate());
        assertEquals(phone, o.getCustomerPhone(), "入库存 Excel 原值");
    }

    @Test
    void same_customer_across_days_becomes_two_orders() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0552");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("B1", 320, "Cross Day", phone, "addr", p.getProductCode(), 1, SERIAL_D1),
                row("B2", 430, "Cross Day", phone, "addr", p.getProductCode(), 1, SERIAL_D2)));

        assertEquals(2, res.getOrderCount(), "跨天必须拆成两单，不是报错");
        assertEquals(2, res.getSuccess());
        assertEquals(0, res.getFailed());

        List<SalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone));
        assertEquals(2, orders.size());
        assertTrue(orders.stream().anyMatch(o -> o.getOrderDate().equals(LocalDate.of(2026, 9, 15))));
        assertTrue(orders.stream().anyMatch(o -> o.getOrderDate().equals(LocalDate.of(2026, 9, 16))));
    }

    @Test
    void international_phone_formats_merge_into_one_order() throws Exception {
        SupplierProduct p = anyProduct(0);
        String local = uniquePhone("0244");
        String intl = "233" + local.substring(1);

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("C1", 100, "Intl Phone", local, "addr", p.getProductCode(), 1, SERIAL_D1),
                row("C2", 200, "Intl Phone", intl, "addr", p.getProductCode(), 1, SERIAL_D1)));

        assertEquals(1, res.getOrderCount(), "0xxx 与 233xxx 应归一为同一客户");
        assertEquals(1, res.getSuccess());
    }

    @Test
    void unknown_product_code_fails_that_order_only() throws Exception {
        SupplierProduct p = anyProduct(0);
        String goodPhone = uniquePhone("0553");
        String badPhone = uniquePhone("0554");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("D1", 100, "Good One", goodPhone, "addr", p.getProductCode(), 1, SERIAL_D1),
                row("D2", 100, "Bad One", badPhone, "addr", "NO-SUCH-CODE-ZZZ", 1, SERIAL_D1)));

        assertEquals(2, res.getOrderCount());
        assertEquals(1, res.getSuccess(), "好单照常导入");
        assertEquals(1, res.getFailed());
        assertEquals(1, res.getErrors().size());
        assertEquals("NO-SUCH-CODE-ZZZ", res.getErrors().get(0).getProductCode());
        assertTrue(res.getErrors().get(0).getReason().contains("不存在"));
        assertEquals("3", res.getErrors().get(0).getRows(), "错误应定位到源文件第 3 行");

        assertTrue(orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, badPhone)).isEmpty(), "失败单不得留残缺数据");
    }

    @Test
    void ambiguous_product_code_across_suppliers_fails_that_order_only() throws Exception {
        List<Long> supplierIds = jdbc.queryForList(
                "SELECT id FROM supplier WHERE deleted = 0 ORDER BY id LIMIT 2", Long.class);
        assertTrue(supplierIds.size() >= 2, "dev 库需要至少 2 个 supplier 才能跑本测试");
        // product_code 只在供应商内唯一（(supplier_id, product_code) 联合唯一），
        // 跨供应商可能重名——纳秒后缀确保这个测试编码不会撞真实数据。
        String code = "IMPTEST-" + System.nanoTime();
        fixtureProduct(supplierIds.get(0), code);
        fixtureProduct(supplierIds.get(1), code);
        String phone = uniquePhone("0560");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("K1", 100, "Ambiguous Code", phone, "addr", code, 1, SERIAL_D1)));

        assertEquals(1, res.getOrderCount());
        assertEquals(0, res.getSuccess());
        assertEquals(1, res.getFailed(), "编码歧义（>1 个供应商产品匹配）应让整单失败");
        assertEquals(1, res.getErrors().size());
        String reason = res.getErrors().get(0).getReason();
        assertTrue(reason.contains(code), "原因应指出具体的歧义编码");
        assertTrue(reason.contains("2"), "原因应指出匹配到的数量");

        assertTrue(orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).isEmpty(), "歧义单不得留残缺数据");
    }

    @Test
    void bad_row_fails_whole_order_even_if_other_rows_are_fine() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0555");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("E1", 100, "Partial", phone, "addr", p.getProductCode(), 1, SERIAL_D1),
                row("E2", 100, "Partial", phone, "addr", p.getProductCode(), 0, SERIAL_D1)));

        assertEquals(1, res.getOrderCount());
        assertEquals(0, res.getSuccess());
        assertEquals(1, res.getFailed(), "同组内一行非法 → 整单失败，不生成残缺订单");
        assertTrue(orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).isEmpty());
    }

    @Test
    void missing_order_date_fails_that_order() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0556");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("F1", 100, "No Date", phone, "addr", p.getProductCode(), 1, null)));

        assertEquals(0, res.getSuccess());
        assertEquals(1, res.getFailed());
        assertTrue(res.getErrors().get(0).getReason().contains("Order Date"));
    }

    @Test
    void reimport_of_same_file_is_skipped() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0557");
        Object[] r = row("G1", 100, "Dup Guard", phone, "addr", p.getProductCode(), 1, SERIAL_D1);

        assertEquals(1, importService.importExcel(xlsx(r)).getSuccess());

        SalesOrderImportResultVO second = importService.importExcel(xlsx(r));
        assertEquals(0, second.getSuccess());
        assertEquals(1, second.getSkipped(), "同客户同日期已存在 → 跳过");
        assertEquals(0, second.getFailed());

        assertEquals(1, orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).size(), "不得重复建单");
    }

    @Test
    void unit_price_rounds_but_amount_keeps_excel_value() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0558");

        importService.importExcel(xlsx(
                row("H1", 700, "Rounding", phone, "addr", p.getProductCode(), 3, SERIAL_D1)));

        SalesOrder o = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).get(0);
        SalesOrderItem item = itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItem>()
                .eq(SalesOrderItem::getOrderId, o.getId())).get(0);

        assertEquals(0, new BigDecimal("233.33").compareTo(item.getUnitPrice()), "700/3 四舍五入到 2 位");
        assertEquals(0, new BigDecimal("700.00").compareTo(item.getAmount()), "行金额保留 Excel 原值");
        assertEquals(0, new BigDecimal("700.00").compareTo(o.getTotalAmount()), "总额精确等于 Excel 之和");
        assertEquals("H1", item.getExternalOrderId());
    }

    @Test
    void deducts_stock_like_manual_order() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0559");
        int before = currentStock(p.getId());

        importService.importExcel(xlsx(
                row("I1", 100, "Stock Check", phone, "addr", p.getProductCode(), 2, SERIAL_D1)));

        assertEquals(before - 2, currentStock(p.getId()), "导入应与手工下单一样扣库存");
    }

    private int currentStock(Long supplierProductId) {
        InventoryStock s = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSupplierProductId, supplierProductId));
        return s == null ? 0 : s.getQuantity();
    }
}
