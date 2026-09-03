package africa.zokomart.admin.module.ad.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.file.FileStorageService;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.ad.client.AiImageClient;
import africa.zokomart.admin.module.ad.dto.AdDiscardDTO;
import africa.zokomart.admin.module.ad.dto.AdGenerateDTO;
import africa.zokomart.admin.module.ad.dto.AdKeepDTO;
import africa.zokomart.admin.module.ad.entity.AdAiModel;
import africa.zokomart.admin.module.ad.entity.AdProductImage;
import africa.zokomart.admin.module.ad.mapper.AdProductImageMapper;
import africa.zokomart.admin.module.ad.service.AdAiModelService;
import africa.zokomart.admin.module.ad.service.AdImageService;
import africa.zokomart.admin.module.ad.vo.AdGenerateVO;
import africa.zokomart.admin.module.ad.vo.AdProductImageVO;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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
    private final AdProductImageMapper imageMapper;
    private final SupplierProductMapper supplierProductMapper;

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

    @Override
    @Transactional
    public List<Long> keep(AdKeepDTO dto) {
        if (supplierProductMapper.selectById(dto.getSupplierProductId()) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商产品不存在");
        }
        Integer maxSort = imageMapper.selectList(
                        Wrappers.<AdProductImage>lambdaQuery()
                                .eq(AdProductImage::getSupplierProductId, dto.getSupplierProductId())
                                .orderByDesc(AdProductImage::getSort).last("LIMIT 1"))
                .stream().findFirst().map(AdProductImage::getSort).orElse(0);
        List<Long> ids = new ArrayList<>();
        for (AdKeepDTO.Item item : dto.getItems()) {
            Path src = requireTempPath(item.getTempUrl());
            if (!Files.exists(src)) {
                throw new BusinessException(ResultCode.AD_INVALID_TEMP_URL, "临时图不存在: " + item.getTempUrl());
            }
            String filename = src.getFileName().toString();
            String keptUrl;
            try {
                Path destDir = storage.resolvePublicUrl("/files/" + KEEP_CATEGORY + "/x").getParent();
                Files.createDirectories(destDir);
                Files.move(src, destDir.resolve(filename));
                keptUrl = "/files/" + KEEP_CATEGORY + "/" + filename;
            } catch (IOException e) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "移动文件失败");
            }
            AdProductImage row = new AdProductImage();
            row.setSupplierProductId(dto.getSupplierProductId());
            row.setFileUrl(keptUrl);
            row.setPrompt(item.getPrompt());
            row.setModelId(item.getModelId());
            row.setSort(++maxSort);
            imageMapper.insert(row);
            ids.add(row.getId());
        }
        return ids;
    }

    @Override
    public void discard(AdDiscardDTO dto) {
        for (String u : dto.getTempUrls()) {
            try {
                Files.deleteIfExists(requireTempPath(u));   // 不存在视为成功（幂等）
            } catch (IOException e) {
                log.warn("discard temp file failed: {}", u);
            }
        }
    }

    @Override
    public List<AdProductImageVO> listByProduct(Long supplierProductId) {
        return imageMapper.selectList(Wrappers.<AdProductImage>lambdaQuery()
                        .eq(AdProductImage::getSupplierProductId, supplierProductId)
                        .orderByAsc(AdProductImage::getSort))
                .stream().map(e -> {
                    AdProductImageVO vo = new AdProductImageVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                }).toList();
    }

    @Override
    public void delete(Long id) {
        imageMapper.deleteById(id);   // 逻辑删；文件保留避免 WC 已引用的图裂链
    }

    /** 归一化 + 强制位于 ad-temp 目录内，防任意删/移文件。 */
    private Path requireTempPath(String url) {
        Path p = storage.resolvePublicUrl(url);
        Path parent = p.getParent();
        if (parent == null || !TEMP_CATEGORY.equals(parent.getFileName().toString())) {
            throw new BusinessException(ResultCode.AD_INVALID_TEMP_URL, url);
        }
        return p;
    }
}
