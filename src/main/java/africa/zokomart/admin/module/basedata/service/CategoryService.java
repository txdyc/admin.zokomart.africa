package africa.zokomart.admin.module.basedata.service;

import africa.zokomart.admin.module.basedata.dto.CategorySaveDTO;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.vo.CategoryVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface CategoryService extends IService<Category> {
    Long createCategory(CategorySaveDTO dto);

    void updateCategory(CategorySaveDTO dto);

    void deleteCategory(Long id);

    List<CategoryVO> tree();
}
