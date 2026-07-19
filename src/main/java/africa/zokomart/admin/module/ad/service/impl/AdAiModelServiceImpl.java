package africa.zokomart.admin.module.ad.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.ad.dto.AdAiModelSaveDTO;
import africa.zokomart.admin.module.ad.entity.AdAiModel;
import africa.zokomart.admin.module.ad.mapper.AdAiModelMapper;
import africa.zokomart.admin.module.ad.service.AdAiModelService;
import africa.zokomart.admin.module.ad.vo.AdAiModelVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdAiModelServiceImpl implements AdAiModelService {

    private final AdAiModelMapper mapper;

    @Override
    public PageResult<AdAiModelVO> page(String keyword, Integer enabled, long current, long size) {
        Page<AdAiModel> p = mapper.selectPage(new Page<>(current, size),
                Wrappers.<AdAiModel>lambdaQuery()
                        .like(StringUtils.hasText(keyword), AdAiModel::getName, keyword)
                        .eq(enabled != null, AdAiModel::getEnabled, enabled)
                        .orderByAsc(AdAiModel::getSort).orderByAsc(AdAiModel::getId));
        return PageResult.of(p.getRecords().stream().map(this::toVO).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public List<AdAiModelVO> listEnabled() {
        return mapper.selectList(Wrappers.<AdAiModel>lambdaQuery()
                        .eq(AdAiModel::getEnabled, 1)
                        .orderByAsc(AdAiModel::getSort).orderByAsc(AdAiModel::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public Long create(AdAiModelSaveDTO dto) {
        if (!StringUtils.hasText(dto.getApiKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "API Key 不能为空");
        }
        AdAiModel m = new AdAiModel();
        BeanUtils.copyProperties(dto, m, "id");
        if (m.getEnabled() == null) m.setEnabled(1);
        if (m.getSort() == null) m.setSort(0);
        mapper.insert(m);
        return m.getId();
    }

    @Override
    public void update(AdAiModelSaveDTO dto) {
        AdAiModel old = mapper.selectById(dto.getId());
        if (old == null) throw new BusinessException(ResultCode.NOT_FOUND, "模型不存在");
        BeanUtils.copyProperties(dto, old, "id", "apiKey");
        if (StringUtils.hasText(dto.getApiKey())) old.setApiKey(dto.getApiKey());   // 留空 = 不改
        mapper.updateById(old);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public AdAiModel getActive(Long id) {
        AdAiModel m = mapper.selectById(id);
        if (m == null || m.getEnabled() == null || m.getEnabled() != 1) {
            throw new BusinessException(ResultCode.AD_MODEL_DISABLED);
        }
        return m;
    }

    private AdAiModelVO toVO(AdAiModel m) {
        AdAiModelVO vo = new AdAiModelVO();
        BeanUtils.copyProperties(m, vo);
        String k = m.getApiKey();
        vo.setApiKeyMasked(k == null ? "" : "****" + k.substring(Math.max(0, k.length() - 4)));
        return vo;
    }
}
