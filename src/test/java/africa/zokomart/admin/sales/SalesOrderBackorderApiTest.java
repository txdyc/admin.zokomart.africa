package africa.zokomart.admin.sales;

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

/** 缺货可下单（backorder）：无库存记录的产品下单成功，库存转负。 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderBackorderApiTest {

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
    void out_of_stock_product_can_be_backordered() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();
        long supplierId = postForId("/api/suppliers", "{\"name\":\"BOSup_" + ts + "\",\"status\":1}", su);
        // NOTE: no inbound -> product has no inventory_stock row (qty 0)
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"BOProd_" + ts
                        + "\",\"productCode\":\"BOC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);

        // order qty 2 with zero stock -> succeeds (backorder)
        long orderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Kofi\",\"customerPhone\":\"024\",\"customerAddress\":\"Accra\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":2}]}", su);
        assert orderId > 0;

        // stock now -2
        mvc.perform(get("/api/inventory/stocks").header("Authorization", su).param("keyword", "BOC_" + ts))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].quantity").value(-2));
    }
}
