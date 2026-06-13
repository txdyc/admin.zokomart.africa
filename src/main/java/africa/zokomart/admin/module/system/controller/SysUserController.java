package africa.zokomart.admin.module.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.system.dto.AssignRolesDTO;
import africa.zokomart.admin.module.system.dto.ResetPwdDTO;
import africa.zokomart.admin.module.system.dto.UserQueryDTO;
import africa.zokomart.admin.module.system.dto.UserSaveDTO;
import africa.zokomart.admin.module.system.service.SysUserService;
import africa.zokomart.admin.module.system.vo.UserVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
@Tag(name = "用户管理")
public class SysUserController {

    private final SysUserService userService;

    @GetMapping
    @SaCheckPermission("system:user:list")
    public Result<PageResult<UserVO>> page(UserQueryDTO query) {
        return Result.ok(userService.pageUsers(query));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:user:list")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.getDetail(id));
    }

    @PostMapping
    @SaCheckPermission("system:user:create")
    public Result<Long> create(@Valid @RequestBody UserSaveDTO dto) {
        return Result.ok(userService.createUser(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:user:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveDTO dto) {
        dto.setId(id);
        userService.updateUser(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:user:delete")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    @SaCheckPermission("system:user:resetPwd")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPwdDTO dto) {
        userService.resetPassword(id, dto.getNewPassword());
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @SaCheckPermission("system:user:update")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody AssignRolesDTO dto) {
        userService.assignRoles(id, dto.getRoleIds());
        return Result.ok();
    }
}
