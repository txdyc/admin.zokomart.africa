package africa.zokomart.admin.module.product.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.product.dto.ProductSkuSaveDTO;
import africa.zokomart.admin.module.product.entity.ProductSku;
import africa.zokomart.admin.module.product.mapper.ProductSkuMapper;
import africa.zokomart.admin.module.product.mapper.ProductSpuMapper;
import africa.zokomart.admin.module.product.service.ProductSkuService;
import africa.zokomart.admin.module.product.vo.ProductSkuVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductSkuServiceImpl extends ServiceImpl<ProductSkuMapper, ProductSku> implements ProductSkuService {

    private final ProductSpuMapper spuMapper;

    @Override
    public Long createSku(ProductSkuSaveDTO dto) {
        if (spuMapper.selectById(dto.getSpuId()) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "所属 SPU 不存在");
        }
        if (existsCode(dto.getSkuCode(), null)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "SKU 编码已存在");
        }
        ProductSku sku = new ProductSku();
        BeanUtils.copyProperties(dto, sku, "id");
        if (sku.getPrice() == null) {
            sku.setPrice(BigDecimal.ZERO);
        }
        if (sku.getStatus() == null) {
            sku.setStatus(1);
        }
        save(sku);
        return sku.getId();
    }

    @Override
    public void updateSku(ProductSkuSaveDTO dto) {
        ProductSku exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "SKU 不存在");
        }
        if (existsCode(dto.getSkuCode(), dto.getId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "SKU 编码已存在");
        }
        BeanUtils.copyProperties(dto, exist, "id");
        updateById(exist);
    }

    @Override
    public void deleteSku(Long id) {
        removeById(id);
    }

    @Override
    public ProductSkuVO getDetail(Long id) {
        ProductSku sku = getById(id);
        if (sku == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "SKU 不存在");
        }
        return toVO(sku);
    }

    @Override
    public List<ProductSkuVO> listBySpu(Long spuId) {
        return list(Wrappers.<ProductSku>lambdaQuery().eq(ProductSku::getSpuId, spuId)
                .orderByAsc(ProductSku::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    private ProductSkuVO toVO(ProductSku sku) {
        ProductSkuVO vo = new ProductSkuVO();
        BeanUtils.copyProperties(sku, vo);
        return vo;
    }

    private boolean existsCode(String code, Long excludeId) {
        return exists(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getSkuCode, code)
                .ne(excludeId != null, ProductSku::getId, excludeId));
    }
}
