package africa.zokomart.admin.module.basedata.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.basedata.dto.LogisticsProviderSaveDTO;
import africa.zokomart.admin.module.basedata.entity.LogisticsProvider;
import africa.zokomart.admin.module.basedata.vo.LogisticsProviderVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface LogisticsProviderService extends IService<LogisticsProvider> {
    Long createProvider(LogisticsProviderSaveDTO dto);

    void updateProvider(LogisticsProviderSaveDTO dto);

    void deleteProvider(Long id);

    PageResult<LogisticsProviderVO> pageProviders(String keyword, Integer status, long current, long size);

    LogisticsProviderVO getDetail(Long id);
}
