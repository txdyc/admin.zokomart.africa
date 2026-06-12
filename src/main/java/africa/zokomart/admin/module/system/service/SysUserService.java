package africa.zokomart.admin.module.system.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.system.dto.UserQueryDTO;
import africa.zokomart.admin.module.system.dto.UserSaveDTO;
import africa.zokomart.admin.module.system.entity.SysUser;
import africa.zokomart.admin.module.system.vo.UserVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface SysUserService extends IService<SysUser> {

    Long createUser(UserSaveDTO dto);

    void updateUser(UserSaveDTO dto);

    void deleteUser(Long id);

    void resetPassword(Long id, String newPassword);

    void assignRoles(Long userId, List<Long> roleIds);

    PageResult<UserVO> pageUsers(UserQueryDTO query);

    UserVO getDetail(Long id);
}
