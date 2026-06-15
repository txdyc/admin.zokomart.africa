package africa.zokomart.admin.module.supplierproduct.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.mapper.CategoryMapper;
import africa.zokomart.admin.module.basedata.vo.CategoryVO;
import africa.zokomart.admin.module.supplierproduct.dto.SupplierProductSaveDTO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductService;
import africa.zokomart.admin.module.supplierproduct.vo.SupplierProductVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierProductServiceImpl extends ServiceImpl<SupplierProductMapper, SupplierProduct>
        implements SupplierProductService {

    private final CategoryMapper categoryMapper;
    private final africa.zokomart.admin.module.basedata.service.SupplierBrandService supplierBrandService;

    @Override
    @Transactional
    public Long createSupplierProduct(SupplierProductSaveDTO dto) {
        if (existsProductCode(dto.getSupplierId(), dto.getProductCode(), null)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该供应商下产品编码已存在");
        }
        if (dto.getBrandId() != null
                && !supplierBrandService.isAuthorized(dto.getSupplierId(), dto.getBrandId())) {
            throw new BusinessException(ResultCode.BRAND_NOT_AUTHORIZED);
        }
        SupplierProduct sp = new SupplierProduct();
        BeanUtils.copyProperties(dto, sp, "id");
        applyDefaults(sp);
        save(sp);
        return sp.getId();
    }

    @Override
    @Transactional
    public void updateSupplierProduct(SupplierProductSaveDTO dto) {
        SupplierProduct exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商产品不存在");
        }
        if (existsProductCode(dto.getSupplierId(), dto.getProductCode(), dto.getId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该供应商下产品编码已存在");
        }
        if (dto.getBrandId() != null
                && !supplierBrandService.isAuthorized(dto.getSupplierId(), dto.getBrandId())) {
            throw new BusinessException(ResultCode.BRAND_NOT_AUTHORIZED);
        }
        BeanUtils.copyProperties(dto, exist, "id");
        updateById(exist);
    }

    @Override
    public void deleteSupplierProduct(Long id) {
        removeById(id);
    }

    @Override
    public PageResult<SupplierProductVO> pageSupplierProducts(Long supplierId, Long brandId, Long categoryId,
                                                              String keyword, Integer status,
                                                              long current, long size) {
        IPage<SupplierProduct> page = page(new Page<>(current, size),
                Wrappers.<SupplierProduct>lambdaQuery()
                        .eq(supplierId != null, SupplierProduct::getSupplierId, supplierId)
                        .eq(brandId != null, SupplierProduct::getBrandId, brandId)
                        .eq(categoryId != null, SupplierProduct::getCategoryId, categoryId)
                        .eq(status != null, SupplierProduct::getStatus, status)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SupplierProduct::getName, keyword)
                                .or().like(SupplierProduct::getProductCode, keyword))
                        .orderByDesc(SupplierProduct::getCreateTime));
        Page<SupplierProductVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return PageResult.of(voPage);
    }

    @Override
    public SupplierProductVO getDetail(Long id) {
        SupplierProduct sp = getById(id);
        if (sp == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商产品不存在");
        }
        return toVO(sp);
    }

    @Override
    public List<CategoryVO> listCategoriesBySupplier(Long supplierId) {
        List<Long> categoryIds = distinctRefIds(supplierId, "category_id");
        if (categoryIds.isEmpty()) {
            return Collections.emptyList();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream().map(this::toCategoryVO).toList();
    }

    @Override
    public boolean existsBySupplierId(Long supplierId) {
        return exists(Wrappers.<SupplierProduct>lambdaQuery().eq(SupplierProduct::getSupplierId, supplierId));
    }

    @Override
    public boolean existsByBrandId(Long brandId) {
        return exists(Wrappers.<SupplierProduct>lambdaQuery().eq(SupplierProduct::getBrandId, brandId));
    }

    @Override
    public boolean existsByCategoryId(Long categoryId) {
        return exists(Wrappers.<SupplierProduct>lambdaQuery().eq(SupplierProduct::getCategoryId, categoryId));
    }

    @Override
    public SupplierProduct findBySupplierAndCode(Long supplierId, String productCode) {
        return getOne(Wrappers.<SupplierProduct>lambdaQuery()
                .eq(SupplierProduct::getSupplierId, supplierId)
                .eq(SupplierProduct::getProductCode, productCode), false);
    }

    /** 查询某供应商现有产品引用的去重外键 id（自动过滤逻辑删除行）。 */
    private List<Long> distinctRefIds(Long supplierId, String column) {
        return baseMapper.selectObjs(Wrappers.<SupplierProduct>query()
                        .select("DISTINCT " + column)
                        .eq("supplier_id", supplierId)
                        .isNotNull(column))
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(o -> Long.valueOf(o.toString()))
                .toList();
    }

    private void applyDefaults(SupplierProduct sp) {
        if (sp.getStatus() == null) {
            sp.setStatus(1);
        }
        if (sp.getMinPurchaseQty() == null) {
            sp.setMinPurchaseQty(1);
        }
        if (sp.getWholesalePrice() == null) {
            sp.setWholesalePrice(java.math.BigDecimal.ZERO);
        }
        if (sp.getRetailPrice() == null) {
            sp.setRetailPrice(java.math.BigDecimal.ZERO);
        }
    }

    private SupplierProductVO toVO(SupplierProduct sp) {
        SupplierProductVO vo = new SupplierProductVO();
        BeanUtils.copyProperties(sp, vo);
        return vo;
    }

    private CategoryVO toCategoryVO(Category category) {
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }

    private boolean existsProductCode(Long supplierId, String productCode, Long excludeId) {
        return exists(Wrappers.<SupplierProduct>lambdaQuery()
                .eq(SupplierProduct::getSupplierId, supplierId)
                .eq(SupplierProduct::getProductCode, productCode)
                .ne(excludeId != null, SupplierProduct::getId, excludeId));
    }
}
