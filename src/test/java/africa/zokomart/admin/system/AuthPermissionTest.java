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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1 集成测试：超管登录、用户信息、未登录拦截、权限不足拦截。
 * 依赖启动时自动种入的超管账号 superadmin / Admin@123，以及本地 MySQL + Redis。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthPermissionTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        return root.at("/data/token").asText();
    }

    @Test
    void superadmin_login_and_user_info() throws Exception {
        String token = login("superadmin", "Admin@123");
        assertThat(token).isNotBlank();

        mvc.perform(get("/api/auth/user-info").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.isSuper").value(1))
                .andExpect(jsonPath("$.data.permissions[0]").value("*"));
    }

    @Test
    void wrong_password_is_rejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"superadmin\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void protected_endpoint_requires_login() throws Exception {
        mvc.perform(get("/api/system/users"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void normal_user_without_permission_is_forbidden() throws Exception {
        String superToken = login("superadmin", "Admin@123");
        String username = "tester_" + System.currentTimeMillis();

        // 超管创建一个无角色的普通用户
        MvcResult created = mvc.perform(post("/api/system/users")
                        .header("Authorization", superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Test@123\",\"status\":1}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long newUserId = om.readTree(created.getResponse().getContentAsString()).at("/data").asLong();

        // 该用户登录后访问需要 system:user:list 权限的接口，应 403
        String token = login(username, "Test@123");
        mvc.perform(get("/api/system/users").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(403));

        // 清理：超管删除该测试用户
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/system/users/" + newUserId)
                        .header("Authorization", superToken))
                .andExpect(jsonPath("$.code").value(0));
    }
}
