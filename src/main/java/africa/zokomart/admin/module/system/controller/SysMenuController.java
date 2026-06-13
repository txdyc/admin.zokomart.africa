package africa.zokomart.admin.module.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.system.dto.MenuSaveDTO;
import africa.zokomart.admin.module.system.service.SysMenuService;
import africa.zokomart.admin.module.system.vo.MenuVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
@Tag(name = "菜单管理")
public class SysMenuController {

    private final SysMenuService menuService;

    @GetMapping("/tree")
    @SaCheckPermission("system:menu:list")
    public Result<List<MenuVO>> tree() {
        return Result.ok(menuService.tree());
    }

    @PostMapping
    @SaCheckPermission("system:menu:create")
    public Result<Long> create(@Valid @RequestBody MenuSaveDTO dto) {
        return Result.ok(menuService.createMenu(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:menu:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MenuSaveDTO dto) {
        dto.setId(id);
        menuService.updateMenu(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:menu:delete")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return Result.ok();
    }
}
