package africa.zokomart.admin.module.ad.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.ad.dto.AdAiModelSaveDTO;
import africa.zokomart.admin.module.ad.entity.AdAiModel;
import africa.zokomart.admin.module.ad.vo.AdAiModelVO;

import java.util.List;

public interface AdAiModelService {
    PageResult<AdAiModelVO> page(String keyword, Integer enabled, long current, long size);
    List<AdAiModelVO> listEnabled();
    Long create(AdAiModelSaveDTO dto);
    void update(AdAiModelSaveDTO dto);
    void delete(Long id);
    /** 内部用：返回含明文 key 的启用模型；不存在/停用抛 AD_MODEL_DISABLED。 */
    AdAiModel getActive(Long id);
}
