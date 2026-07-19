package africa.zokomart.admin.module.ad.mapper;

import africa.zokomart.admin.module.ad.entity.AdProductImage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdProductImageMapper extends BaseMapper<AdProductImage> {

    /** 含逻辑删行的计数：判断产品是否「有过」广告图（触发 WC 清理路径）。 */
    @Select("SELECT COUNT(*) FROM ad_product_image WHERE supplier_product_id = #{supplierProductId}")
    long countIncludingDeleted(Long supplierProductId);
}
