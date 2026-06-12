package africa.zokomart.admin.module.basedata.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.dto.CategorySaveDTO;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.mapper.CategoryMapper;
import africa.zokomart.admin.module.basedata.service.CategoryService;
import africa.zokomart.admin.module.basedata.support.CategoryTreeBuilder;
import africa.zokomart.admin.module.basedata.vo.CategoryVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public Long createCategory(CategorySaveDTO dto) {
        Category category = new Category();
        BeanUtils.copyProperties(dto, category, "id");
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        save(category);
        return category.getId();
    }

    @Override
    public void updateCategory(CategorySaveDTO dto) {
        Category exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        if (dto.getId().equals(dto.getParentId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "父分类不能是自身");
        }
        BeanUtils.copyProperties(dto, exist, "id");
        updateById(exist);
    }

    @Override
    public void deleteCategory(Long id) {
        boolean hasChild = exists(Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id));
        if (hasChild) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "存在子分类，不能删除");
        }
        removeById(id);
    }

    @Override
    public List<CategoryVO> tree() {
        List<Category> all = list(Wrappers.<Category>lambdaQuery().orderByAsc(Category::getSort));
        return CategoryTreeBuilder.build(all);
    }
}
