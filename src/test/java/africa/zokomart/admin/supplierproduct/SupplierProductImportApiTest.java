package africa.zokomart.admin.supplierproduct;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 供应商产品 CSV 导入集成测试：尽力导入 + 逐行报告、skip/overwrite、品牌未授权快速失败。
 * 以超管 token 操作；自建数据测试结束清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SupplierProductImportApiTest {

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

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv",
                ("﻿" + content).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void import_best_effort_skip_then_overwrite() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();

        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"IMP_Sup_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"IMP_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        long catId = postForId("/api/categories",
                "{\"name\":\"IMP_Cat_" + ts + "\",\"parentId\":0,\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandIds\":[" + brandId + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        String catName = "IMP_Cat_" + ts;
        // 4 行：1 好(带分类) / 1 名称为空(坏) / 1 分类未找到(坏) / 1 好
        String body = "产品名称,产品编码,分类路径,批发价,零售价,最小采购量,图片URL,备注\n"
                + "好货A," + "A" + ts + "," + catName + ",100,200,5,,ok\n"
                + ",B" + ts + ",,1,2,1,,缺名称\n"
                + "好货C,C" + ts + ",不存在的分类,1,2,1,,坏分类\n"
                + "好货D,D" + ts + ",,1,2,1,,ok\n";

        MvcResult r = mvc.perform(multipart("/api/supplier-products/import").file(csv(body))
                        .param("supplierId", String.valueOf(supplierId))
                        .param("brandId", String.valueOf(brandId))
                        .param("mode", "skip")
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.failed").value(2))
                .andReturn();
        // 坏行行号应为 3 与 4（表头第 1 行）
        String json = r.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"row\":3"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"row\":4"));

        // 再次 skip 同文件：两条好行已存在 → skipped=2
        mvc.perform(multipart("/api/supplier-products/import").file(csv(body))
                        .param("supplierId", String.valueOf(supplierId))
                        .param("brandId", String.valueOf(brandId))
                        .param("mode", "skip")
                        .header("Authorization", t))
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.skipped").value(2))
                .andExpect(jsonPath("$.data.failed").value(2));

        // overwrite：好行 A 改零售价 → updated 计入
        String body2 = "产品名称,产品编码,分类路径,批发价,零售价,最小采购量,图片URL,备注\n"
                + "好货A改名,A" + ts + ",,100,999,5,,upd\n";
        mvc.perform(multipart("/api/supplier-products/import").file(csv(body2))
                        .param("supplierId", String.valueOf(supplierId))
                        .param("brandId", String.valueOf(brandId))
                        .param("mode", "overwrite")
                        .header("Authorization", t))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        // 清理：删该供应商下所有产品（按编码查询页拿到后逐个删）
        for (String code : new String[]{"A" + ts, "C" + ts, "D" + ts}) {
            MvcResult pr = mvc.perform(get("/api/supplier-products").header("Authorization", t)
                            .param("supplierId", String.valueOf(supplierId)).param("keyword", code))
                    .andReturn();
            var recs = om.readTree(pr.getResponse().getContentAsString()).at("/data/records");
            for (var n : recs) {
                mvc.perform(delete("/api/supplier-products/" + n.at("/id").asLong()).header("Authorization", t));
            }
        }
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
        mvc.perform(delete("/api/categories/" + catId).header("Authorization", t));
    }

    @Test
    void import_rejects_unauthorized_brand() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"IMP_Sup2_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"IMP_Brand2_" + ts + "\",\"sort\":1,\"status\":1}", t);
        // 未授权该品牌 → 整请求失败 40007
        mvc.perform(multipart("/api/supplier-products/import")
                        .file(csv("产品名称,产品编码,分类路径,批发价,零售价,最小采购量,图片URL,备注\nX,X" + ts + ",,1,1,1,,\n"))
                        .param("supplierId", String.valueOf(supplierId))
                        .param("brandId", String.valueOf(brandId))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(40007));

        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }
}
