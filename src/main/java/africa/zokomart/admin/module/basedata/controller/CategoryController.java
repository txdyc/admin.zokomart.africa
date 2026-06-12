package africa.zokomart.admin.module.basedata.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.basedata.dto.CategorySaveDTO;
import africa.zokomart.admin.module.basedata.service.CategoryService;
import africa.zokomart.admin.module.basedata.vo.CategoryVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    @SaCheckPermission("category:list")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    @PostMapping
    @SaCheckPermission("category:create")
    public Result<Long> create(@Valid @RequestBody CategorySaveDTO dto) {
        return Result.ok(categoryService.createCategory(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("category:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveDTO dto) {
        dto.setId(id);
        categoryService.updateCategory(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("category:delete")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.ok();
    }
}
