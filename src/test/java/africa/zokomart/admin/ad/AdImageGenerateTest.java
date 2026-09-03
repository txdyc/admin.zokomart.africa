package africa.zokomart.admin.ad;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.file.FileStorageService;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.ad.client.AiImageClient;
import africa.zokomart.admin.module.ad.dto.AdGenerateDTO;
import africa.zokomart.admin.module.ad.entity.AdAiModel;
import africa.zokomart.admin.module.ad.mapper.AdAiModelMapper;
import africa.zokomart.admin.module.ad.service.AdImageService;
import africa.zokomart.admin.module.ad.vo.AdGenerateVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class AdImageGenerateTest {

    @Autowired AdImageService adImageService;
    @Autowired AdAiModelMapper modelMapper;
    @Autowired FileStorageService storage;

    @MockBean AiImageClient client;

    private AdAiModel newModel(int enabled) {
        AdAiModel m = new AdAiModel();
        m.setName("T_" + System.nanoTime());
        m.setBaseUrl("https://agg.example/v1");
        m.setApiKey("sk-x");
        m.setModelCode("nano");
        m.setEnabled(enabled);
        m.setSort(0);
        modelMapper.insert(m);
        return m;
    }

    @Test
    void generate_stores_temp_files_and_collects_partial_errors() throws Exception {
        AdAiModel m = newModel(1);
        // count=2：第一次成功返回 1 张，第二次抛错 → 部分成功
        when(client.generate(any(), anyString(), anyList()))
                .thenReturn(List.of(new byte[]{9, 9}))
                .thenThrow(new BusinessException(ResultCode.AD_GENERATE_FAILED, "quota"));

        AdGenerateDTO dto = new AdGenerateDTO();
        dto.setModelId(m.getId());
        dto.setPrompt("make an ad");
        dto.setCount(2);
        AdGenerateVO vo = adImageService.generate(dto);

        assertEquals(1, vo.getTempUrls().size());
        assertTrue(vo.getTempUrls().get(0).startsWith("/files/ad-temp/"));
        assertEquals(1, vo.getErrors().size());
        assertTrue(Files.exists(storage.resolvePublicUrl(vo.getTempUrls().get(0))));

        // 清理
        Files.deleteIfExists(storage.resolvePublicUrl(vo.getTempUrls().get(0)));
        modelMapper.deleteById(m.getId());
    }

    @Test
    void generate_rejects_disabled_model_and_all_failures() {
        AdAiModel disabled = newModel(0);
        AdGenerateDTO dto = new AdGenerateDTO();
        dto.setModelId(disabled.getId());
        dto.setPrompt("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> adImageService.generate(dto));
        assertEquals(ResultCode.AD_MODEL_DISABLED.getCode(), ex.getCode());

        AdAiModel ok = newModel(1);
        when(client.generate(any(), anyString(), anyList()))
                .thenThrow(new BusinessException(ResultCode.AD_GENERATE_FAILED, "boom"));
        dto.setModelId(ok.getId());
        BusinessException ex2 = assertThrows(BusinessException.class, () -> adImageService.generate(dto));
        assertEquals(ResultCode.AD_GENERATE_FAILED.getCode(), ex2.getCode());

        modelMapper.deleteById(disabled.getId());
        modelMapper.deleteById(ok.getId());
    }
}
