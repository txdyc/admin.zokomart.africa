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

/** orderable-products：返回全部在售产品（含无库存），有货优先，keyword 命中。 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderableProductsApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String login(String u, String p) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}")).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }
    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    @Test
    void lists_all_products_instock_first_with_keyword() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();
        long supplierId = postForId("/api/suppliers", "{\"name\":\"OPSup_" + ts + "\",\"status\":1}", su);
        String kw = "OPKW" + ts;
        // out-of-stock product (no inbound)
        long noStock = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"" + kw + "_zero\",\"productCode\":\"" + kw
                        + "Z\",\"wholesalePrice\":100,\"retailPrice\":200,\"minPurchaseQty\":1,\"status\":1}", su);
        // in-stock product (inbound 5)
        long inStock = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"" + kw + "_five\",\"productCode\":\"" + kw
                        + "F\",\"wholesalePrice\":100,\"retailPrice\":300,\"minPurchaseQty\":1,\"status\":1}", su);
        mvc.perform(put("/api/inventory/stocks/" + inStock).header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(jsonPath("$.code").value(0));

        MvcResult r = mvc.perform(get("/api/sales-orders/orderable-products").header("Authorization", su)
                        .param("keyword", kw).param("size", "50"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode recs = om.readTree(r.getResponse().getContentAsString()).at("/data/records");
        // both products present
        assertThat(recs).hasSize(2);
        // in-stock first
        assertThat(recs.get(0).get("supplierProductId").asLong()).isEqualTo(inStock);
        assertThat(recs.get(0).get("quantity").asInt()).isEqualTo(5);
        assertThat(recs.get(0).get("retailPrice").asDouble()).isEqualTo(300.0);
        assertThat(recs.get(1).get("supplierProductId").asLong()).isEqualTo(noStock);
        assertThat(recs.get(1).get("quantity").asInt()).isEqualTo(0);
    }
}
