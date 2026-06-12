package africa.zokomart.admin.module.product.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.product.dto.ProductSpuSaveDTO;
import africa.zokomart.admin.module.product.entity.ProductSpu;
import africa.zokomart.admin.module.product.vo.ProductSpuVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ProductSpuService extends IService<ProductSpu> {
    Long createSpu(ProductSpuSaveDTO dto);

    void updateSpu(ProductSpuSaveDTO dto);

    void deleteSpu(Long id);

    PageResult<ProductSpuVO> pageSpus(String keyword, Long brandId, Long categoryId, Integer status,
                                      long current, long size);

    ProductSpuVO getDetail(Long id);
}
