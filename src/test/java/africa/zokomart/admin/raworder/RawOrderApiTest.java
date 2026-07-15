package africa.zokomart.admin.raworder;

import africa.zokomart.admin.module.raworder.entity.RawOrder;
import africa.zokomart.admin.module.raworder.mapper.RawOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
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
 * 原始订单 CSV 导入集成测试：尽力导入 + 逐行报告 + 列表查询；表头缺列/空文件整体拒绝。
 * 以超管 token 操作；测试数据按唯一电话号码清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RawOrderApiTest {

    static final String HEADER =
            "date,brand,price,customer_name,city,address,telephone,product_name,product_code,quantity,status,cod,freight,balance";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    RawOrderMapper rawOrderMapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "orders.csv", "text/csv",
                ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void import_best_effort_then_page_query() throws Exception {
        String t = token();
        String tel = "0555" + System.currentTimeMillis();

        // 行号（表头=1）：2 好 / 3 坏状态 / 4 坏日期 / 5 数量 0 / 6 缺 customer_name / 7 负 cod / 8 好
        String body = HEADER + "\n"
                + "2026-07-01,Hisense,1200.00,Ama Mensah,Accra,12 High St," + tel + ",Fridge 201,RAW-A,2,PAID,1200.00,50.00,0.00\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,RAW-B,1,SHIPPED,100.00,10.00,0.00\n"
                + "07/01/2026,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,RAW-C,1,PAID,100.00,10.00,0.00\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,RAW-D,0,PAID,100.00,10.00,0.00\n"
                + "2026-07-01,Hisense,100.00,,Accra,addr," + tel + ",TV 32,RAW-E,1,PAID,100.00,10.00,0.00\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,RAW-F,1,PAID,-5.00,10.00,0.00\n"
                + "2026-07-02,Nasco,300.00,Esi Boateng,Kumasi,5 Low Rd," + tel + ",Blender X,RAW-G,1,RECIPIENT_REFUSED,0.00,20.00,300.00\n";

        MvcResult r = mvc.perform(multipart("/api/raw-orders/import").file(csv(body))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.success").value(2))
                .andExpect(jsonPath("$.data.failed").value(5))
                .andReturn();
        String json = r.getResponse().getContentAsString();
        for (int row = 3; row <= 7; row++) {
            Assertions.assertTrue(json.contains("\"row\":" + row), "missing error row " + row);
        }

        // 列表：按电话关键字查 → 2 条；再叠加状态过滤 → 1 条
        mvc.perform(get("/api/raw-orders").header("Authorization", t)
                        .param("keyword", tel))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/raw-orders").header("Authorization", t)
                        .param("keyword", tel).param("status", "PAID"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productCode").value("RAW-A"));
        // 日期范围过滤：只含 07-02 → 1 条
        mvc.perform(get("/api/raw-orders").header("Authorization", t)
                        .param("keyword", tel)
                        .param("dateStart", "2026-07-02").param("dateEnd", "2026-07-02"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("RECIPIENT_REFUSED"));

        // 清理（无删除接口，直接走 mapper 逻辑删除）
        rawOrderMapper.delete(new LambdaQueryWrapper<RawOrder>().eq(RawOrder::getTelephone, tel));
    }

    @Test
    void import_blank_status_freight_balance_use_defaults() throws Exception {
        String t = token();
        String tel = "0777" + System.currentTimeMillis();
        // status / freight / balance 三列留空 → status 默认 NOT_DISPATCHED，freight/balance 默认 0.00
        String body = HEADER + "\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,RAW-DEF,1,,100.00,,\n";
        mvc.perform(multipart("/api/raw-orders/import").file(csv(body)).header("Authorization", t))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel))
                .andExpect(jsonPath("$.data.records[0].status").value("NOT_DISPATCHED"))
                .andExpect(jsonPath("$.data.records[0].freight").value(0.00))
                .andExpect(jsonPath("$.data.records[0].balance").value(0.00));

        rawOrderMapper.delete(new LambdaQueryWrapper<RawOrder>().eq(RawOrder::getTelephone, tel));
    }

    @Test
    void import_rejects_missing_header_column() throws Exception {
        String t = token();
        // 缺 balance 列 → 整文件拒绝 40009
        String header = HEADER.replace(",balance", "");
        mvc.perform(multipart("/api/raw-orders/import")
                        .file(csv(header + "\n2026-07-01,B,1,C,Accra,A,024,P,PC,1,PAID,1,1\n"))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void import_rejects_empty_file() throws Exception {
        String t = token();
        mvc.perform(multipart("/api/raw-orders/import")
                        .file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void import_rejects_too_many_rows() throws Exception {
        String t = token();
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < 1001; i++) {
            sb.append("2026-07-01,B,1,C,Accra,A,024,P,PC").append(i).append(",1,PAID,1,1,0\n");
        }
        mvc.perform(multipart("/api/raw-orders/import").file(csv(sb.toString()))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(40010)); // IMPORT_TOO_MANY_ROWS，整体拒绝不入库
    }

    /** 合法更新载荷；tel 用于查询定位与清理。 */
    private static String updateJson(String tel, String status) {
        return """
                {"orderDate":"2026-07-03","brand":"Nasco","price":150.50,
                 "customerName":"Ama Updated","city":"Kumasi","address":"new addr",
                 "telephone":"%s","productName":"TV 43","productCode":"UPD-NEW",
                 "quantity":2,"status":"%s","cod":0.00,"freight":12.00,"balance":150.50}
                """.formatted(tel, status);
    }

    @Test
    void update_row_then_get_reflects_changes() throws Exception {
        String t = token();
        String tel = "0666" + System.currentTimeMillis();
        String body = HEADER + "\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,UPD-A,1,PAID,100.00,10.00,0.00\n";
        mvc.perform(multipart("/api/raw-orders/import").file(csv(body)).header("Authorization", t))
                .andExpect(jsonPath("$.data.success").value(1));

        MvcResult r = mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel))
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        long id = om.readTree(r.getResponse().getContentAsString()).at("/data/records/0/id").asLong();

        mvc.perform(put("/api/raw-orders/" + id).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson(tel, "RECIPIENT_REFUSED")))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel))
                .andExpect(jsonPath("$.data.records[0].orderDate").value("2026-07-03"))
                .andExpect(jsonPath("$.data.records[0].brand").value("Nasco"))
                .andExpect(jsonPath("$.data.records[0].productCode").value("UPD-NEW"))
                .andExpect(jsonPath("$.data.records[0].quantity").value(2))
                .andExpect(jsonPath("$.data.records[0].status").value("RECIPIENT_REFUSED"))
                .andExpect(jsonPath("$.data.records[0].balance").value(150.50));

        rawOrderMapper.delete(new LambdaQueryWrapper<RawOrder>().eq(RawOrder::getTelephone, tel));
    }

    @Test
    void update_blank_status_freight_balance_use_defaults() throws Exception {
        String t = token();
        String tel = "0668" + System.currentTimeMillis();
        String body = HEADER + "\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,UPD-C,1,PAID,100.00,10.00,50.00\n";
        mvc.perform(multipart("/api/raw-orders/import").file(csv(body)).header("Authorization", t))
                .andExpect(jsonPath("$.data.success").value(1));
        MvcResult r = mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel)).andReturn();
        long id = om.readTree(r.getResponse().getContentAsString()).at("/data/records/0/id").asLong();

        // status/freight/balance 三字段省略 → 回落默认（NOT_DISPATCHED / 0.00 / 0.00）
        String payload = """
                {"orderDate":"2026-07-03","brand":"Nasco","price":150.50,
                 "customerName":"Ama Updated","city":"Kumasi","address":"new addr",
                 "telephone":"%s","productName":"TV 43","productCode":"UPD-NEW",
                 "quantity":2,"cod":0.00}
                """.formatted(tel);
        mvc.perform(put("/api/raw-orders/" + id).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel))
                .andExpect(jsonPath("$.data.records[0].status").value("NOT_DISPATCHED"))
                .andExpect(jsonPath("$.data.records[0].freight").value(0.00))
                .andExpect(jsonPath("$.data.records[0].balance").value(0.00));

        rawOrderMapper.delete(new LambdaQueryWrapper<RawOrder>().eq(RawOrder::getTelephone, tel));
    }

    @Test
    void update_rejects_bad_status_blank_field_and_unknown_id() throws Exception {
        String t = token();
        String tel = "0667" + System.currentTimeMillis();
        String body = HEADER + "\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,UPD-B,1,PAID,100.00,10.00,0.00\n";
        mvc.perform(multipart("/api/raw-orders/import").file(csv(body)).header("Authorization", t))
                .andExpect(jsonPath("$.data.success").value(1));
        MvcResult r = mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel)).andReturn();
        long id = om.readTree(r.getResponse().getContentAsString()).at("/data/records/0/id").asLong();

        // 非法状态 → 400
        mvc.perform(put("/api/raw-orders/" + id).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson(tel, "SHIPPED")))
                .andExpect(jsonPath("$.code").value(400));

        // 空 customerName → 400（jakarta 校验）
        mvc.perform(put("/api/raw-orders/" + id).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(tel, "PAID").replace("\"Ama Updated\"", "\"\"")))
                .andExpect(jsonPath("$.code").value(400));

        // 不存在 id → 404
        mvc.perform(put("/api/raw-orders/999999999999999").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson(tel, "PAID")))
                .andExpect(jsonPath("$.code").value(404));

        // 失败的更新均未落库：状态仍是导入时的 PAID、姓名未变
        mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel))
                .andExpect(jsonPath("$.data.records[0].status").value("PAID"))
                .andExpect(jsonPath("$.data.records[0].customerName").value("Kofi"));

        rawOrderMapper.delete(new LambdaQueryWrapper<RawOrder>().eq(RawOrder::getTelephone, tel));
    }
}
