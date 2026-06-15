package africa.zokomart.admin.module.supplierproduct.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.basedata.vo.CategoryVO;
import africa.zokomart.admin.module.supplierproduct.dto.SupplierProductSaveDTO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.vo.SupplierProductVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface SupplierProductService extends IService<SupplierProduct> {

    Long createSupplierProduct(SupplierProductSaveDTO dto);

    void updateSupplierProduct(SupplierProductSaveDTO dto);

    void deleteSupplierProduct(Long id);

    PageResult<SupplierProductVO> pageSupplierProducts(Long supplierId, Long brandId, Long categoryId,
                                                       String keyword, Integer status, long current, long size);

    SupplierProductVO getDetail(Long id);

    /** 该供应商已有产品涉及的分类（distinct），用于采购页联动筛选。 */
    List<CategoryVO> listCategoriesBySupplier(Long supplierId);

    /** 基础数据删除前的引用校验。 */
    boolean existsBySupplierId(Long supplierId);

    boolean existsByBrandId(Long brandId);

    boolean existsByCategoryId(Long categoryId);
}
