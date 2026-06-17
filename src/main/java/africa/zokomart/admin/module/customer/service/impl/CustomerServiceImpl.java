package africa.zokomart.admin.module.customer.service.impl;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.customer.mapper.CustomerMapper;
import africa.zokomart.admin.module.customer.service.CustomerService;
import africa.zokomart.admin.module.customer.vo.CustomerVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;

    @Override
    public PageResult<CustomerVO> page(String keyword, long current, long size) {
        Page<CustomerVO> page = new Page<>(current, size);
        IPage<CustomerVO> result = customerMapper.pageCustomers(
                page, StringUtils.hasText(keyword) ? keyword.trim() : null);
        return PageResult.of(result);
    }
}
