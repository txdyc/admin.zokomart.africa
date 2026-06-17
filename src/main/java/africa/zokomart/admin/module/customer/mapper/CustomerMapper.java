package africa.zokomart.admin.module.customer.mapper;

import africa.zokomart.admin.module.customer.vo.CustomerVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 客户聚合查询（无实体，纯 XML 聚合）。 */
@Mapper
public interface CustomerMapper {

    IPage<CustomerVO> pageCustomers(Page<CustomerVO> page, @Param("kw") String kw);
}
