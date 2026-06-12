package africa.zokomart.admin.module.basedata.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.dto.BrandSaveDTO;
import africa.zokomart.admin.module.basedata.entity.Brand;
import africa.zokomart.admin.module.basedata.mapper.BrandMapper;
import africa.zokomart.admin.module.basedata.service.BrandService;
import africa.zokomart.admin.module.basedata.vo.BrandVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements BrandService {

    @Override
    public Long createBrand(BrandSaveDTO dto) {
        if (existsName(dto.getName(), null)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "品牌名已存在");
        }
        Brand brand = new Brand();
        BeanUtils.copyProperties(dto, brand, "id");
        if (brand.getStatus() == null) {
            brand.setStatus(1);
        }
        save(brand);
        return brand.getId();
    }

    @Override
    public void updateBrand(BrandSaveDTO dto) {
        Brand exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "品牌不存在");
        }
        if (existsName(dto.getName(), dto.getId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "品牌名已存在");
        }
        BeanUtils.copyProperties(dto, exist, "id");
        updateById(exist);
    }

    @Override
    public void deleteBrand(Long id) {
        // 被供应商产品引用的校验在 Phase 4 回填
        removeById(id);
    }

    @Override
    public PageResult<BrandVO> pageBrands(String keyword, Integer status, long current, long size) {
        IPage<Brand> page = page(new Page<>(current, size),
                Wrappers.<Brand>lambdaQuery()
                        .like(StringUtils.hasText(keyword), Brand::getName, keyword)
                        .eq(status != null, Brand::getStatus, status)
                        .orderByAsc(Brand::getSort));
        Page<BrandVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return PageResult.of(voPage);
    }

    @Override
    public BrandVO getDetail(Long id) {
        Brand brand = getById(id);
        if (brand == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "品牌不存在");
        }
        return toVO(brand);
    }

    private BrandVO toVO(Brand brand) {
        BrandVO vo = new BrandVO();
        BeanUtils.copyProperties(brand, vo);
        return vo;
    }

    private boolean existsName(String name, Long excludeId) {
        return exists(Wrappers.<Brand>lambdaQuery()
                .eq(Brand::getName, name)
                .ne(excludeId != null, Brand::getId, excludeId));
    }
}
