package africa.zokomart.admin.module.purchase.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.purchase.entity.ActualPurchaseOrder;
import africa.zokomart.admin.module.purchase.vo.ActualPurchaseOrderVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ActualPurchaseOrderService extends IService<ActualPurchaseOrder> {

    PageResult<ActualPurchaseOrderVO> page(Long purchaseOrderId, String status, long current, long size);

    ActualPurchaseOrderVO getDetail(Long id);
}
