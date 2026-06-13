package africa.zokomart.admin.module.purchase.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrder;
import africa.zokomart.admin.module.purchase.vo.PurchaseOrderVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface PurchaseOrderService extends IService<PurchaseOrder> {

    PageResult<PurchaseOrderVO> page(Long planId, Long supplierId, String status, long current, long size);

    PurchaseOrderVO getDetail(Long id);
}
