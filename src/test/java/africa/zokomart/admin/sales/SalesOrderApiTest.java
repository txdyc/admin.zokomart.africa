package africa.zokomart.admin.sales;

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

/**
 * Phase 7 Task 7.3 + 控制器层：销售员仅见本人订单、completed 筛选；
 * 并以销售员角色走 HTTP 全链（下单→派送→签收→拒收→完成结算）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

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

    private long menu(String t, String name, String perm) throws Exception {
        return postForId("/api/system/menus",
                "{\"name\":\"" + name + "\",\"type\":3,\"permCode\":\"" + perm + "\",\"status\":1}", t);
    }

    @Test
    void salesperson_sees_own_orders_and_runs_full_flow() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();

        // 超管建供应商 + 产品 + 库存
        long supplierId = postForId("/api/suppliers", "{\"name\":\"SOAPI_Sup_" + ts + "\",\"status\":1}", su);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"SOAPI_Prod_" + ts
                        + "\",\"productCode\":\"SOAPIC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
                .andExpect(jsonPath("$.code").value(0));

        // 建销售员角色（含下单/列表/物流权限，但不含 sales:order:list:all）
        long mCreate = menu(su, "下单_" + ts, "sales:order:create");
        long mList = menu(su, "销售列表_" + ts, "sales:order:list");
        long mDispatch = menu(su, "派送_" + ts, "logistics:dispatch");
        long mStatus = menu(su, "状态_" + ts, "logistics:status");
        long mReject = menu(su, "拒收_" + ts, "logistics:reject");
        long mComplete = menu(su, "完成_" + ts, "logistics:complete");
        long roleId = postForId("/api/system/roles",
                "{\"name\":\"Salesperson_" + ts + "\",\"code\":\"SALES_" + ts + "\",\"status\":1,\"menuIds\":["
                        + mCreate + "," + mList + "," + mDispatch + "," + mStatus + "," + mReject + "," + mComplete + "]}", su);
        String uname = "sales_" + ts;
        long userId = postForId("/api/system/users",
                "{\"username\":\"" + uname + "\",\"password\":\"Test@123\",\"status\":1}", su);
        mvc.perform(put("/api/system/users/" + userId + "/roles").header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[" + roleId + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        // 超管也下一单（不同 salesperson），用于验证本人筛选
        postForId("/api/sales-orders",
                "{\"customerName\":\"X\",\"customerPhone\":\"024\",\"customerAddress\":\"A\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":1}]}", su);

        // 销售员登录并下单 qty=3
        String sp = login(uname, "Test@123");
        long orderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Kofi\",\"customerPhone\":\"024111\",\"customerAddress\":\"Accra\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":3}]}", sp);

        // 本人筛选：列表仅含本人订单
        MvcResult listRes = mvc.perform(get("/api/sales-orders").header("Authorization", sp)
                        .param("completed", "false"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode records = om.readTree(listRes.getResponse().getContentAsString()).at("/data/records");
        assertThat(records).isNotEmpty();
        for (JsonNode n : records) {
            assertThat(n.get("salespersonId").asLong()).isEqualTo(userId);
        }
        // 已完成筛选此刻应为空
        mvc.perform(get("/api/sales-orders").header("Authorization", sp).param("completed", "true"))
                .andExpect(jsonPath("$.data.records.length()").value(0));

        // 全链：派送→签收→拒收 1→完成
        long itemId = om.readTree(mvc.perform(get("/api/sales-orders/" + orderId).header("Authorization", sp))
                .andReturn().getResponse().getContentAsString()).at("/data/items/0/id").asLong();

        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", sp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logisticsProviderId\":1,\"deliveryFee\":15}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(put("/api/sales-orders/" + orderId + "/status").header("Authorization", sp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SIGNED\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(put("/api/sales-orders/" + orderId + "/items/reject").header("Authorization", sp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":" + itemId + ",\"rejectQty\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/sales-orders/" + orderId + "/complete").header("Authorization", sp))
                .andExpect(jsonPath("$.code").value(0));

        // 结算：实收 200*(3-1)=400，已完成
        mvc.perform(get("/api/sales-orders/" + orderId).header("Authorization", sp))
                .andExpect(jsonPath("$.data.actualAmount").value(400.00))
                .andExpect(jsonPath("$.data.completed").value(1));
    }
}
