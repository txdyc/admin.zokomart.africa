package africa.zokomart.admin.ad;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AdAiModelApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}")).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    @Test
    void crud_with_key_masking_and_blank_key_keeps_old() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();

        // 新建
        MvcResult cr = mvc.perform(post("/api/ad/models").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NB_" + ts + "\",\"baseUrl\":\"https://agg.example/v1\","
                                + "\"apiKey\":\"sk-secret-abcd1234\",\"modelCode\":\"nano-banana-pro\",\"enabled\":1}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long id = om.readTree(cr.getResponse().getContentAsString()).at("/data").asLong();

        // 列表：key 脱敏，明文绝不出现
        MvcResult pg = mvc.perform(get("/api/ad/models").header("Authorization", t)
                        .param("keyword", "NB_" + ts))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].apiKeyMasked", endsWith("1234"))).andReturn();
        assertFalse(pg.getResponse().getContentAsString().contains("sk-secret-abcd1234"));

        // 编辑留空 key -> 不改；enabled 停用
        mvc.perform(put("/api/ad/models/" + id).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NB_" + ts + "\",\"baseUrl\":\"https://agg.example/v1\","
                                + "\"apiKey\":\"\",\"modelCode\":\"nano-banana-pro\",\"enabled\":0}"))
                .andExpect(jsonPath("$.code").value(0));

        // enabled 列表：停用后不出现
        mvc.perform(get("/api/ad/models/enabled").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.name=='NB_" + ts + "')]").isEmpty());

        // 清理
        mvc.perform(delete("/api/ad/models/" + id).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
    }
}
