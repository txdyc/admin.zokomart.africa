package africa.zokomart.admin.module.system.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.system.dto.RoleSaveDTO;
import africa.zokomart.admin.module.system.entity.SysRole;
import africa.zokomart.admin.module.system.vo.RoleVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SysRoleService extends IService<SysRole> {

    Long createRole(RoleSaveDTO dto);

    void updateRole(RoleSaveDTO dto);

    void deleteRole(Long id);

    PageResult<RoleVO> pageRoles(long current, long size, String keyword);

    RoleVO getDetail(Long id);
}
