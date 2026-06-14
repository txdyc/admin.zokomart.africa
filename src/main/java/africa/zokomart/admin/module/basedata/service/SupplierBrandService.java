package africa.zokomart.admin.module.basedata.service;

import africa.zokomart.admin.module.basedata.vo.BrandVO;
import africa.zokomart.admin.module.basedata.vo.SupplierBrandVO;

import java.util.List;

public interface SupplierBrandService {

    List<SupplierBrandVO> listBySupplier(Long supplierId);

    List<BrandVO> listAuthorizedBrands(Long supplierId);

    void assign(Long supplierId, List<Long> brandIds);

    boolean isAuthorized(Long supplierId, Long brandId);

    boolean existsByBrandId(Long brandId);

    void removeBySupplier(Long supplierId);
}
