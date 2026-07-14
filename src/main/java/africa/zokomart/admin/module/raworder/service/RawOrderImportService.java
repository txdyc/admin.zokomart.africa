package africa.zokomart.admin.module.raworder.service;

import africa.zokomart.admin.module.raworder.vo.RawOrderImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface RawOrderImportService {

    RawOrderImportResultVO importCsv(MultipartFile file);
}
