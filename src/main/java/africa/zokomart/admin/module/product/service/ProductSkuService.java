package africa.zokomart.admin.module.product.service;

import africa.zokomart.admin.module.product.dto.ProductSkuSaveDTO;
import africa.zokomart.admin.module.product.entity.ProductSku;
import africa.zokomart.admin.module.product.vo.ProductSkuVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ProductSkuService extends IService<ProductSku> {
    Long createSku(ProductSkuSaveDTO dto);

    void updateSku(ProductSkuSaveDTO dto);

    void deleteSku(Long id);

    ProductSkuVO getDetail(Long id);

    List<ProductSkuVO> listBySpu(Long spuId);
}
