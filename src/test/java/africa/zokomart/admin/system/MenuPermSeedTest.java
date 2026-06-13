package africa.zokomart.admin.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Phase 8 Task 8.1：验证 V7 种入的「角色模板 + 菜单/权限码」可直接组角色并形成权限边界。
 * <p>以超管登录，按 code 取到种子角色「采购员(BUYER)」，赋给新建用户，再以该用户登录，
 * 断言其聚合权限码恰为采购员职责范围：含 purchase:plan:create，不含 inventory:edit / approve；
 * 并实地校验 inventory:edit 接口对其 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MenuPermSeedTest {

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

    private long roleIdByCode(String token, String code) throws Exception {
        MvcResult r = mvc.perform(get("/api/system/roles").header("Authorization", token).param("keyword", code))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        for (JsonNode n : om.readTree(r.getResponse().getContentAsString()).at("/data/records")) {
            if (code.equals(n.get("code").asText())) {
                return n.get("id").asLong();
            }
        }
        throw new AssertionError("种子角色未找到: " + code);
    }

    @Test
    void buyer_role_template_grants_purchase_not_inventory() throws Exception {
        String su = login("superadmin", "Admin@123");

        // 种子角色「采购员」应存在且 id == 901
        long buyerRoleId = roleIdByCode(su, "BUYER");
        assertThat(buyerRoleId).isEqualTo(901L);

        // 超管建用户并赋采购员角色
        String uname = "buyer_" + System.nanoTime();
        MvcResult created = mvc.perform(post("/api/system/users").header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + uname + "\",\"password\":\"Test@123\",\"status\":1}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long userId = om.readTree(created.getResponse().getContentAsString()).at("/data").asLong();
        mvc.perform(put("/api/system/users/" + userId + "/roles").header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[" + buyerRoleId + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        // 以采购员登录，聚合权限码边界
        String buyer = login(uname, "Test@123");
        MvcResult info = mvc.perform(get("/api/auth/user-info").header("Authorization", buyer))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Set<String> perms = new HashSet<>();
        for (JsonNode n : om.readTree(info.getResponse().getContentAsString()).at("/data/permissions")) {
            perms.add(n.asText());
        }
        assertThat(perms)
                .contains("supplierProduct:create", "purchase:plan:create", "purchase:plan:submit",
                        "purchase:order:pay", "purchase:order:confirm")
                // 职责分离：采购员不含审批，也不含库存编辑/系统管理
                .doesNotContain("purchase:plan:approve", "inventory:edit", "system:user:list");

        // 实地校验：采购员调库存调整接口应 403（无 inventory:edit）
        mvc.perform(put("/api/inventory/stocks/1").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(jsonPath("$.code").value(403));

        // 清理本测试建立的用户
        mvc.perform(delete("/api/system/users/" + userId).header("Authorization", su))
                .andExpect(jsonPath("$.code").value(0));
    }
}
