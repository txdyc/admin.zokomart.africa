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
 * Phase 4 集成测试：供应商产品 CRUD（含 MOQ）、联动筛选、(supplier,product_code) 唯一、
 * 被引用的品牌删除被拒。以超管 token 操作；自建数据测试结束清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SupplierProductTest {

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
    void crud_filters_unique_and_reference_guard() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();

        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"SP_Sup_" + ts + "\",\"contactPerson\":\"Ama\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"SP_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        long categoryId = postForId("/api/categories",
                "{\"name\":\"SP_Cat_" + ts + "\",\"parentId\":0,\"sort\":1,\"status\":1}", t);

        String code = "PC_" + ts;
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"Blender_" + ts + "\",\"brandId\":" + brandId
                        + ",\"categoryId\":" + categoryId + ",\"productCode\":\"" + code
                        + "\",\"wholesalePrice\":100,\"retailPrice\":200,\"minPurchaseQty\":5,\"status\":1}", t);

        // MOQ 落库
        mvc.perform(get("/api/supplier-products/" + spId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.minPurchaseQty").value(5))
                .andExpect(jsonPath("$.data.retailPrice").value(200.00));

        // 联动筛选命中
        mvc.perform(get("/api/supplier-products").header("Authorization", t)
                        .param("supplierId", String.valueOf(supplierId))
                        .param("brandId", String.valueOf(brandId))
                        .param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].productCode").value(code));

        // 供应商已有产品涉及的品牌/分类
        mvc.perform(get("/api/suppliers/" + supplierId + "/brands").header("Authorization", t))
                .andExpect(jsonPath("$.data[0].id").value(brandId));
        mvc.perform(get("/api/suppliers/" + supplierId + "/categories").header("Authorization", t))
                .andExpect(jsonPath("$.data[0].id").value(categoryId));

        // (supplier, product_code) 唯一：重复编码报错
        mvc.perform(post("/api/supplier-products").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"name\":\"Dup_" + ts
                                + "\",\"productCode\":\"" + code + "\",\"wholesalePrice\":1,\"retailPrice\":1}"))
                .andExpect(jsonPath("$.code").value(500));

        // 被供应商产品引用的品牌/供应商/分类不能删除
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(500));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(500));
        mvc.perform(delete("/api/categories/" + categoryId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(500));

        // 清理：先删产品，引用解除后基础数据可删
        mvc.perform(delete("/api/supplier-products/" + spId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/categories/" + categoryId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void create_rejects_qty_below_one() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"SP_Sup2_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);

        // minPurchaseQty < 1 触发 @Min 校验 -> code=400
        mvc.perform(post("/api/supplier-products").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"name\":\"X_" + ts
                                + "\",\"productCode\":\"PCX_" + ts + "\",\"minPurchaseQty\":0}"))
                .andExpect(jsonPath("$.code").value(400));

        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
    }
}
