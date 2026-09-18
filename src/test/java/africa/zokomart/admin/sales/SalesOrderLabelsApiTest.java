package africa.zokomart.admin.sales;

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

/** Task B1：销售订单面单数据端点 GET /api/sales-orders/labels（字段、当天过滤、本人范围）。 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderLabelsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String login(String u, String p) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    @Test
    void labels_returns_today_pending_orders_with_label_fields() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();

        long supplierId = postForId("/api/suppliers", "{\"name\":\"LBL_Sup_" + ts + "\",\"status\":1}", su);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"LBL_Prod_" + ts
                        + "\",\"productCode\":\"LBLC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
                .andExpect(jsonPath("$.code").value(0));

        long orderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Ama\",\"customerPhone\":\"024999\",\"customerAddress\":\"Kumasi\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":3}]}", su);

        MvcResult res = mvc.perform(get("/api/sales-orders/labels").header("Authorization", su))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode data = om.readTree(res.getResponse().getContentAsString()).at("/data");
        assertThat(data.isArray()).isTrue();

        JsonNode mine = null;
        for (JsonNode n : data) {
            if (n.get("id").asLong() == orderId) { mine = n; break; }
        }
        assertThat(mine).as("今日新建订单应在面单结果中").isNotNull();
        assertThat(mine.get("customerName").asText()).isEqualTo("Ama");
        assertThat(mine.get("customerPhone").asText()).isEqualTo("024999");
        assertThat(mine.get("customerAddress").asText()).isEqualTo("Kumasi");
        assertThat(mine.get("totalQty").asInt()).isEqualTo(3);
        assertThat(mine.get("totalAmount").asDouble()).isEqualTo(600.0);
        assertThat(mine.has("orderNo")).isTrue();
    }

    @Test
    void labels_excludes_other_dates() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();

        long supplierId = postForId("/api/suppliers", "{\"name\":\"LBLD_Sup_" + ts + "\",\"status\":1}", su);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"LBLD_Prod_" + ts
                        + "\",\"productCode\":\"LBLDC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
                .andExpect(jsonPath("$.code").value(0));

        long orderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Kofi\",\"customerPhone\":\"024000\",\"customerAddress\":\"Tema\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":1}]}", su);

        // 用远期日期查询：今日新建订单不应出现
        MvcResult res = mvc.perform(get("/api/sales-orders/labels")
                        .header("Authorization", su).param("date", "2000-01-01"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode data = om.readTree(res.getResponse().getContentAsString()).at("/data");
        assertThat(data.isArray()).isTrue();
        for (JsonNode n : data) {
            assertThat(n.get("id").asLong()).isNotEqualTo(orderId);
        }
    }

    /**
     * labels() 过滤口径是 order_date（业务日期），不是 create_time 范围：面单打印面板没有
     * 日期选择器，永远按“今天”请求，一张 orderDate 是过去日期的历史/导入订单只能靠直接带
     * date 参数请求到；这里验证按其真实 order_date 请求时能查到，不依赖 create_time 是否
     * 恰好也落在同一天。
     */
    @Test
    void labels_returns_order_with_past_order_date_when_that_date_is_requested() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();

        long supplierId = postForId("/api/suppliers", "{\"name\":\"LBLP_Sup_" + ts + "\",\"status\":1}", su);
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"LBLP_Prod_" + ts
                        + "\",\"productCode\":\"LBLPC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
        mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
                .andExpect(jsonPath("$.code").value(0));

        String pastDate = "2026-01-05";
        long orderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Past\",\"customerPhone\":\"024777\",\"customerAddress\":\"Takoradi\","
                        + "\"orderDate\":\"" + pastDate + "\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":2}]}", su);

        MvcResult res = mvc.perform(get("/api/sales-orders/labels")
                        .header("Authorization", su).param("date", pastDate))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode data = om.readTree(res.getResponse().getContentAsString()).at("/data");
        assertThat(data.isArray()).isTrue();

        boolean found = false;
        for (JsonNode n : data) {
            if (n.get("id").asLong() == orderId) {
                found = true;
                break;
            }
        }
        assertThat(found).as("按历史订单真实 order_date 请求应能查到，即便它早已不在“今天”").isTrue();
    }

    /**
     * 贴纸要印 product code，而一个订单可能含多个商品：面单出参必须带明细，前端才能
     * 按明细逐件展开（A×1、B×2 → 第 1 张印 A，第 2/3 张印 B）。只给订单级的 totalQty
     * 无法区分每张贴纸对应哪件货。明细顺序须与下单顺序一致，否则贴纸和实物对不上。
     */
    @Test
    void labels_include_items_with_product_code_for_multi_product_order() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();

        long supplierId = postForId("/api/suppliers", "{\"name\":\"LBLI_Sup_" + ts + "\",\"status\":1}", su);
        long spA = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"LBLI_ProdA_" + ts
                        + "\",\"productCode\":\"LBLIA_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
        long spB = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"LBLI_ProdB_" + ts
                        + "\",\"productCode\":\"LBLIB_" + ts + "\",\"wholesalePrice\":50,\"retailPrice\":80,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);
        for (long spId : new long[] {spA, spB}) {
            mvc.perform(put("/api/inventory/stocks/" + spId).header("Authorization", su)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}"))
                    .andExpect(jsonPath("$.code").value(0));
        }

        long orderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Yaa\",\"customerPhone\":\"024555\",\"customerAddress\":\"Cape Coast\",\"items\":[{\"supplierProductId\":"
                        + spA + ",\"qty\":1},{\"supplierProductId\":" + spB + ",\"qty\":2}]}", su);

        MvcResult res = mvc.perform(get("/api/sales-orders/labels").header("Authorization", su))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode data = om.readTree(res.getResponse().getContentAsString()).at("/data");

        JsonNode mine = null;
        for (JsonNode n : data) {
            if (n.get("id").asLong() == orderId) { mine = n; break; }
        }
        assertThat(mine).as("今日新建订单应在面单结果中").isNotNull();
        assertThat(mine.get("totalQty").asInt()).isEqualTo(3);

        JsonNode items = mine.get("items");
        assertThat(items).as("面单出参须带明细，贴纸才能逐件印 product code").isNotNull();
        assertThat(items.isArray()).isTrue();
        assertThat(items).hasSize(2);

        assertThat(items.get(0).get("productCode").asText()).isEqualTo("LBLIA_" + ts);
        assertThat(items.get(0).get("productName").asText()).isEqualTo("LBLI_ProdA_" + ts);
        assertThat(items.get(0).get("qty").asInt()).isEqualTo(1);

        assertThat(items.get(1).get("productCode").asText()).isEqualTo("LBLIB_" + ts);
        assertThat(items.get(1).get("productName").asText()).isEqualTo("LBLI_ProdB_" + ts);
        assertThat(items.get(1).get("qty").asInt()).isEqualTo(2);
    }
}
