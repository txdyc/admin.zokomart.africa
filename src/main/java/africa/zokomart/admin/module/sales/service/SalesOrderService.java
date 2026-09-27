package africa.zokomart.admin.module.sales.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.dto.SalesOrderUpdateDTO;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.vo.SalesOrderLabelVO;
import africa.zokomart.admin.module.sales.vo.SalesOrderVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;

public interface SalesOrderService extends IService<SalesOrder> {

    /** 创建销售订单：扣库存(SALES_OUT) + 录客户信息，状态 PENDING_DISPATCH。 */
    Long create(SalesOrderCreateDTO dto);

    /** 列表：salespersonId 非 null 时限定本人；completed 非 null 时按完成状态筛选。 */
    PageResult<SalesOrderVO> page(Long salespersonId, Boolean completed, long current, long size);

    SalesOrderVO getDetail(Long id);

    /** 管理员修正订单：客户信息任何状态可改；明细仅未派送时可改，库存按净变化调整。
     *  salespersonId 非 null 时仅限本人订单。 */
    void update(Long id, Long salespersonId, SalesOrderUpdateDTO dto);

    /** 逻辑删除订单及明细并回补库存；仅待派送订单可删。salespersonId 非 null 时仅限本人订单。 */
    void delete(Long id, Long salespersonId);

    /** 面单数据：按 status（默认 PENDING_DISPATCH）+ 当天(date，默认今日 create_time)；
     *  salespersonId 非 null 时仅本人。 */
    List<SalesOrderLabelVO> labels(Long salespersonId, String status, LocalDate date);

    /** 可下单产品分页：全部在售产品（含无库存），有货优先。 */
    PageResult<africa.zokomart.admin.module.sales.vo.OrderableProductVO> orderableProducts(
            Long supplierId, Long brandId, Long categoryId, String keyword, long current, long size);
}
