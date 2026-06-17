package africa.zokomart.admin.module.customer.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.customer.vo.CustomerVO;

public interface CustomerService {

    PageResult<CustomerVO> page(String keyword, long current, long size);
}
