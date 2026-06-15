package africa.zokomart.admin.basedata;

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

@SpringBootTest
@AutoConfigureMockMvc
class SupplierBrandApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

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
    void assign_enforce_and_guard_flow() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"SBA_Sup_" + ts + "\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"SBA_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);

        // 未授权时录产品 -> 40007
        mvc.perform(post("/api/supplier-products").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"name\":\"P_" + ts
                                + "\",\"brandId\":" + brandId + ",\"productCode\":\"PC_" + ts
                                + "\",\"wholesalePrice\":1,\"retailPrice\":2,\"minPurchaseQty\":1}"))
                .andExpect(jsonPath("$.code").value(40007));

        // 授权
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandIds\":[" + brandId + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        // 回显
        mvc.perform(get("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].brandId").value(String.valueOf(brandId)));

        // 采购联动也返回授权品牌
        mvc.perform(get("/api/suppliers/" + supplierId + "/brands").header("Authorization", t))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(brandId)));

        // 授权后录产品成功
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"P_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"PC_" + ts + "\",\"wholesalePrice\":1,\"retailPrice\":2,\"minPurchaseQty\":1}", t);

        // 品牌被授权引用 -> 删除被挡
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(500));

        // 清理：删产品 -> 删供应商(清绑定) -> 删品牌
        mvc.perform(delete("/api/supplier-products/" + spId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0));
    }
}
