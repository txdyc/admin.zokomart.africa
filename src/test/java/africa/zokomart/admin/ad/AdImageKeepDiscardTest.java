package africa.zokomart.admin.ad;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.file.FileStorageService;
import africa.zokomart.admin.module.ad.dto.AdDiscardDTO;
import africa.zokomart.admin.module.ad.dto.AdKeepDTO;
import africa.zokomart.admin.module.ad.mapper.AdProductImageMapper;
import africa.zokomart.admin.module.ad.service.AdImageService;
import africa.zokomart.admin.module.ad.vo.AdProductImageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AdImageKeepDiscardTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AdImageService adImageService;
    @Autowired AdProductImageMapper imageMapper;
    @Autowired FileStorageService storage;

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
    void keep_moves_file_and_persists_then_list_and_delete() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers", "{\"name\":\"AD_Sup_" + ts + "\",\"status\":1}", t);
        long brandId = postForId("/api/brands", "{\"name\":\"AD_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"));
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"ADP_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"ADPC_" + ts + "\",\"wholesalePrice\":1,\"retailPrice\":2,\"minPurchaseQty\":1}", t);

        String tempUrl = storage.storeBytes(new byte[]{7}, "ad-temp", "png");

        AdKeepDTO dto = new AdKeepDTO();
        dto.setSupplierProductId(spId);
        AdKeepDTO.Item item = new AdKeepDTO.Item();
        item.setTempUrl(tempUrl);
        item.setPrompt("p1");
        dto.setItems(List.of(item));
        List<Long> ids = adImageService.keep(dto);

        assertEquals(1, ids.size());
        List<AdProductImageVO> list = adImageService.listByProduct(spId);
        assertEquals(1, list.size());
        assertTrue(list.get(0).getFileUrl().startsWith("/files/ad/"));      // 已移入正式目录
        assertFalse(Files.exists(storage.resolvePublicUrl(tempUrl)));       // 临时文件已不在
        assertTrue(Files.exists(storage.resolvePublicUrl(list.get(0).getFileUrl())));

        // 逻辑删：列表不再返回；文件保留（WC 端可能已引用）
        adImageService.delete(ids.get(0));
        assertTrue(adImageService.listByProduct(spId).isEmpty());
        assertTrue(Files.exists(storage.resolvePublicUrl(list.get(0).getFileUrl())));

        // 清理
        Files.deleteIfExists(storage.resolvePublicUrl(list.get(0).getFileUrl()));
        imageMapper.selectList(null).stream()
                .filter(i -> i.getSupplierProductId().equals(spId))
                .forEach(i -> imageMapper.deleteById(i.getId()));
        mvc.perform(delete("/api/supplier-products/" + spId).header("Authorization", t));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }

    @Test
    void keep_and_discard_reject_urls_outside_ad_temp() throws Exception {
        AdDiscardDTO bad = new AdDiscardDTO();
        bad.setTempUrls(List.of("/files/brand/x.png"));           // 非 ad-temp 目录
        assertThrows(BusinessException.class, () -> adImageService.discard(bad));

        AdDiscardDTO traversal = new AdDiscardDTO();
        traversal.setTempUrls(List.of("/files/ad-temp/../../secret.txt"));
        assertThrows(BusinessException.class, () -> adImageService.discard(traversal));

        // 丢弃不存在的文件：幂等成功
        AdDiscardDTO gone = new AdDiscardDTO();
        gone.setTempUrls(List.of("/files/ad-temp/not-exists.png"));
        assertDoesNotThrow(() -> adImageService.discard(gone));
    }
}
