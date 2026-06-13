package africa.zokomart.admin.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Phase 5 控制器层端到端冒烟（HTTP）：登录→建供应商/产品→采购计划→提交→审批生单
 * →标付款→确认生成实际采购单→入库→实际采购单 INBOUND_DONE。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseFlowApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}"))
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

    @Test
    void full_purchase_chain_over_http() throws Exception {
        String t = token();
        long ts = System.nanoTime();

        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"FLOW_Sup_" + ts + "\",\"status\":1}", t);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"FLOW_Prod_" + ts
                        + "\",\"productCode\":\"FLOWC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", t);

        long planId = postForId("/api/purchase-plans",
                "{\"items\":[{\"supplierProductId\":" + spId + ",\"purchaseQty\":5}]}", t);

        mvc.perform(post("/api/purchase-plans/" + planId + "/submit").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/purchase-plans/" + planId + "/approve").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));

        // 取该计划生成的采购订单
        MvcResult ordersRes = mvc.perform(get("/api/purchase-orders").header("Authorization", t)
                        .param("planId", String.valueOf(planId)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long orderId = om.readTree(ordersRes.getResponse().getContentAsString())
                .at("/data/records/0/id").asLong();

        // 订单详情拿明细 id
        MvcResult orderDetail = mvc.perform(get("/api/purchase-orders/" + orderId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode items = om.readTree(orderDetail.getResponse().getContentAsString()).at("/data/items");
        long itemId = items.get(0).get("id").asLong();

        // 标记已付款
        mvc.perform(put("/api/purchase-orders/" + orderId + "/items/payment").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[" + itemId + "],\"paymentStatus\":\"PAID\"}"))
                .andExpect(jsonPath("$.code").value(0));

        // 确认生成实际采购单
        long actualId = Long.parseLong(om.readTree(
                mvc.perform(post("/api/purchase-orders/" + orderId + "/confirm").header("Authorization", t))
                        .andExpect(jsonPath("$.code").value(0))
                        .andReturn().getResponse().getContentAsString()).at("/data").asText());

        // 入库（整单）
        mvc.perform(post("/api/actual-purchase-orders/" + actualId + "/inbound").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));

        // 实际采购单已入库完成
        mvc.perform(get("/api/actual-purchase-orders/" + actualId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("INBOUND_DONE"))
                .andExpect(jsonPath("$.data.items[0].inboundStatus").value("DONE"));
    }
}
