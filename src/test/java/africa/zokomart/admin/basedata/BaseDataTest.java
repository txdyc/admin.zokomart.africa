package africa.zokomart.admin.basedata;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 集成测试：品牌 CRUD、供应商创建、分类树 + 删父校验。以超管 token 操作。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BaseDataTest {

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
    void brand_crud_flow() throws Exception {
        String t = token();
        String name = "Midea_" + System.currentTimeMillis();
        long id = postForId("/api/brands",
                "{\"name\":\"" + name + "\",\"sort\":1,\"status\":1}", t);

        mvc.perform(get("/api/brands").param("keyword", name).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].name").value(name));

        mvc.perform(put("/api/brands/" + id).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "_x\",\"sort\":2,\"status\":1}"))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/brands/" + id).header("Authorization", t))
                .andExpect(jsonPath("$.data.name").value(name + "_x"));

        mvc.perform(delete("/api/brands/" + id).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void supplier_create_and_detail() throws Exception {
        String t = token();
        String name = "SupplierA_" + System.currentTimeMillis();
        long id = postForId("/api/suppliers",
                "{\"name\":\"" + name + "\",\"contactPerson\":\"Kofi\",\"contactPhone\":\"024\",\"status\":1}", t);
        mvc.perform(get("/api/suppliers/" + id).header("Authorization", t))
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.contactPerson").value("Kofi"));
        mvc.perform(delete("/api/suppliers/" + id).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void category_tree_and_delete_guard() throws Exception {
        String t = token();
        long parentId = postForId("/api/categories",
                "{\"name\":\"Appliances\",\"parentId\":0,\"sort\":1,\"status\":1}", t);
        long childId = postForId("/api/categories",
                "{\"name\":\"Fridges\",\"parentId\":" + parentId + ",\"sort\":1,\"status\":1}", t);

        MvcResult treeRes = mvc.perform(get("/api/categories/tree").header("Authorization", t))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roots = om.readTree(treeRes.getResponse().getContentAsString()).at("/data");
        JsonNode parentNode = null;
        for (JsonNode n : roots) {
            if (n.get("id").asLong() == parentId) {
                parentNode = n;
                break;
            }
        }
        assertThat(parentNode).isNotNull();
        assertThat(parentNode.get("children")).isNotEmpty();

        // 有子分类时删除父分类应被拒绝
        mvc.perform(delete("/api/categories/" + parentId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(500));

        // 清理：先删子再删父
        mvc.perform(delete("/api/categories/" + childId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/categories/" + parentId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void logistics_provider_create() throws Exception {
        String t = token();
        String name = "DHL_" + System.currentTimeMillis();
        long id = postForId("/api/logistics-providers",
                "{\"name\":\"" + name + "\",\"contactPhone\":\"030\",\"status\":1}", t);
        mvc.perform(delete("/api/logistics-providers/" + id).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
    }
}
