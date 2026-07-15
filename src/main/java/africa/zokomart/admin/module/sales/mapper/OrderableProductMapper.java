package africa.zokomart.admin.module.sales.mapper;

import africa.zokomart.admin.module.sales.vo.OrderableProductVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 可下单产品：supplier_product LEFT JOIN inventory_stock，有货优先。 */
@Mapper
public interface OrderableProductMapper {

    IPage<OrderableProductVO> pageOrderable(Page<OrderableProductVO> page,
                                            @Param("supplierId") Long supplierId,
                                            @Param("brandId") Long brandId,
                                            @Param("categoryId") Long categoryId,
                                            @Param("kw") String kw);
}
