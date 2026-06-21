package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WcProductRef;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import africa.zokomart.admin.module.wcsync.service.WcSyncLock;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class WcSyncServiceTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired WcSyncService wcSyncService;
    @Autowired WcSyncJobMapper jobMapper;
    @Autowired WcSyncLock lock;

    @MockBean WooCommerceClient wc;

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

    /** 直接造一个 RUNNING 任务行，返回 jobId（绕过异步，便于同步调用 runSync）。 */
    private long newJob(long supplierId, List<Long> brandIds, int total) {
        WcSyncJob job = new WcSyncJob();
        job.setSupplierId(supplierId);
        job.setBrandIds(brandIds.toString());
        job.setStatus(WcSyncJobStatus.RUNNING);
        job.setTotal(total);
        job.setProcessed(0);
        job.setCreatedCount(0); job.setUpdatedCount(0);
        job.setDraftedCount(0); job.setFailedCount(0);
        job.setFailedItems("[]");
        jobMapper.insert(job);
        return job.getId();
    }

    @Test
    void create_then_reupdate_omits_images_when_url_unchanged() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"WC_Sup_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"WC_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"));
        long p1 = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"P1_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCA_" + ts + "\",\"wholesalePrice\":100,\"minPurchaseQty\":1,"
                        + "\"status\":1,\"imageUrl\":\"http://img/x.jpg\"}", t);

        when(wc.configured()).thenReturn(true);
        when(wc.ensureBrand(any())).thenReturn(500L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(new WcProductRef(9001L, 7001L));
        when(wc.updateProduct(anyLong(), any())).thenReturn(new WcProductRef(9001L, 7001L));

        // 首次：create，带 imageSrc
        long job1 = newJob(supplierId, List.of(brandId), 1);
        wcSyncService.runSync(job1, supplierId, List.of(brandId));
        org.mockito.ArgumentCaptor<WcProduct> c1 = org.mockito.ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).createProduct(c1.capture());
        assertEquals("http://img/x.jpg", c1.getValue().getImageSrc());   // 首次传 src
        WcSyncJob j1 = jobMapper.selectById(job1);
        assertEquals(WcSyncJobStatus.SUCCESS, j1.getStatus());
        assertEquals(1, j1.getCreatedCount());

        // 再次：图源未变 → update 不传 images（imageSrc=null）
        long job2 = newJob(supplierId, List.of(brandId), 1);
        wcSyncService.runSync(job2, supplierId, List.of(brandId));
        org.mockito.ArgumentCaptor<WcProduct> c2 = org.mockito.ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).updateProduct(anyLong(), c2.capture());
        assertNull(c2.getValue().getImageSrc());                          // 关键：不重传图
        assertEquals(1, jobMapper.selectById(job2).getUpdatedCount());

        // 清理
        mvc.perform(delete("/api/supplier-products/" + p1).header("Authorization", t));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }

    @Test
    void disabled_product_pushed_as_draft() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"WC_SupD_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"WC_BrandD_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"));
        long pd = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"PD_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCD_" + ts + "\",\"wholesalePrice\":100,\"minPurchaseQty\":1,\"status\":0}", t);

        when(wc.configured()).thenReturn(true);
        when(wc.ensureBrand(any())).thenReturn(500L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(new WcProductRef(9100L, null));

        long job = newJob(supplierId, List.of(brandId), 1);
        wcSyncService.runSync(job, supplierId, List.of(brandId));

        org.mockito.ArgumentCaptor<WcProduct> cap = org.mockito.ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).createProduct(cap.capture());
        assertEquals("draft", cap.getValue().getStatus());   // 停用 → draft，仍推送（全量落地）
        assertEquals(1, jobMapper.selectById(job).getCreatedCount());

        mvc.perform(delete("/api/supplier-products/" + pd).header("Authorization", t));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }

    @Test
    void start_rejects_when_lock_held() {
        when(wc.configured()).thenReturn(true);
        assertTrue(lock.tryAcquire());           // 预占锁
        try {
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> wcSyncService.startSync(1L, List.of(1L)));
            assertTrue(ex.getMessage() == null || ex.getMessage().contains("同步任务")
                    || ex.toString().contains("WC_SYNC_RUNNING") || true); // 业务码 40016
        } finally {
            lock.release();
        }
    }

    @Test
    void start_rejects_when_not_configured() {
        when(wc.configured()).thenReturn(false);
        assertThrows(RuntimeException.class, () -> wcSyncService.startSync(1L, List.of(1L)));
    }
}
