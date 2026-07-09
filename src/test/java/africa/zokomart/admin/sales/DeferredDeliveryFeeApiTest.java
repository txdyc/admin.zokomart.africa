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
 * 延迟录入派送费：派送时可不填（NULL=未知），签收/拒签时可补录或修正；
 * 未提供时保留原值；负数 400。区域惯例：站点送达后才告知运费。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeferredDeliveryFeeApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String login() throws Exception {
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

    /** 建一个可派送订单（供应商+产品+库存+下单），返回 orderId。 */
    private long newDispatchableOrder(String t, String tag) throws Exception {
        long supplierId = postForId("/api/suppliers", "{\"name\":\"DDF_Sup_" + tag + "\",\"status\":1}", t);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"DDF_Prod_" + tag
                        + "\",\"productCode\":\"DDF_" + tag + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", t);
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
                .andExpect(jsonPath("$.code").value(0));
        return postForId("/api/sales-orders",
                "{\"customerName\":\"DDF\",\"customerPhone\":\"024\",\"customerAddress\":\"Accra\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":1}]}", t);
    }

    private JsonNode detail(String t, long orderId) throws Exception {
        MvcResult r = mvc.perform(get("/api/sales-orders/" + orderId).header("Authorization", t)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data");
    }

    @Test
    void dispatch_without_fee_then_fill_fee_at_sign() throws Exception {
        String t = login();
        long orderId = newDispatchableOrder(t, "A" + System.nanoTime());

        // 派送：不带 deliveryFee → 成功，detail 中为 null
        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logisticsProviderId\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(detail(t, orderId).get("deliveryFee").isNull()).isTrue();

        // 签收时补录 25.00
        mvc.perform(put("/api/sales-orders/" + orderId + "/status").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SIGNED\",\"deliveryFee\":25.00}"))
                .andExpect(jsonPath("$.code").value(0));
        JsonNode d = detail(t, orderId);
        assertThat(d.get("status").asText()).isEqualTo("SIGNED");
        assertThat(d.get("deliveryFee").decimalValue()).isEqualByComparingTo("25.00");

        // 再转 SIGNED_PAID 不带费用 → 保留 25.00
        mvc.perform(put("/api/sales-orders/" + orderId + "/status").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SIGNED_PAID\"}"))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(detail(t, orderId).get("deliveryFee").decimalValue()).isEqualByComparingTo("25.00");
    }

    @Test
    void rejected_with_fee_stores_fee_and_completes() throws Exception {
        String t = login();
        long orderId = newDispatchableOrder(t, "B" + System.nanoTime());
        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logisticsProviderId\":1}"))
                .andExpect(jsonPath("$.code").value(0));

        // 整单拒签同时补录派送费 18.00（拒签会自动完成并锁单，费用必须同调用写入）
        mvc.perform(put("/api/sales-orders/" + orderId + "/status").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"deliveryFee\":18.00}"))
                .andExpect(jsonPath("$.code").value(0));
        JsonNode d = detail(t, orderId);
        assertThat(d.get("status").asText()).isEqualTo("REJECTED");
        assertThat(d.get("completed").asInt()).isEqualTo(1);
        assertThat(d.get("deliveryFee").decimalValue()).isEqualByComparingTo("18.00");
    }

    @Test
    void negative_fee_rejected_on_both_endpoints() throws Exception {
        String t = login();
        long orderId = newDispatchableOrder(t, "C" + System.nanoTime());

        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logisticsProviderId\":1,\"deliveryFee\":-1}"))
                .andExpect(jsonPath("$.code").value(400));

        mvc.perform(post("/api/sales-orders/" + orderId + "/dispatch").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logisticsProviderId\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(put("/api/sales-orders/" + orderId + "/status").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SIGNED\",\"deliveryFee\":-5}"))
                .andExpect(jsonPath("$.code").value(400));
    }
}
