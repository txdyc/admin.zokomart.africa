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

/**
 * 销售订单详情的本人隔离：无 sales:order:list:all 的用户只能看自己的订单，
 * 访问他人订单返回 NOT_FOUND(404)；超管（通配 list:all）可看任意订单。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderDetailIsolationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String login(String u, String p) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}"))
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

    private long menu(String t, String name, String perm) throws Exception {
        return postForId("/api/system/menus",
                "{\"name\":\"" + name + "\",\"type\":3,\"permCode\":\"" + perm + "\",\"status\":1}", t);
    }

    /** 建一个仅含 create+list（无 list:all）的角色并返回其 id。 */
    private long ownScopeRole(String su, long ts, String tag) throws Exception {
        long mCreate = menu(su, "det下单_" + tag + ts, "sales:order:create");
        long mList = menu(su, "det列表_" + tag + ts, "sales:order:list");
        return postForId("/api/system/roles",
                "{\"name\":\"DetRole_" + tag + ts + "\",\"code\":\"DETR_" + tag + ts
                        + "\",\"status\":1,\"menuIds\":[" + mCreate + "," + mList + "]}", su);
    }

    private long createUserWithRole(String su, long roleId, String uname) throws Exception {
        long userId = postForId("/api/system/users",
                "{\"username\":\"" + uname + "\",\"password\":\"Test@123\",\"status\":1}", su);
        mvc.perform(put("/api/system/users/" + userId + "/roles").header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[" + roleId + "]}"))
                .andExpect(jsonPath("$.code").value(0));
        return userId;
    }

    @Test
    void detail_is_isolated_per_salesperson() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();

        long supplierId = postForId("/api/suppliers", "{\"name\":\"DETSup_" + ts + "\",\"status\":1}", su);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"DETProd_" + ts
                        + "\",\"productCode\":\"DETC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":50}"))
                .andExpect(jsonPath("$.code").value(0));

        long roleA = ownScopeRole(su, ts, "A");
        long roleB = ownScopeRole(su, ts, "B");
        createUserWithRole(su, roleA, "detA_" + ts);
        createUserWithRole(su, roleB, "detB_" + ts);

        // A 下单
        String ta = login("detA_" + ts, "Test@123");
        long orderA = postForId("/api/sales-orders",
                "{\"customerName\":\"Kofi\",\"customerPhone\":\"024\",\"customerAddress\":\"Accra\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":1}]}", ta);

        // A 看自己的订单 -> 200/code=0
        mvc.perform(get("/api/sales-orders/" + orderA).header("Authorization", ta))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(orderA));

        // B 看 A 的订单 -> NOT_FOUND(404)，且无 data
        String tb = login("detB_" + ts, "Test@123");
        mvc.perform(get("/api/sales-orders/" + orderA).header("Authorization", tb))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 超管（通配 list:all）看 A 的订单 -> 200/code=0
        mvc.perform(get("/api/sales-orders/" + orderA).header("Authorization", su))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(orderA));
    }
}
