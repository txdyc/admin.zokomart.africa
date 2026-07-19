package africa.zokomart.admin.module.ad.service;

import africa.zokomart.admin.module.ad.dto.AdDiscardDTO;
import africa.zokomart.admin.module.ad.dto.AdGenerateDTO;
import africa.zokomart.admin.module.ad.dto.AdKeepDTO;
import africa.zokomart.admin.module.ad.vo.AdGenerateVO;
import africa.zokomart.admin.module.ad.vo.AdProductImageVO;

import java.util.List;

public interface AdImageService {
    AdGenerateVO generate(AdGenerateDTO dto);

    List<Long> keep(AdKeepDTO dto);

    void discard(AdDiscardDTO dto);

    List<AdProductImageVO> listByProduct(Long supplierProductId);

    void delete(Long id);
}
