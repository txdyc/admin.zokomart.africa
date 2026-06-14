package africa.zokomart.admin.module.basedata.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.entity.Brand;
import africa.zokomart.admin.module.basedata.entity.SupplierBrand;
import africa.zokomart.admin.module.basedata.mapper.BrandMapper;
import africa.zokomart.admin.module.basedata.mapper.SupplierBrandMapper;
import africa.zokomart.admin.module.basedata.service.SupplierBrandService;
import africa.zokomart.admin.module.basedata.vo.BrandVO;
import africa.zokomart.admin.module.basedata.vo.SupplierBrandVO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierBrandServiceImpl extends ServiceImpl<SupplierBrandMapper, SupplierBrand>
        implements SupplierBrandService {

    private final BrandMapper brandMapper;
    private final SupplierProductMapper supplierProductMapper;

    @Override
    public List<SupplierBrandVO> listBySupplier(Long supplierId) {
        List<SupplierBrand> rows = list(Wrappers.<SupplierBrand>lambdaQuery()
                .eq(SupplierBrand::getSupplierId, supplierId)
                .orderByDesc(SupplierBrand::getCreateTime));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Brand> brands = brandMapper.selectBatchIds(
                        rows.stream().map(SupplierBrand::getBrandId).distinct().toList())
                .stream().collect(Collectors.toMap(Brand::getId, b -> b));
        return rows.stream().map(r -> {
            SupplierBrandVO vo = new SupplierBrandVO();
            vo.setId(r.getId());
            vo.setBrandId(r.getBrandId());
            vo.setStatus(r.getStatus());
            vo.setRemark(r.getRemark());
            Brand b = brands.get(r.getBrandId());
            if (b != null) {
                vo.setBrandName(b.getName());
                vo.setBrandLogoUrl(b.getLogoUrl());
            }
            return vo;
        }).toList();
    }

    @Override
    public List<BrandVO> listAuthorizedBrands(Long supplierId) {
        List<Long> brandIds = list(Wrappers.<SupplierBrand>lambdaQuery()
                .eq(SupplierBrand::getSupplierId, supplierId)
                .eq(SupplierBrand::getStatus, 1))
                .stream().map(SupplierBrand::getBrandId).toList();
        if (brandIds.isEmpty()) {
            return Collections.emptyList();
        }
        return brandMapper.selectBatchIds(brandIds).stream().map(b -> {
            BrandVO vo = new BrandVO();
            BeanUtils.copyProperties(b, vo);
            return vo;
        }).toList();
    }

    @Override
    @Transactional
    public void assign(Long supplierId, List<Long> brandIds) {
        List<Long> target = brandIds == null ? List.of()
                : brandIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!target.isEmpty()) {
            long cnt = brandMapper.selectCount(Wrappers.<Brand>lambdaQuery().in(Brand::getId, target));
            if (cnt != target.size()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "存在不存在的品牌");
            }
        }
        List<SupplierBrand> existing = list(Wrappers.<SupplierBrand>lambdaQuery()
                .eq(SupplierBrand::getSupplierId, supplierId));
        Set<Long> existingBrandIds = existing.stream().map(SupplierBrand::getBrandId).collect(Collectors.toSet());
        Set<Long> targetSet = new HashSet<>(target);

        for (SupplierBrand sb : existing) {
            if (!targetSet.contains(sb.getBrandId())) {
                boolean inUse = supplierProductMapper.exists(Wrappers.<SupplierProduct>lambdaQuery()
                        .eq(SupplierProduct::getSupplierId, supplierId)
                        .eq(SupplierProduct::getBrandId, sb.getBrandId()));
                if (inUse) {
                    throw new BusinessException(ResultCode.BRAND_IN_USE);
                }
                removeById(sb.getId());
            }
        }
        for (Long bid : target) {
            if (!existingBrandIds.contains(bid)) {
                SupplierBrand sb = new SupplierBrand();
                sb.setSupplierId(supplierId);
                sb.setBrandId(bid);
                sb.setStatus(1);
                save(sb);
            }
        }
    }

    @Override
    public boolean isAuthorized(Long supplierId, Long brandId) {
        return exists(Wrappers.<SupplierBrand>lambdaQuery()
                .eq(SupplierBrand::getSupplierId, supplierId)
                .eq(SupplierBrand::getBrandId, brandId)
                .eq(SupplierBrand::getStatus, 1));
    }

    @Override
    public boolean existsByBrandId(Long brandId) {
        return exists(Wrappers.<SupplierBrand>lambdaQuery().eq(SupplierBrand::getBrandId, brandId));
    }

    @Override
    public void removeBySupplier(Long supplierId) {
        remove(Wrappers.<SupplierBrand>lambdaQuery().eq(SupplierBrand::getSupplierId, supplierId));
    }
}
