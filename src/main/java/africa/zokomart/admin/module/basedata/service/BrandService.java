package africa.zokomart.admin.module.basedata.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.basedata.dto.BrandSaveDTO;
import africa.zokomart.admin.module.basedata.entity.Brand;
import africa.zokomart.admin.module.basedata.vo.BrandVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface BrandService extends IService<Brand> {
    Long createBrand(BrandSaveDTO dto);

    void updateBrand(BrandSaveDTO dto);

    void deleteBrand(Long id);

    PageResult<BrandVO> pageBrands(String keyword, Integer status, long current, long size);

    BrandVO getDetail(Long id);
}
