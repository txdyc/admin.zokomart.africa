package africa.zokomart.admin.module.purchase.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.purchase.dto.PurchasePlanSaveDTO;
import africa.zokomart.admin.module.purchase.entity.PurchasePlan;
import africa.zokomart.admin.module.purchase.vo.PurchasePlanVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface PurchasePlanService extends IService<PurchasePlan> {

    Long create(PurchasePlanSaveDTO dto);

    void update(PurchasePlanSaveDTO dto);

    void submit(Long id);

    void delete(Long id);

    PurchasePlanVO getDetail(Long id);

    PageResult<PurchasePlanVO> page(String status, Long supplierId, long current, long size);
}
