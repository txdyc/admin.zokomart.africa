package africa.zokomart.admin.module.system.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.system.dto.LoginDTO;
import africa.zokomart.admin.module.system.entity.SysUser;
import africa.zokomart.admin.module.system.mapper.SysUserMapper;
import africa.zokomart.admin.module.system.service.AuthService;
import africa.zokomart.admin.module.system.service.PermissionQueryService;
import africa.zokomart.admin.module.system.support.MenuTreeBuilder;
import africa.zokomart.admin.module.system.vo.LoginUserVO;
import africa.zokomart.admin.module.system.vo.LoginVO;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionQueryService permissionQueryService;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被停用");
        }
        StpUtil.login(user.getId());
        return new LoginVO(StpUtil.getTokenValue(), buildUserInfo(user));
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public LoginUserVO currentUserInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        return buildUserInfo(user);
    }

    private LoginUserVO buildUserInfo(SysUser user) {
        boolean isSuper = Integer.valueOf(1).equals(user.getIsSuper());
        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setIsSuper(user.getIsSuper());
        vo.setRoles(isSuper ? List.of("*") : permissionQueryService.listRoleCodesByUserId(user.getId()));
        vo.setPermissions(isSuper ? List.of("*") : permissionQueryService.listPermCodesByUserId(user.getId()));
        vo.setMenus(MenuTreeBuilder.build(permissionQueryService.listMenusByUserId(user.getId())));
        return vo;
    }
}
