package africa.zokomart.admin.supplierproduct;

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
 * URL 抓取/导入集成测试：非白名单 URL 拒绝；import-scraped best-effort + 新列落库 + skip/overwrite。
 * 超管 token；自建数据并清理。不做联网抓取。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SupplierProductScrapeApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}")).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    @Test
    void scrape_rejects_non_allowlisted_host() throws Exception {
        String t = token();
        mvc.perform(post("/api/supplier-products/scrape").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://evil.example.com/x\"}"))
                .andExpect(jsonPath("$.code").value(40011));
        // http 协议也拒绝
        mvc.perform(post("/api/supplier-products/scrape").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://morgan.dzncm.com/x\"}"))
                .andExpect(jsonPath("$.code").value(40011));
    }

    @Test
    void import_scraped_best_effort_and_new_columns() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"SC_Sup_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"SC_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandIds\":[" + brandId + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        String c1 = "SC_A_" + ts, c2 = "SC_B_" + ts;
        // 3 行：c1(好,带新列) / c2(好) / c1(批次内重复 -> 失败)
        String rows = "["
                + "{\"productName\":\"Juicer\",\"productCode\":\"" + c1 + "\",\"qtyPerBox\":6,"
                + "\"imageUrl\":\"https://morgan.dzncm.com/uploadfile/202601/eafe.jpg\",\"unitPrice\":220,\"boxPrice\":1320,\"stockStatus\":\"Stock Sufficient\"},"
                + "{\"productName\":\"Blender\",\"productCode\":\"" + c2 + "\",\"qtyPerBox\":12,\"unitPrice\":90,\"boxPrice\":1080,\"stockStatus\":\"Stock Less\"},"
                + "{\"productName\":\"Dup\",\"productCode\":\"" + c1 + "\",\"unitPrice\":1,\"boxPrice\":1}"
                + "]";
        String body = "{\"supplierId\":" + supplierId + ",\"brandId\":" + brandId + ",\"mode\":\"skip\",\"rows\":" + rows + "}";

        mvc.perform(post("/api/supplier-products/import-scraped").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.failed").value(1));

        // 新列落库：查 c1
        MvcResult pr = mvc.perform(get("/api/supplier-products").header("Authorization", t)
                        .param("supplierId", String.valueOf(supplierId)).param("keyword", c1)).andReturn();
        var rec = om.readTree(pr.getResponse().getContentAsString()).at("/data/records/0");
        org.junit.jupiter.api.Assertions.assertEquals(6, rec.at("/qtyPerBox").asInt());
        // MOQ 必须等于每箱量（最小采购量为一箱）
        org.junit.jupiter.api.Assertions.assertEquals(6, rec.at("/minPurchaseQty").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("Stock Sufficient", rec.at("/stockStatus").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("1320")
                .compareTo(new java.math.BigDecimal(rec.at("/boxPrice").asText())));

        // 再次 skip：c1/c2 已存在 -> skipped=2，dup 仍失败
        mvc.perform(post("/api/supplier-products/import-scraped").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.skipped").value(2))
                .andExpect(jsonPath("$.data.failed").value(1));

        // overwrite：c1 改单价 -> updated=1
        String body2 = "{\"supplierId\":" + supplierId + ",\"brandId\":" + brandId + ",\"mode\":\"overwrite\",\"rows\":["
                + "{\"productName\":\"Juicer2\",\"productCode\":\"" + c1 + "\",\"unitPrice\":999}]}";
        mvc.perform(post("/api/supplier-products/import-scraped").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        // 清理
        for (String code : new String[]{c1, c2}) {
            MvcResult q = mvc.perform(get("/api/supplier-products").header("Authorization", t)
                    .param("supplierId", String.valueOf(supplierId)).param("keyword", code)).andReturn();
            for (var n : om.readTree(q.getResponse().getContentAsString()).at("/data/records")) {
                mvc.perform(delete("/api/supplier-products/" + n.at("/id").asLong()).header("Authorization", t));
            }
        }
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }
}
