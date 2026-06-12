package africa.zokomart.admin.module.system.vo;

import lombok.Data;

import java.util.List;

/**
 * 登录用户信息：身份 + 角色码 + 权限码 + 可见菜单树。供前端 Vben 路由/按钮权限使用。
 */
@Data
public class LoginUserVO {
    private Long id;
    private String username;
    private String nickname;
    private Integer isSuper;
    private List<String> roles;
    private List<String> permissions;
    private List<MenuVO> menus;
}
