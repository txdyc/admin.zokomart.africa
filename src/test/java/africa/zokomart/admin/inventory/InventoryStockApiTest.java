package africa.zokomart.admin.inventory;

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
 * Phase 6 控制器层：手工调整库存 → 列表联动筛选回显（含名称、数量一致）；负数被拒。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryStockApiTest {

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
    void adjust_then_list_reflects_quantity_and_names() throws Exception {
        String t = token();
        long ts = System.nanoTime();
        String supName = "INVAPI_Sup_" + ts;
        long supplierId = postForId("/api/suppliers", "{\"name\":\"" + supName + "\",\"status\":1}", t);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"INVAPI_Prod_" + ts
                        + "\",\"productCode\":\"INVAPIC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", t);

        // 手工调整建账 = 12
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":12,\"remark\":\"建账\"}"))
                .andExpect(jsonPath("$.code").value(0));

        // 列表按供应商筛选回显
        mvc.perform(get("/api/inventory/stocks").header("Authorization", t)
                        .param("supplierId", String.valueOf(supplierId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].supplierProductId").value(String.valueOf(spId)))
                .andExpect(jsonPath("$.data.records[0].quantity").value(12))
                .andExpect(jsonPath("$.data.records[0].supplierName").value(supName));

        // 负数被拒（@Min 校验 -> code 400）
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":-1}"))
                .andExpect(jsonPath("$.code").value(400));
    }
}
