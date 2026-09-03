package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.ad.entity.AdImageSiteMedia;
import africa.zokomart.admin.module.ad.entity.AdProductImage;
import africa.zokomart.admin.module.ad.mapper.AdImageSiteMediaMapper;
import africa.zokomart.admin.module.ad.mapper.AdProductImageMapper;
import africa.zokomart.admin.module.wcsync.client.WcImage;
import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WcProductRef;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClientFactory;
import africa.zokomart.admin.module.wcsync.config.WcSyncProperties;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.entity.WcSyncRecord;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncRecordMapper;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 多站点隔离集成测试：站点映射隔离 / 每站图片幂等 / 广告图 media id 隔离 / 每站倍率。
 */
@SpringBootTest(properties = "app.wc.public-file-base-url=http://admin.example")
@AutoConfigureMockMvc
class WcMultiSiteSyncTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired WcSyncService wcSyncService;
    @Autowired WcSyncJobMapper jobMapper;
    @Autowired WcSyncRecordMapper recordMapper;
    @Autowired AdProductImageMapper adImageMapper;
    @Autowired AdImageSiteMediaMapper adMediaMapper;

    @MockBean WooCommerceClientFactory clientFactory;
    WooCommerceClient wcZoko = mock(WooCommerceClient.class);
    WooCommerceClient wcKiano = mock(WooCommerceClient.class);

    private void stubTwoSites() {
        WcSyncProperties.WcSite zoko = WcSyncServiceTest.site("zokomart");
        WcSyncProperties.WcSite kiano = WcSyncServiceTest.site("kianosmart");
        kiano.setRegularMultiplier(new java.math.BigDecimal("2.0"));   // 站点覆盖倍率
        when(clientFactory.sites()).thenReturn(List.of(zoko, kiano));
        when(clientFactory.site("zokomart")).thenReturn(zoko);
        when(clientFactory.site("kianosmart")).thenReturn(kiano);
        when(clientFactory.forSite("zokomart")).thenReturn(wcZoko);
        when(clientFactory.forSite("kianosmart")).thenReturn(wcKiano);
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

    private long newJob(long supplierId, List<Long> brandIds, String siteCode) {
        WcSyncJob job = new WcSyncJob();
        job.setSupplierId(supplierId);
        job.setSiteCode(siteCode);
        job.setBrandIds(brandIds.toString());
        job.setStatus(WcSyncJobStatus.RUNNING);
        job.setTotal(1); job.setProcessed(0);
        job.setCreatedCount(0); job.setUpdatedCount(0);
        job.setDraftedCount(0); job.setFailedCount(0);
        job.setFailedItems("[]");
        jobMapper.insert(job);
        return job.getId();
    }

    private WcSyncRecord findRecord(Long spId, String siteCode) {
        return recordMapper.selectOne(Wrappers.<WcSyncRecord>lambdaQuery()
                .eq(WcSyncRecord::getSupplierProductId, spId)
                .eq(WcSyncRecord::getSiteCode, siteCode));
    }

    private AdImageSiteMedia findMedia(Long adImageId, String siteCode) {
        return adMediaMapper.selectOne(Wrappers.<AdImageSiteMedia>lambdaQuery()
                .eq(AdImageSiteMedia::getAdImageId, adImageId)
                .eq(AdImageSiteMedia::getSiteCode, siteCode));
    }

    @Test
    void site_isolation_and_per_site_multiplier_and_ad_media_isolation() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"WCMS_Sup_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"WCMS_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"));
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"WCMS_P_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCMS_" + ts + "\",\"wholesalePrice\":100,\"minPurchaseQty\":1,"
                        + "\"status\":1,\"imageUrl\":\"http://img/multi.jpg\"}", t);

        AdProductImage ad = new AdProductImage();
        ad.setSupplierProductId(spId);
        ad.setFileUrl("/files/ad/multi1.png");
        ad.setSort(1);
        adImageMapper.insert(ad);

        stubTwoSites();
        for (WooCommerceClient c : List.of(wcZoko, wcKiano)) {
            when(c.ensureBrand(any())).thenReturn(500L);
            when(c.findProductIdBySku(any())).thenReturn(null);
        }
        when(wcZoko.createProduct(any())).thenReturn(new WcProductRef(9001L, 7001L, List.of(
                new WcImage(7001L, "https://zoko/main.jpg"),
                new WcImage(7002L, "https://zoko/ad.jpg"))));
        when(wcKiano.createProduct(any())).thenReturn(new WcProductRef(8001L, 6001L, List.of(
                new WcImage(6001L, "https://kiano/main.jpg"),
                new WcImage(6002L, "https://kiano/ad.jpg"))));
        when(wcZoko.getProduct(anyLong())).thenReturn(new africa.zokomart.admin.module.wcsync.client.WcProductDetail(9001L, "", List.of()));
        when(wcKiano.getProduct(anyLong())).thenReturn(new africa.zokomart.admin.module.wcsync.client.WcProductDetail(8001L, "", List.of()));

        // 1) 先同步 zokomart
        wcSyncService.runSync(newJob(supplierId, List.of(brandId), "zokomart"),
                supplierId, List.of(brandId), "zokomart");
        ArgumentCaptor<WcProduct> zokoCap = ArgumentCaptor.forClass(WcProduct.class);
        verify(wcZoko).createProduct(zokoCap.capture());
        assertEquals("175.00", zokoCap.getValue().getRegularPrice());   // 全局倍率 1.75
        WcSyncRecord zokoRec = findRecord(spId, "zokomart");
        assertNotNull(zokoRec);
        assertEquals(9001L, zokoRec.getWcProductId());
        assertEquals(7001L, zokoRec.getWcImageId());
        AdImageSiteMedia zokoMedia = findMedia(ad.getId(), "zokomart");
        assertNotNull(zokoMedia);
        assertEquals(7002L, zokoMedia.getWcMediaId());

        // 2) 再同步 kianosmart：映射/倍率/media 全部隔离
        wcSyncService.runSync(newJob(supplierId, List.of(brandId), "kianosmart"),
                supplierId, List.of(brandId), "kianosmart");
        ArgumentCaptor<WcProduct> kianoCap = ArgumentCaptor.forClass(WcProduct.class);
        verify(wcKiano).createProduct(kianoCap.capture());
        assertEquals("200.00", kianoCap.getValue().getRegularPrice());  // 站点覆盖倍率 2.0
        // 广告图以 src 上传（不得携带 zokomart 的 media id 7002）
        List<WcImage> kianoImgs = kianoCap.getValue().getImagesOverride();
        assertEquals("http://admin.example/files/ad/multi1.png", kianoImgs.get(1).src());
        assertNull(kianoImgs.get(1).id());
        WcSyncRecord kianoRec = findRecord(spId, "kianosmart");
        assertNotNull(kianoRec);
        assertEquals(8001L, kianoRec.getWcProductId());                 // 未被 zoko 覆盖，也未覆盖 zoko
        assertEquals(6001L, kianoRec.getWcImageId());
        assertEquals(9001L, findRecord(spId, "zokomart").getWcProductId());  // zoko 行原样
        AdImageSiteMedia kianoMedia = findMedia(ad.getId(), "kianosmart");
        assertNotNull(kianoMedia);
        assertEquals(6002L, kianoMedia.getWcMediaId());                 // 各站各的 media id

        // 3) 跨站之后再同步 zokomart：仍不重传 images（未被 kiano 污染）
        when(wcZoko.updateProduct(anyLong(), any())).thenReturn(new WcProductRef(9001L, 7001L, List.of(
                new WcImage(7001L, "https://zoko/main.jpg"),
                new WcImage(7002L, "https://zoko/ad.jpg"))));
        wcSyncService.runSync(newJob(supplierId, List.of(brandId), "zokomart"),
                supplierId, List.of(brandId), "zokomart");
        ArgumentCaptor<WcProduct> zokoUpd = ArgumentCaptor.forClass(WcProduct.class);
        verify(wcZoko).updateProduct(anyLong(), zokoUpd.capture());
        assertNull(zokoUpd.getValue().getImageSrc());                    // 关键：不重传主图
        assertEquals(7002L, zokoUpd.getValue().getImagesOverride().get(1).id());  // 广告图按本站 id 引用

        // 清理
        adImageMapper.deleteById(ad.getId());
        adMediaMapper.delete(Wrappers.<AdImageSiteMedia>lambdaQuery()
                .eq(AdImageSiteMedia::getAdImageId, ad.getId()));
        recordMapper.delete(Wrappers.<WcSyncRecord>lambdaQuery()
                .eq(WcSyncRecord::getSupplierProductId, spId));
        mvc.perform(delete("/api/supplier-products/" + spId).header("Authorization", t));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }
}
