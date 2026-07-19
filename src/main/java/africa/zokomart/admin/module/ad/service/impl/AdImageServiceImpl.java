package africa.zokomart.admin.module.ad.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.file.FileStorageService;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.ad.client.AiImageClient;
import africa.zokomart.admin.module.ad.dto.AdGenerateDTO;
import africa.zokomart.admin.module.ad.entity.AdAiModel;
import africa.zokomart.admin.module.ad.service.AdAiModelService;
import africa.zokomart.admin.module.ad.service.AdImageService;
import africa.zokomart.admin.module.ad.vo.AdGenerateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdImageServiceImpl implements AdImageService {

    public static final String TEMP_CATEGORY = "ad-temp";
    public static final String KEEP_CATEGORY = "ad";

    private final AiImageClient client;
    private final AdAiModelService modelService;
    private final FileStorageService storage;

    @Override
    public AdGenerateVO generate(AdGenerateDTO dto) {
        AdAiModel model = modelService.getActive(dto.getModelId());
        List<Path> refs = new ArrayList<>();
        if (dto.getSourceImageUrls() != null) {
            for (String u : dto.getSourceImageUrls()) {
                Path p = storage.resolvePublicUrl(u);   // 前缀 + 防穿越校验
                if (!Files.exists(p)) {
                    throw new BusinessException(ResultCode.AD_INVALID_TEMP_URL, "参考图不存在: " + u);
                }
                refs.add(p);
            }
        }
        int count = dto.getCount() == null ? 1 : dto.getCount();
        List<String> tempUrls = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < count; i++) {   // 串行：聚合平台生图模型普遍单次一图
            try {
                for (byte[] img : client.generate(model, dto.getPrompt(), refs)) {
                    tempUrls.add(storage.storeBytes(img, TEMP_CATEGORY, "png"));
                }
            } catch (BusinessException e) {
                errors.add("第 " + (i + 1) + " 次: " + e.getMessage());
            }
        }
        if (tempUrls.isEmpty()) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, String.join("; ", errors));
        }
        return new AdGenerateVO(tempUrls, errors);
    }
}
