package africa.zokomart.admin.module.system.service;

import africa.zokomart.admin.module.system.entity.SysMenu;

import java.util.List;

/**
 * 鉴权相关只读查询：供 Sa-Token StpInterface 与登录信息组装使用。
 */
public interface PermissionQueryService {

    boolean isSuperAdmin(Long userId);

    List<String> listPermCodesByUserId(Long userId);

    List<String> listRoleCodesByUserId(Long userId);

    /** 用户可见菜单；超管返回全部启用菜单。 */
    List<SysMenu> listMenusByUserId(Long userId);
}
