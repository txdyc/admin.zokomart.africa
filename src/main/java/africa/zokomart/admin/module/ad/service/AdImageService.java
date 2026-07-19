package africa.zokomart.admin.module.ad.service;

import africa.zokomart.admin.module.ad.dto.AdGenerateDTO;
import africa.zokomart.admin.module.ad.vo.AdGenerateVO;

public interface AdImageService {
    AdGenerateVO generate(AdGenerateDTO dto);
}
