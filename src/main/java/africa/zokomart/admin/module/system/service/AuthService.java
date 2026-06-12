package africa.zokomart.admin.module.system.service;

import africa.zokomart.admin.module.system.dto.LoginDTO;
import africa.zokomart.admin.module.system.vo.LoginUserVO;
import africa.zokomart.admin.module.system.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    void logout();

    /** 当前登录用户信息（角色码 + 权限码 + 菜单树）。 */
    LoginUserVO currentUserInfo();
}
