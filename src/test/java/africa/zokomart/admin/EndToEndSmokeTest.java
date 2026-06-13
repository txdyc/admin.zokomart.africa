package africa.zokomart.admin;

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
 * Phase 8 Task 8.2：全链路端到端冒烟（HTTP）。
 * 登录 → 建供应商/供应商产品 → 采购计划(5)→提交→审批生单→付款→确认→入库
 * → 库存=5 → 销售下单(3)→派送→签收→拒收(1)→完成结算
 * → 断言库存最终值 = 3（采购入5 − 销售出3 + 拒收回补1）、实收金额 = 200×(3−1)=400。
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndToEndSmokeTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
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

    private long stockOf(long supplierId, long spId, String t) throws Exception {
        MvcResult r = mvc.perform(get("/api/inventory/stocks").header("Authorization", t)
                        .param("supplierId", String.valueOf(supplierId)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        for (JsonNode n : om.readTree(r.getResponse().getContentAsString()).at("/data/records")) {
            if (n.get("supplierProductId").asLong() == spId) {
                return n.get("quantity").asLong();
            }
        }
        throw new AssertionError("库存记录未找到: spId=" + spId);
    }

    @Test
    void full_chain_purchase_inbound_sales_logistics() throws Exception {
        String t = token();
        long ts = System.nanoTime();

        // ---- 基础数据：供应商 + 供应商产品（批发100/零售200/MOQ1） ----
        long supplierId = postForId("/api/suppliers", "{\"name\":\"E2E_Sup_" + ts + "\",\"status\":1}", t);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"E2E_Prod_" + ts
                        + "\",\"productCode\":\"E2EC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", t);

        // ---- 采购全链：计划(5)→提交→审批→付款→确认→入库 ----
        long planId = postForId("/api/purchase-plans",
                "{\"items\":[{\"supplierProductId\":" + spId + ",\"purchaseQty\":5}]}", t);
        mvc.perform(post("/api/purchase-plans/" + planId + "/submit").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/purchase-plans/" + planId + "/approve").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));

        long orderId = om.readTree(mvc.perform(get("/api/purchase-orders").header("Authorization", t)
                        .param("planId", String.valueOf(planId)))
                .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString())
                .at("/data/records/0/id").asLong();
        long poItemId = om.readTree(mvc.perform(get("/api/purchase-orders/" + orderId).header("Authorization", t))
                .andReturn().getResponse().getContentAsString()).at("/data/items/0/id").asLong();

        mvc.perform(put("/api/purchase-orders/" + orderId + "/items/payment").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[" + poItemId + "],\"paymentStatus\":\"PAID\"}"))
                .andExpect(jsonPath("$.code").value(0));
        long actualId = Long.parseLong(om.readTree(
                mvc.perform(post("/api/purchase-orders/" + orderId + "/confirm").header("Authorization", t))
                        .andExpect(jsonPath("$.code").value(0))
                        .andReturn().getResponse().getContentAsString()).at("/data").asText());
        mvc.perform(post("/api/actual-purchase-orders/" + actualId + "/inbound").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));

        // 入库后库存 = 5
        org.assertj.core.api.Assertions.assertThat(stockOf(supplierId, spId, t)).isEqualTo(5L);

        // ---- 销售全链：下单(3)→派送→签收→拒收(1)→完成 ----
        long salesOrderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Ama\",\"customerPhone\":\"024222\",\"customerAddress\":\"Kumasi\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":3}]}", t);
        // 销售扣减后库存 = 2
        org.assertj.core.api.Assertions.assertThat(stockOf(supplierId, spId, t)).isEqualTo(2L);

        long soItemId = om.readTree(mvc.perform(get("/api/sales-orders/" + salesOrderId).header("Authorization", t))
                .andReturn().getResponse().getContentAsString()).at("/data/items/0/id").asLong();

        mvc.perform(post("/api/sales-orders/" + salesOrderId + "/dispatch").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logisticsProviderId\":1,\"deliveryFee\":15}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(put("/api/sales-orders/" + salesOrderId + "/status").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SIGNED\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(put("/api/sales-orders/" + salesOrderId + "/items/reject").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":" + soItemId + ",\"rejectQty\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/sales-orders/" + salesOrderId + "/complete").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));

        // ---- 终值断言：实收 200×(3−1)=400、已完成；库存 5−3+1=3 ----
        mvc.perform(get("/api/sales-orders/" + salesOrderId).header("Authorization", t))
                .andExpect(jsonPath("$.data.actualAmount").value(400.00))
                .andExpect(jsonPath("$.data.completed").value(1));
        org.assertj.core.api.Assertions.assertThat(stockOf(supplierId, spId, t)).isEqualTo(3L);
    }
}
