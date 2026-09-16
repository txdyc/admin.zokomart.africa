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
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 跑的是真实 dev 库：导入成功会走真实 create()，真实扣减 inventory_stock 并写
 * inventory_transaction。@AfterEach 物理清理订单/明细、删除本次产生的库存流水、
 * 并把库存数量复原到测试前快照，避免像 SalesOrderImportServiceTest 修复前那样
 * 永久污染 dev 库。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderImportApiTest {

    static final String[] HEADER = {
            "Order ID", "Sale Price", "Customer Name", "City", "Shipping Address",
            "Phone Number", "Product Name", "Product Code", "Quantity", "Status", "Order Date"};

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
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

    String phone;
    /** supplierProductId -> quantity 测试开始前的快照（null = 当时无库存记录）；cleanup 按快照绝对值复原。 */
    final Map<Long, Integer> stockSnapshots = new HashMap<>();

    @AfterEach
    void cleanup() {
        if (phone != null) {
            for (SalesOrder o : orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                    .eq(SalesOrder::getCustomerPhone, phone))) {
                txMapper.delete(new LambdaQueryWrapper<InventoryTransaction>()
                        .eq(InventoryTransaction::getRefId, o.getId())
                        .eq(InventoryTransaction::getRefType, InventoryConst.REF_SALES_ORDER));
                // sales_order(_item) 继承 BaseEntity 的 @TableLogic：mapper 层 delete/deleteById
                // 只会置 deleted=1，物理行仍在，每次 mvn test 都会在 dev 库里累积——用原生 JDBC
                // 物理删除，同 stockSnapshots 清理已经在用的手法。
                jdbc.update("DELETE FROM sales_order_item WHERE order_id = ?", o.getId());
                jdbc.update("DELETE FROM sales_order WHERE id = ?", o.getId());
            }
            phone = null;
        }

        for (Map.Entry<Long, Integer> e : stockSnapshots.entrySet()) {
            if (e.getValue() == null) {
                // 测试前该产品没有库存记录：changeStock 会新建一行。InventoryStock 继承
                // BaseEntity 的 @TableLogic，mapper 层 delete 只会置 deleted=1，物理行仍占着
                // (supplier_product_id) 唯一键，必须用原生 JDBC 绕开逻辑删除拦截器做真正的物理删除。
                jdbc.update("DELETE FROM inventory_stock WHERE supplier_product_id = ?", e.getKey());
            } else {
                stockMapper.update(null, new LambdaUpdateWrapper<InventoryStock>()
                        .eq(InventoryStock::getSupplierProductId, e.getKey())
                        .set(InventoryStock::getQuantity, e.getValue()));
            }
        }
        stockSnapshots.clear();
    }

    private String token(String user, String pwd) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pwd + "\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    private MockMultipartFile oneRowXlsx(String code) throws Exception {
        phone = "0560" + (System.nanoTime() % 1000000L);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Workbook wb = new Workbook(os, "test", "1.0")) {
            Worksheet ws = wb.newWorksheet("Sheet1");
            for (int c = 0; c < HEADER.length; c++) ws.value(0, c, HEADER[c]);
            Object[] row = {"API1", 500, "Api Import", "Accra", "api addr", phone, "Any", code, 1, "未发货", 46280};
            for (int c = 0; c < row.length; c++) {
                if (row[c] instanceof Number n) ws.value(1, c, n);
                else ws.value(1, c, String.valueOf(row[c]));
            }
        }
        return new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", os.toByteArray());
    }

    private String anyCode() {
        List<SupplierProduct> all = supplierProductMapper.selectList(
                new LambdaQueryWrapper<SupplierProduct>().last("limit 1"));
        assertTrue(!all.isEmpty(), "dev 库需要至少一个 supplier_product");
        SupplierProduct p = all.get(0);
        stockSnapshots.putIfAbsent(p.getId(), snapshotQty(p.getId()));
        return p.getProductCode();
    }

    private Integer snapshotQty(Long supplierProductId) {
        InventoryStock s = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSupplierProductId, supplierProductId));
        return s == null ? null : s.getQuantity();
    }

    @Test
    void superadmin_can_import() throws Exception {
        mvc.perform(multipart("/api/sales-orders/import").file(oneRowXlsx(anyCode()))
                        .header("Authorization", token("superadmin", "Admin@123")))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.skipped").value(0))
                .andExpect(jsonPath("$.data.failed").value(0));
    }

    @Test
    void non_xlsx_rejected_with_40009() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "orders.csv", "text/csv",
                "Order ID,Sale Price\n1,2\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/sales-orders/import").file(csv)
                        .header("Authorization", token("superadmin", "Admin@123")))
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void request_without_token_is_rejected() throws Exception {
        // 不断言具体状态码/业务码（未登录走 Sa-Token 异常通道，形态可能是 401 或 Result）；
        // 只要求它没有成功，且没有建单。
        MvcResult r = mvc.perform(multipart("/api/sales-orders/import").file(oneRowXlsx(anyCode())))
                .andReturn();
        assertTrue(!r.getResponse().getContentAsString().contains("\"code\":0"),
                "未登录不得导入成功，实际响应: " + r.getResponse().getContentAsString());
        assertTrue(orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).isEmpty(), "未登录不得建单");
    }

    /**
     * 设计要求鉴权覆盖三种态：有权限、未登录（见上）、登录但无 sales:order:import 权限（本用例）。
     * dev 库没有现成「已登录但缺这个权限」的种子账号，就地建一个空权限角色+用户，跑完物理删除。
     */
    @Test
    void logged_in_user_without_import_permission_is_forbidden() throws Exception {
        String su = token("superadmin", "Admin@123");
        long ts = System.nanoTime();

        long roleId = postForId("/api/system/roles",
                "{\"name\":\"NoImport_" + ts + "\",\"code\":\"NOIMPORT_" + ts + "\",\"status\":1}", su);
        String uname = "noimport_" + ts;
        long userId = postForId("/api/system/users",
                "{\"username\":\"" + uname + "\",\"password\":\"Test@123\",\"status\":1}", su);
        mvc.perform(put("/api/system/users/" + userId + "/roles").header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[" + roleId + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        try {
            String t = token(uname, "Test@123");
            mvc.perform(multipart("/api/sales-orders/import").file(oneRowXlsx(anyCode()))
                            .header("Authorization", t))
                    .andExpect(jsonPath("$.code").value(403));
            assertTrue(orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                    .eq(SalesOrder::getCustomerPhone, phone)).isEmpty(), "无权限不得建单");
        } finally {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM sys_user WHERE id = ?", userId);
            jdbc.update("DELETE FROM sys_role WHERE id = ?", roleId);
        }
    }
}
