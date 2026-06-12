package africa.zokomart.admin.module.basedata.support;

import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.vo.CategoryVO;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把扁平分类列表组装成树。parentId=0 或父节点不在列表中的视为根。
 */
public final class CategoryTreeBuilder {

    private CategoryTreeBuilder() {
    }

    public static List<CategoryVO> build(List<Category> categories) {
        List<CategoryVO> vos = categories.stream().map(CategoryTreeBuilder::toVO).toList();
        Map<Long, CategoryVO> byId = vos.stream().collect(Collectors.toMap(CategoryVO::getId, v -> v));
        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO vo : vos) {
            CategoryVO parent = vo.getParentId() == null ? null : byId.get(vo.getParentId());
            if (parent != null) {
                parent.getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    private static CategoryVO toVO(Category c) {
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(c, vo, "children");
        return vo;
    }
}
