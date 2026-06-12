package africa.zokomart.admin.module.basedata.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.dto.LogisticsProviderSaveDTO;
import africa.zokomart.admin.module.basedata.entity.LogisticsProvider;
import africa.zokomart.admin.module.basedata.mapper.LogisticsProviderMapper;
import africa.zokomart.admin.module.basedata.service.LogisticsProviderService;
import africa.zokomart.admin.module.basedata.vo.LogisticsProviderVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LogisticsProviderServiceImpl
        extends ServiceImpl<LogisticsProviderMapper, LogisticsProvider>
        implements LogisticsProviderService {

    @Override
    public Long createProvider(LogisticsProviderSaveDTO dto) {
        if (existsName(dto.getName(), null)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "物流服务商名已存在");
        }
        LogisticsProvider lp = new LogisticsProvider();
        BeanUtils.copyProperties(dto, lp, "id");
        if (lp.getStatus() == null) {
            lp.setStatus(1);
        }
        save(lp);
        return lp.getId();
    }

    @Override
    public void updateProvider(LogisticsProviderSaveDTO dto) {
        LogisticsProvider exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "物流服务商不存在");
        }
        if (existsName(dto.getName(), dto.getId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "物流服务商名已存在");
        }
        BeanUtils.copyProperties(dto, exist, "id");
        updateById(exist);
    }

    @Override
    public void deleteProvider(Long id) {
        removeById(id);
    }

    @Override
    public PageResult<LogisticsProviderVO> pageProviders(String keyword, Integer status, long current, long size) {
        IPage<LogisticsProvider> page = page(new Page<>(current, size),
                Wrappers.<LogisticsProvider>lambdaQuery()
                        .like(StringUtils.hasText(keyword), LogisticsProvider::getName, keyword)
                        .eq(status != null, LogisticsProvider::getStatus, status)
                        .orderByDesc(LogisticsProvider::getCreateTime));
        Page<LogisticsProviderVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return PageResult.of(voPage);
    }

    @Override
    public LogisticsProviderVO getDetail(Long id) {
        LogisticsProvider lp = getById(id);
        if (lp == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "物流服务商不存在");
        }
        return toVO(lp);
    }

    private LogisticsProviderVO toVO(LogisticsProvider lp) {
        LogisticsProviderVO vo = new LogisticsProviderVO();
        BeanUtils.copyProperties(lp, vo);
        return vo;
    }

    private boolean existsName(String name, Long excludeId) {
        return exists(Wrappers.<LogisticsProvider>lambdaQuery()
                .eq(LogisticsProvider::getName, name)
                .ne(excludeId != null, LogisticsProvider::getId, excludeId));
    }
}
