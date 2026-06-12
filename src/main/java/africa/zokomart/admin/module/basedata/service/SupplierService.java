package africa.zokomart.admin.module.basedata.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.entity.Supplier;
import africa.zokomart.admin.module.basedata.vo.SupplierVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SupplierService extends IService<Supplier> {
    Long createSupplier(SupplierSaveDTO dto);

    void updateSupplier(SupplierSaveDTO dto);

    void deleteSupplier(Long id);

    PageResult<SupplierVO> pageSuppliers(String keyword, Integer status, long current, long size);

    SupplierVO getDetail(Long id);
}
