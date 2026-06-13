package africa.zokomart.admin.module.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.system.dto.RoleSaveDTO;
import africa.zokomart.admin.module.system.service.SysRoleService;
import africa.zokomart.admin.module.system.vo.RoleVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理")
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping
    @SaCheckPermission("system:role:list")
    public Result<PageResult<RoleVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(roleService.pageRoles(current, size, keyword));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:role:list")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.getDetail(id));
    }

    @PostMapping
    @SaCheckPermission("system:role:create")
    public Result<Long> create(@Valid @RequestBody RoleSaveDTO dto) {
        return Result.ok(roleService.createRole(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:role:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveDTO dto) {
        dto.setId(id);
        roleService.updateRole(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:role:delete")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.ok();
    }
}
