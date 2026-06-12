package africa.zokomart.admin.module.system.auth;

import africa.zokomart.admin.module.system.service.PermissionQueryService;
import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源。
 * 超级管理员返回通配 "*"（Sa-Token 视为拥有全部权限），其余按角色聚合权限码。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final PermissionQueryService permissionQueryService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        long userId = Long.parseLong(loginId.toString());
        if (permissionQueryService.isSuperAdmin(userId)) {
            return List.of("*");
        }
        return permissionQueryService.listPermCodesByUserId(userId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        long userId = Long.parseLong(loginId.toString());
        if (permissionQueryService.isSuperAdmin(userId)) {
            return List.of("*");
        }
        return permissionQueryService.listRoleCodesByUserId(userId);
    }
}
