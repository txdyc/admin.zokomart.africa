package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.ad.entity.AdProductImage;
import africa.zokomart.admin.module.ad.mapper.AdProductImageMapper;
import africa.zokomart.admin.module.wcsync.client.*;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
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

@SpringBootTest(properties = "app.wc.public-file-base-url=http://admin.example")
@AutoConfigureMockMvc
class WcSyncAdImagesTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired WcSyncService wcSyncService;
    @Autowired WcSyncJobMapper jobMapper;
    @Autowired AdProductImageMapper adImageMapper;

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

    private long newJob(long supplierId, List<Long> brandIds) {
        WcSyncJob job = new WcSyncJob();
        job.setSupplierId(supplierId);
        job.setBrandIds(brandIds.toString());
        job.setStatus(WcSyncJobStatus.RUNNING);
        job.setTotal(1); job.setProcessed(0);
        job.setCreatedCount(0); job.setUpdatedCount(0);
        job.setDraftedCount(0); job.setFailedCount(0);
        job.setFailedItems("[]");
        jobMapper.insert(job);
        return job.getId();
    }

    @Test
    void ad_images_go_to_gallery_and_description_and_media_id_written_back() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers", "{\"name\":\"WCAD_Sup_" + ts + "\",\"status\":1}", t);
        long brandId = postForId("/api/brands", "{\"name\":\"WCAD_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"));
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"WCADP_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCAD_" + ts + "\",\"wholesalePrice\":100,\"minPurchaseQty\":1,"
                        + "\"status\":1,\"imageUrl\":\"http://img/main.jpg\"}", t);

        AdProductImage ad = new AdProductImage();
        ad.setSupplierProductId(spId);
        ad.setFileUrl("/files/ad/ad1.png");
        ad.setSort(1);
        adImageMapper.insert(ad);

        when(wc.configured()).thenReturn(true);
        when(wc.ensureBrand(any())).thenReturn(500L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(new WcProductRef(9001L, 7001L, List.of(
                new WcImage(7001L, "https://wc/main.jpg"),
                new WcImage(7002L, "https://wc/ad1.jpg"))));
        when(wc.getProduct(9001L)).thenReturn(new WcProductDetail(9001L, "", List.of()));

        wcSyncService.runSync(newJob(supplierId, List.of(brandId)), supplierId, List.of(brandId));

        // gallery：主图 src + 广告图公网 src
        ArgumentCaptor<WcProduct> cap = ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).createProduct(cap.capture());
        List<WcImage> sent = cap.getValue().getImagesOverride();
        assertNotNull(sent);
        assertEquals(2, sent.size());
        assertEquals("http://img/main.jpg", sent.get(0).src());
        assertEquals("http://admin.example/files/ad/ad1.png", sent.get(1).src());

        // 描述：标记区块 + WC 媒体 URL
        ArgumentCaptor<String> desc = ArgumentCaptor.forClass(String.class);
        verify(wc).updateProductDescription(eq(9001L), desc.capture());
        assertTrue(desc.getValue().contains("ZOKO-AD:START"));
        assertTrue(desc.getValue().contains("https://wc/ad1.jpg"));

        // 回写 media id
        AdProductImage after = adImageMapper.selectById(ad.getId());
        assertEquals(7002L, after.getWcMediaId());

        // 第二次同步：广告图按 id 引用，不重传 src
        when(wc.updateProduct(anyLong(), any())).thenReturn(new WcProductRef(9001L, 7001L, List.of(
                new WcImage(7001L, "https://wc/main.jpg"),
                new WcImage(7002L, "https://wc/ad1.jpg"))));
        wcSyncService.runSync(newJob(supplierId, List.of(brandId)), supplierId, List.of(brandId));
        ArgumentCaptor<WcProduct> cap2 = ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).updateProduct(anyLong(), cap2.capture());
        assertEquals(7002L, cap2.getValue().getImagesOverride().get(1).id());
        assertNull(cap2.getValue().getImagesOverride().get(1).src());

        // 清理
        adImageMapper.deleteById(ad.getId());
        mvc.perform(delete("/api/supplier-products/" + spId).header("Authorization", t));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }

    @Test
    void product_without_ad_images_behaves_exactly_as_before() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers", "{\"name\":\"WCNO_Sup_" + ts + "\",\"status\":1}", t);
        long brandId = postForId("/api/brands", "{\"name\":\"WCNO_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"));
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"WCNOP_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCNO_" + ts + "\",\"wholesalePrice\":100,\"minPurchaseQty\":1,\"status\":1}", t);

        when(wc.configured()).thenReturn(true);
        when(wc.ensureBrand(any())).thenReturn(500L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(new WcProductRef(9200L, null));

        wcSyncService.runSync(newJob(supplierId, List.of(brandId)), supplierId, List.of(brandId));

        ArgumentCaptor<WcProduct> cap = ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).createProduct(cap.capture());
        assertNull(cap.getValue().getImagesOverride());              // 未触发广告图分支
        verify(wc, never()).getProduct(anyLong());                   // 零额外调用
        verify(wc, never()).updateProductDescription(anyLong(), any());

        mvc.perform(delete("/api/supplier-products/" + spId).header("Authorization", t));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }
}
