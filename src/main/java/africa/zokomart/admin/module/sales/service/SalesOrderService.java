package africa.zokomart.admin.module.sales.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.vo.SalesOrderVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SalesOrderService extends IService<SalesOrder> {

    /** 创建销售订单：扣库存(SALES_OUT) + 录客户信息，状态 PENDING_DISPATCH。 */
    Long create(SalesOrderCreateDTO dto);

    /** 列表：salespersonId 非 null 时限定本人；completed 非 null 时按完成状态筛选。 */
    PageResult<SalesOrderVO> page(Long salespersonId, Boolean completed, long current, long size);

    SalesOrderVO getDetail(Long id);
}
