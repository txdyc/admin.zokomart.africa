package africa.zokomart.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 启动冒烟测试：验证全上下文可加载且 /api/ping 返回统一 Result。
 * 需本地 MySQL（zokomart_admin 库）与 Redis 可用。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PingTest {

    @Autowired
    MockMvc mvc;

    @Test
    void ping_returns_pong() throws Exception {
        mvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("pong"));
    }
}
