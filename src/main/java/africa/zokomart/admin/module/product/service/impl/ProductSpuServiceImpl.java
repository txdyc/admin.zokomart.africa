package africa.zokomart.admin.module.product.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.product.dto.ProductSpuSaveDTO;
import africa.zokomart.admin.module.product.entity.ProductSku;
import africa.zokomart.admin.module.product.entity.ProductSpu;
import africa.zokomart.admin.module.product.mapper.ProductSkuMapper;
import africa.zokomart.admin.module.product.mapper.ProductSpuMapper;
import africa.zokomart.admin.module.product.service.ProductSpuService;
import africa.zokomart.admin.module.product.vo.ProductSpuVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductSpuServiceImpl extends ServiceImpl<ProductSpuMapper, ProductSpu> implements ProductSpuService {

    private final ProductSkuMapper skuMapper;

    @Override
    public Long createSpu(ProductSpuSaveDTO dto) {
        ProductSpu spu = new ProductSpu();
        BeanUtils.copyProperties(dto, spu, "id");
        if (spu.getStatus() == null) {
            spu.setStatus(1);
        }
        save(spu);
        return spu.getId();
    }

    @Override
    public void updateSpu(ProductSpuSaveDTO dto) {
        ProductSpu exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "SPU 不存在");
        }
        BeanUtils.copyProperties(dto, exist, "id");
        updateById(exist);
    }

    @Override
    public void deleteSpu(Long id) {
        boolean hasSku = skuMapper.exists(Wrappers.<ProductSku>lambdaQuery().eq(ProductSku::getSpuId, id));
        if (hasSku) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该 SPU 下存在 SKU，不能删除");
        }
        removeById(id);
    }

    @Override
    public PageResult<ProductSpuVO> pageSpus(String keyword, Long brandId, Long categoryId, Integer status,
                                             long current, long size) {
        IPage<ProductSpu> page = page(new Page<>(current, size),
                Wrappers.<ProductSpu>lambdaQuery()
                        .like(StringUtils.hasText(keyword), ProductSpu::getName, keyword)
                        .eq(brandId != null, ProductSpu::getBrandId, brandId)
                        .eq(categoryId != null, ProductSpu::getCategoryId, categoryId)
                        .eq(status != null, ProductSpu::getStatus, status)
                        .orderByDesc(ProductSpu::getCreateTime));
        Page<ProductSpuVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return PageResult.of(voPage);
    }

    @Override
    public ProductSpuVO getDetail(Long id) {
        ProductSpu spu = getById(id);
        if (spu == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "SPU 不存在");
        }
        return toVO(spu);
    }

    private ProductSpuVO toVO(ProductSpu spu) {
        ProductSpuVO vo = new ProductSpuVO();
        BeanUtils.copyProperties(spu, vo);
        return vo;
    }
}
