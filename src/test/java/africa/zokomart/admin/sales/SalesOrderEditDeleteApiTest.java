package africa.zokomart.admin.sales;

import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 管理员编辑 / 逻辑删除销售订单：明细替换按净变化调库存、派送后仅可改客户信息、仅待派送可删并回补库存、权限。 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderEditDeleteApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    InventoryStockService stockService;

    private String login(String u, String p) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    private long product(String su, long supplierId, String tag) throws Exception {
        return postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"SOED_" + tag
                        + "\",\"productCode\":\"SOEDC_" + tag + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
    }

    private JsonNode detail(long orderId, String t) throws Exception {
        return om.readTree(mvc.perform(get("/api/sales-orders/" + orderId).header("Authorization", t))
                .andReturn().getResponse().getContentAsString()).at("/data");
    }

    private void putOrder(long orderId, String body, String t, int expectCode) throws Exception {
        mvc.perform(put("/api/sales-orders/" + orderId).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(expectCode));
    }

    @Test
    void admin_edits_items_and_customer_then_deletes_with_stock_restored() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();
        long supplierId = postForId("/api/suppliers", "{\"name\":\"SOED_Sup_" + ts + "\",\"status\":1}", su);
        long spA = product(su, supplierId, "A" + ts);
        long spB = product(su, supplierId, "B" + ts);

        long orderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Kofi\",\"customerPhone\":\"024\",\"customerAddress\":\"Accra\",\"remark\":\"r\","
                        + "\"items\":[{\"supplierProductId\":" + spA + ",\"qty\":3,\"unitPrice\":200}]}", su);
        assertThat(stockService.getQty(spA)).isEqualTo(-3);
        long itemA = detail(orderId, su).at("/items/0/id").asLong();

        // 保留行 A 改为 1 件、新增 B 2 件、改客户信息并清空备注
        putOrder(orderId, "{\"customerName\":\"Ama\",\"customerPhone\":\"055\",\"customerAddress\":\"Kumasi\","
                + "\"city\":\"Kumasi\",\"orderDate\":\"2026-01-05\",\"remark\":null,\"items\":["
                + "{\"id\":" + itemA + ",\"supplierProductId\":" + spA + ",\"qty\":1,\"unitPrice\":150},"
                + "{\"supplierProductId\":" + spB + ",\"qty\":2,\"unitPrice\":100}]}", su, 0);

        JsonNode d = detail(orderId, su);
        assertThat(d.get("customerName").asText()).isEqualTo("Ama");
        assertThat(d.get("city").asText()).isEqualTo("Kumasi");
        assertThat(d.get("orderDate").asText()).isEqualTo("2026-01-05");
        assertThat(d.get("createTime").asText()).startsWith("2026-01-05");
        assertThat(d.get("remark").isNull()).isTrue();
        assertThat(d.get("totalQty").asInt()).isEqualTo(3);
        assertThat(d.get("totalAmount").decimalValue()).isEqualByComparingTo("350");
        assertThat(d.get("items")).hasSize(2);
        assertThat(stockService.getQty(spA)).isEqualTo(-1);
        assertThat(stockService.getQty(spB)).isEqualTo(-2);

        // 他单的明细 id 不可混入
        putOrder(orderId, "{\"customerName\":\"Ama\",\"customerPhone\":\"055\",\"customerAddress\":\"Kumasi\","
                + "\"items\":[{\"id\":1,\"supplierProductId\":" + spA + ",\"qty\":1,\"unitPrice\":1}]}", su, 404);

        // 派送后：改客户信息可以，改明细不行
        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logisticsProviderId\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        putOrder(orderId, "{\"customerName\":\"Ama K\",\"customerPhone\":\"055\",\"customerAddress\":\"Kumasi\"}", su, 0);
        assertThat(detail(orderId, su).get("customerName").asText()).isEqualTo("Ama K");
        putOrder(orderId, "{\"customerName\":\"Ama\",\"customerPhone\":\"055\",\"customerAddress\":\"Kumasi\","
                + "\"items\":[{\"supplierProductId\":" + spA + ",\"qty\":5,\"unitPrice\":1}]}", su, 40003);
        assertThat(stockService.getQty(spA)).isEqualTo(-1);

        // 已派送：不可删除，库存不动
        mvc.perform(delete("/api/sales-orders/" + orderId).header("Authorization", su))
                .andExpect(jsonPath("$.code").value(40003));
        assertThat(stockService.getQty(spA)).isEqualTo(-1);
        assertThat(detail(orderId, su).get("id").asLong()).isEqualTo(orderId);

        // 待派送：可删除，整单数量回补，之后查不到
        long pendingId = postForId("/api/sales-orders",
                "{\"customerName\":\"Yaw\",\"customerPhone\":\"020\",\"customerAddress\":\"Tema\","
                        + "\"items\":[{\"supplierProductId\":" + spB + ",\"qty\":4,\"unitPrice\":100}]}", su);
        assertThat(stockService.getQty(spB)).isEqualTo(-6);
        mvc.perform(delete("/api/sales-orders/" + pendingId).header("Authorization", su))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(stockService.getQty(spB)).isEqualTo(-2);
        mvc.perform(get("/api/sales-orders/" + pendingId).header("Authorization", su))
                .andExpect(jsonPath("$.code").value(404));
        mvc.perform(delete("/api/sales-orders/" + pendingId).header("Authorization", su))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void user_without_permission_cannot_edit_or_delete() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();
        long roleId = postForId("/api/system/roles",
                "{\"name\":\"SOED_Role_" + ts + "\",\"code\":\"SOED_" + ts + "\",\"status\":1,\"menuIds\":[2054]}", su);
        // 仅授予种子菜单 2054 sales:order:list，不新建菜单，避免测试往 sys_menu 堆垃圾
        String uname = "soed_" + ts;
        long userId = postForId("/api/system/users",
                "{\"username\":\"" + uname + "\",\"password\":\"Test@123\",\"status\":1}", su);
        mvc.perform(put("/api/system/users/" + userId + "/roles").header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[" + roleId + "]}"))
                .andExpect(jsonPath("$.code").value(0));
        String t = login(uname, "Test@123");

        MvcResult r = mvc.perform(delete("/api/sales-orders/1").header("Authorization", t)).andReturn();
        assertThat(om.readTree(r.getResponse().getContentAsString()).get("code").asInt()).isNotZero();
        r = mvc.perform(put("/api/sales-orders/1").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerName\":\"x\",\"customerPhone\":\"x\",\"customerAddress\":\"x\"}")).andReturn();
        assertThat(om.readTree(r.getResponse().getContentAsString()).get("code").asInt()).isNotZero();
    }
}
