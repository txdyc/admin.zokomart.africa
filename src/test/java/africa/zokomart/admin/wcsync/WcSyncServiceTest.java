package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WcProductRef;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClientFactory;
import africa.zokomart.admin.module.wcsync.config.WcSyncProperties;
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

    @MockBean WooCommerceClientFactory clientFactory;
    WooCommerceClient wc = mock(WooCommerceClient.class);

    static WcSyncProperties.WcSite site(String code) {
        WcSyncProperties.WcSite s = new WcSyncProperties.WcSite();
        s.setCode(code);
        s.setName(code.equals("zokomart") ? "ZokoMart" : "KianoSmart");
        s.setBaseUrl("https://" + code + ".example");
        s.setConsumerKey("ck");
        s.setConsumerSecret("cs");
        return s;
    }

    /** 把工厂 mock 指向单站点 zokomart + mock 客户端 wc。 */
    private void stubSingleSite() {
        WcSyncProperties.WcSite zoko = site("zokomart");
        when(clientFactory.sites()).thenReturn(List.of(zoko));
        when(clientFactory.site("zokomart")).thenReturn(zoko);
        when(clientFactory.forSite("zokomart")).thenReturn(wc);
    }

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
    private long newJob(long supplierId, List<Long> brandIds, int total, String siteCode) {
        WcSyncJob job = new WcSyncJob();
        job.setSupplierId(supplierId);
        job.setSiteCode(siteCode);
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

        stubSingleSite();
        when(wc.ensureBrand(any())).thenReturn(500L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(new WcProductRef(9001L, 7001L));
        when(wc.updateProduct(anyLong(), any())).thenReturn(new WcProductRef(9001L, 7001L));

        // 首次：create，带 imageSrc
        long job1 = newJob(supplierId, List.of(brandId), 1, "zokomart");
        wcSyncService.runSync(job1, supplierId, List.of(brandId), "zokomart");
        org.mockito.ArgumentCaptor<WcProduct> c1 = org.mockito.ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).createProduct(c1.capture());
        assertEquals("http://img/x.jpg", c1.getValue().getImageSrc());   // 首次传 src
        WcSyncJob j1 = jobMapper.selectById(job1);
        assertEquals(WcSyncJobStatus.SUCCESS, j1.getStatus());
        assertEquals(1, j1.getCreatedCount());

        // 再次：图源未变 → update 不传 images（imageSrc=null）
        long job2 = newJob(supplierId, List.of(brandId), 1, "zokomart");
        wcSyncService.runSync(job2, supplierId, List.of(brandId), "zokomart");
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

        stubSingleSite();
        when(wc.ensureBrand(any())).thenReturn(500L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(new WcProductRef(9100L, null));

        long job = newJob(supplierId, List.of(brandId), 1, "zokomart");
        wcSyncService.runSync(job, supplierId, List.of(brandId), "zokomart");

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
        stubSingleSite();
        assertTrue(lock.tryAcquire("zokomart"));   // 预占 zokomart 站点锁
        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> wcSyncService.startSync(1L, List.of(1L), List.of("zokomart")));
            assertEquals(ResultCode.WC_SYNC_RUNNING.getCode(), ex.getCode()); // 业务码 40016
        } finally {
            lock.release("zokomart");
        }
    }

    @Test
    void start_rejects_when_no_site_configured() {
        when(clientFactory.sites()).thenReturn(List.of());
        assertThrows(RuntimeException.class,
                () -> wcSyncService.startSync(1L, List.of(1L), null));
    }

    @Test
    void start_rejects_unknown_site_code() {
        stubSingleSite();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> wcSyncService.startSync(1L, List.of(1L), List.of("nope")));
        assertEquals(ResultCode.WC_NOT_CONFIGURED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("nope"));
    }

    /** 等任务跑到终态，避免异步 runSync 仍持有站点锁而影响后续用例。 */
    private void awaitTerminal(long jobId) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (!WcSyncJobStatus.RUNNING.equals(jobMapper.selectById(jobId).getStatus())) return;
            Thread.sleep(50);
        }
        fail("任务未在预期时间内结束: " + jobId);
    }

    @Test
    void omitting_site_codes_defaults_to_all_configured_sites() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"WC_SupA_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"WC_BrandA_" + ts + "\",\"sort\":1,\"status\":1}", t);
        // 故意不建产品：runSync 空转，异步派发不产生 WC 调用

        WcSyncProperties.WcSite unconfigured = new WcSyncProperties.WcSite();
        unconfigured.setCode("draftsite");      // 缺 base-url/密钥
        unconfigured.setName("DraftSite");
        when(clientFactory.sites())
                .thenReturn(List.of(site("zokomart"), site("kianosmart"), unconfigured));
        when(clientFactory.site(anyString())).thenReturn(site("zokomart"));
        when(clientFactory.forSite(anyString())).thenReturn(wc);

        // siteCodes 省略（null）→ 落到"全部已配置站点"
        List<Long> jobIds = wcSyncService.startSync(supplierId, List.of(brandId), null);

        assertEquals(2, jobIds.size());   // 未配置的 draftsite 被排除，不报错
        List<String> codes = jobIds.stream()
                .map(id -> jobMapper.selectById(id).getSiteCode()).sorted().toList();
        assertEquals(List.of("kianosmart", "zokomart"), codes);   // 每站一个 job

        for (long id : jobIds) awaitTerminal(id);
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }

    @Test
    void sites_endpoint_exposes_code_name_and_configured_flag() throws Exception {
        String t = token();
        WcSyncProperties.WcSite kiano = new WcSyncProperties.WcSite();
        kiano.setCode("kianosmart");            // 缺 base-url/密钥 → configured=false
        kiano.setName("KianoSmart");
        when(clientFactory.sites()).thenReturn(List.of(site("zokomart"), kiano));

        mvc.perform(get("/api/wc-sync/sites").header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("zokomart"))
                .andExpect(jsonPath("$.data[0].name").value("ZokoMart"))
                .andExpect(jsonPath("$.data[0].configured").value(true))
                .andExpect(jsonPath("$.data[1].code").value("kianosmart"))
                .andExpect(jsonPath("$.data[1].name").value("KianoSmart"))
                // 前端据此置灰不可选，必须如实反映"未配置"
                .andExpect(jsonPath("$.data[1].configured").value(false));
    }

    @Test
    void start_rejects_unconfigured_site() {
        WcSyncProperties.WcSite bad = new WcSyncProperties.WcSite();
        bad.setCode("kianosmart");   // 缺 base-url/密钥
        bad.setName("KianoSmart");
        when(clientFactory.sites()).thenReturn(List.of(site("zokomart"), bad));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> wcSyncService.startSync(1L, List.of(1L), List.of("kianosmart")));
        assertEquals(ResultCode.WC_NOT_CONFIGURED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("KianoSmart"));
    }
}
