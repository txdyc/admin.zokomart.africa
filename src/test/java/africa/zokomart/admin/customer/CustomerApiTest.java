package africa.zokomart.admin.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 客户列表聚合集成测试：直插 sales_order，验证按手机号去重的聚合与 keyword 过滤。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    JdbcTemplate jdbc;

    private final long base = System.currentTimeMillis();
    private final String phoneA = "024TESTA" + (base % 100000);
    private final String phoneB = "024TESTB" + (base % 100000);

    private void insertOrder(long id, String no, String name, String phone, String addr,
                             String actualAmount, String createTime) {
        jdbc.update("INSERT INTO sales_order (id, order_no, customer_name, customer_phone, customer_address, "
                        + "actual_amount, total_amount, create_time, deleted, version) "
                        + "VALUES (?,?,?,?,?,?,?,?,0,0)",
                id, no, name, phone, addr, actualAmount, actualAmount, createTime);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM sales_order WHERE customer_phone IN (?,?)", phoneA, phoneB);
    }

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}")).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    @Test
    void aggregates_customers_by_phone() throws Exception {
        String t = token();
        // phoneA: 2 单（最近一单姓名/地址 = Ama K./Tema），phoneB: 1 单
        insertOrder(base + 1, "SOT" + base + "1", "Ama",    phoneA, "Osu",  "100.00", "2026-06-01 10:00:00");
        insertOrder(base + 3, "SOT" + base + "3", "Ama K.", phoneA, "Tema", "250.00", "2026-06-03 10:00:00");
        insertOrder(base + 2, "SOT" + base + "2", "Kofi",   phoneB, "Accra","80.00",  "2026-06-02 10:00:00");

        // 查 phoneA：去重为 1 个客户，订单数 2、累计 350、姓名/地址取最近一单
        MvcResult r = mvc.perform(get("/api/customers").header("Authorization", t).param("keyword", phoneA))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].customerPhone").value(phoneA))
                .andExpect(jsonPath("$.data.records[0].orderCount").value(2))
                .andExpect(jsonPath("$.data.records[0].customerName").value("Ama K."))
                .andExpect(jsonPath("$.data.records[0].customerAddress").value("Tema"))
                .andReturn();
        double total = om.readTree(r.getResponse().getContentAsString()).at("/data/records/0/totalAmount").asDouble();
        org.junit.jupiter.api.Assertions.assertEquals(350.0, total, 0.001);

        // keyword=Kofi 命中 phoneB 这一个客户
        mvc.perform(get("/api/customers").header("Authorization", t).param("keyword", "Kofi"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].customerPhone").value(phoneB))
                .andExpect(jsonPath("$.data.records[0].orderCount").value(1));
    }

    @Test
    void requires_login() throws Exception {
        mvc.perform(get("/api/customers")).andExpect(jsonPath("$.code").value(401));
    }
}
