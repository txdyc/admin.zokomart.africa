package africa.zokomart.admin.module.raworder.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.raworder.vo.RawOrderVO;

import java.time.LocalDate;

public interface RawOrderService {

    PageResult<RawOrderVO> page(LocalDate dateStart, LocalDate dateEnd, String status,
                                String brand, String keyword, long current, long size);
}
