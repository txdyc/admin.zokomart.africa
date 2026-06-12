package africa.zokomart.admin.module.system.service.impl;

import africa.zokomart.admin.module.system.entity.SysMenu;
import africa.zokomart.admin.module.system.entity.SysUser;
import africa.zokomart.admin.module.system.mapper.SysMenuMapper;
import africa.zokomart.admin.module.system.mapper.SysRoleMapper;
import africa.zokomart.admin.module.system.mapper.SysUserMapper;
import africa.zokomart.admin.module.system.service.PermissionQueryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionQueryServiceImpl implements PermissionQueryService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public boolean isSuperAdmin(Long userId) {
        SysUser u = userMapper.selectById(userId);
        return u != null && Integer.valueOf(1).equals(u.getIsSuper());
    }

    @Override
    public List<String> listPermCodesByUserId(Long userId) {
        return menuMapper.selectPermCodesByUserId(userId);
    }

    @Override
    public List<String> listRoleCodesByUserId(Long userId) {
        return roleMapper.selectRoleCodesByUserId(userId);
    }

    @Override
    public List<SysMenu> listMenusByUserId(Long userId) {
        if (isSuperAdmin(userId)) {
            return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                    .eq(SysMenu::getStatus, 1)
                    .orderByAsc(SysMenu::getSort));
        }
        return menuMapper.selectMenusByUserId(userId);
    }
}
