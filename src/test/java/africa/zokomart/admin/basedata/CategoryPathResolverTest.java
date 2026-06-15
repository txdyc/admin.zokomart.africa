package africa.zokomart.admin.basedata;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.util.CategoryPathResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CategoryPathResolverTest {

    private Category cat(long id, Long parentId, String name) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(parentId);
        c.setName(name);
        return c;
    }

    /** 数据：Electronics(1,parent0) > Phones(2,parent1); Appliances(3,0) 与 Appliances(4,2) 同名不同父 */
    private List<Category> data() {
        return List.of(
                cat(1, 0L, "Electronics"),
                cat(2, 1L, "Phones"),
                cat(3, 0L, "Appliances"),
                cat(4, 2L, "Appliances"));
    }

    @Test
    void blank_path_returns_null() {
        assertNull(CategoryPathResolver.resolve(data(), null));
        assertNull(CategoryPathResolver.resolve(data(), "   "));
    }

    @Test
    void resolves_nested_path() {
        assertEquals(2L, CategoryPathResolver.resolve(data(), "Electronics>Phones"));
        assertEquals(1L, CategoryPathResolver.resolve(data(), "Electronics"));
    }

    @Test
    void duplicate_name_disambiguated_by_path() {
        // 顶层 Appliances=3；Electronics>Phones>Appliances=4
        assertEquals(3L, CategoryPathResolver.resolve(data(), "Appliances"));
        assertEquals(4L, CategoryPathResolver.resolve(data(), "Electronics>Phones>Appliances"));
    }

    @Test
    void not_found_throws() {
        assertThrows(BusinessException.class,
                () -> CategoryPathResolver.resolve(data(), "Electronics>Nope"));
    }
}
