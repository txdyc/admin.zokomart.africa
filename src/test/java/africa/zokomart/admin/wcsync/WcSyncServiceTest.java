package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.inventory.entity.InventoryStock;
import africa.zokomart.admin.module.inventory.mapper.InventoryStockMapper;
import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class WcSyncServiceTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    WcSyncService wcSyncService;
    @Autowired
    InventoryStockMapper inventoryStockMapper;

    @MockBean
    WooCommerceClient wc;

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
    void maps_price_stock_status_and_is_idempotent() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"WC_Sup_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"WC_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"))
                .andExpect(jsonPath("$.code").value(0));
        // p1: 未填零售价、批发价 100 -> 期望 170.00；库存 20
        long p1 = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"P1_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCA_" + ts + "\",\"wholesalePrice\":100,\"minPurchaseQty\":1,\"status\":1}", t);
        // p2: 已填零售价 200 -> 期望 200.00；无库存 -> 0
        long p2 = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"P2_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCB_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,\"minPurchaseQty\":1,\"status\":1}", t);
        InventoryStock st = new InventoryStock();
        st.setSupplierProductId(p1);
        st.setSupplierId(supplierId);
        st.setBrandId(brandId);
        st.setQuantity(20);
        inventoryStockMapper.insert(st);

        when(wc.configured()).thenReturn(true);
        when(wc.ensureCategory(any())).thenReturn(100L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(9001L);

        // 首次同步：两条都 create
        WcSyncResultVO r1 = wcSyncService.syncSupplierBrands(supplierId, List.of(brandId));
        org.junit.jupiter.api.Assertions.assertEquals(2, r1.getTotal());
        org.junit.jupiter.api.Assertions.assertEquals(2, r1.getCreated());
        org.junit.jupiter.api.Assertions.assertEquals(0, r1.getFailed());

        ArgumentCaptor<WcProduct> cap = ArgumentCaptor.forClass(WcProduct.class);
        verify(wc, times(2)).createProduct(cap.capture());
        WcProduct a = cap.getAllValues().stream().filter(x -> x.getSku().equals("WCA_" + ts)).findFirst().orElseThrow();
        WcProduct b = cap.getAllValues().stream().filter(x -> x.getSku().equals("WCB_" + ts)).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("170.00", a.getRegularPrice());
        org.junit.jupiter.api.Assertions.assertEquals(20, a.getStockQuantity());
        org.junit.jupiter.api.Assertions.assertEquals("publish", a.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(100L, a.getCategoryId());
        org.junit.jupiter.api.Assertions.assertEquals("200.00", b.getRegularPrice());
        org.junit.jupiter.api.Assertions.assertEquals(0, b.getStockQuantity());

        // 再次同步：已有记录 -> 走 update（幂等）
        reset(wc);
        when(wc.configured()).thenReturn(true);
        when(wc.ensureCategory(any())).thenReturn(100L);
        WcSyncResultVO r2 = wcSyncService.syncSupplierBrands(supplierId, List.of(brandId));
        org.junit.jupiter.api.Assertions.assertEquals(2, r2.getUpdated());
        org.junit.jupiter.api.Assertions.assertEquals(0, r2.getCreated());
        verify(wc, never()).createProduct(any());
        verify(wc, times(2)).updateProduct(anyLong(), any());

        // 清理
        inventoryStockMapper.deleteById(st.getId());
        for (long id : new long[]{p1, p2}) {
            mvc.perform(delete("/api/supplier-products/" + id).header("Authorization", t));
        }
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }

    @Test
    void rejects_when_not_configured() {
        when(wc.configured()).thenReturn(false);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> wcSyncService.syncSupplierBrands(1L, List.of(1L)));
    }
}
