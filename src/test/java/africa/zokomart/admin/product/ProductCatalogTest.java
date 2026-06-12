package africa.zokomart.admin.product;

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
 * Phase 3 集成测试：SPU 创建 → 挂 2 个 SKU → 按 SPU 查 SKU 列表 = 2 → 删 SPU 受 SKU 阻拦。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCatalogTest {

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

    private long postForId(String url, String body, String token) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    @Test
    void spu_with_two_skus() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long spuId = postForId("/api/product-spus",
                "{\"name\":\"TV_" + ts + "\",\"status\":1}", t);
        long sku1 = postForId("/api/product-skus",
                "{\"spuId\":" + spuId + ",\"skuCode\":\"SKU1_" + ts + "\",\"price\":100,\"status\":1}", t);
        long sku2 = postForId("/api/product-skus",
                "{\"spuId\":" + spuId + ",\"skuCode\":\"SKU2_" + ts + "\",\"price\":200,\"status\":1}", t);

        mvc.perform(get("/api/product-spus/" + spuId + "/skus").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2));

        // SPU 下有 SKU 时不可删除
        mvc.perform(delete("/api/product-spus/" + spuId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(500));

        // 清理
        mvc.perform(delete("/api/product-skus/" + sku1).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/product-skus/" + sku2).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/product-spus/" + spuId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
    }
}
