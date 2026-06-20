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

@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderLabelsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String login(String u, String p) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    @Test
    void labels_returns_today_pending_orders_with_label_fields() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();

        long supplierId = postForId("/api/suppliers", "{\"name\":\"LBL_Sup_" + ts + "\",\"status\":1}", su);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"LBL_Prod_" + ts
                        + "\",\"productCode\":\"LBLC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
                .andExpect(jsonPath("$.code").value(0));

        long orderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Ama\",\"customerPhone\":\"024999\",\"customerAddress\":\"Kumasi\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":3}]}", su);

        MvcResult res = mvc.perform(get("/api/sales-orders/labels").header("Authorization", su))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode data = om.readTree(res.getResponse().getContentAsString()).at("/data");
        assertThat(data.isArray()).isTrue();

        JsonNode mine = null;
        for (JsonNode n : data) {
            if (n.get("id").asLong() == orderId) { mine = n; break; }
        }
        assertThat(mine).as("今日新建订单应在面单结果中").isNotNull();
        assertThat(mine.get("customerName").asText()).isEqualTo("Ama");
        assertThat(mine.get("customerPhone").asText()).isEqualTo("024999");
        assertThat(mine.get("customerAddress").asText()).isEqualTo("Kumasi");
        assertThat(mine.get("totalQty").asInt()).isEqualTo(3);
        assertThat(mine.get("totalAmount").asDouble()).isEqualTo(600.0);
        assertThat(mine.has("orderNo")).isTrue();
    }

    @Test
    void labels_excludes_other_dates() throws Exception {
        String su = login("superadmin", "Admin@123");
        MvcResult res = mvc.perform(get("/api/sales-orders/labels")
                        .header("Authorization", su).param("date", "2000-01-01"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode data = om.readTree(res.getResponse().getContentAsString()).at("/data");
        assertThat(data.isArray()).isTrue();
    }
}
