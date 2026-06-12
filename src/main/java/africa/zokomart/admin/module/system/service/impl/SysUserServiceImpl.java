package africa.zokomart.admin.module.system.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.system.dto.UserQueryDTO;
import africa.zokomart.admin.module.system.dto.UserSaveDTO;
import africa.zokomart.admin.module.system.entity.SysUser;
import africa.zokomart.admin.module.system.entity.SysUserRole;
import africa.zokomart.admin.module.system.mapper.SysUserMapper;
import africa.zokomart.admin.module.system.mapper.SysUserRoleMapper;
import africa.zokomart.admin.module.system.service.SysUserService;
import africa.zokomart.admin.module.system.vo.UserVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Long createUser(UserSaveDTO dto) {
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新增用户必须设置密码");
        }
        if (existsUsername(dto.getUsername(), null)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户名已存在");
        }
        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user, "id", "password", "roleIds");
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setIsSuper(0);
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        save(user);
        replaceRoles(user.getId(), dto.getRoleIds());
        return user.getId();
    }

    @Override
    @Transactional
    public void updateUser(UserSaveDTO dto) {
        SysUser exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (isSuper(exist) && Integer.valueOf(0).equals(dto.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "超级管理员不可停用");
        }
        if (existsUsername(dto.getUsername(), dto.getId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户名已存在");
        }
        exist.setUsername(dto.getUsername());
        exist.setNickname(dto.getNickname());
        exist.setPhone(dto.getPhone());
        exist.setEmail(dto.getEmail());
        exist.setRemark(dto.getRemark());
        if (dto.getStatus() != null) {
            exist.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getPassword())) {
            exist.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        updateById(exist);
        if (dto.getRoleIds() != null) {
            replaceRoles(exist.getId(), dto.getRoleIds());
        }
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        SysUser exist = getById(id);
        if (exist == null) {
            return;
        }
        if (isSuper(exist)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "超级管理员不可删除");
        }
        removeById(id);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        SysUser exist = getById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        exist.setPassword(passwordEncoder.encode(newPassword));
        updateById(exist);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (getById(userId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        replaceRoles(userId, roleIds);
    }

    @Override
    public PageResult<UserVO> pageUsers(UserQueryDTO query) {
        IPage<SysUser> page = page(new Page<>(query.getCurrent(), query.getSize()),
                Wrappers.<SysUser>lambdaQuery()
                        .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                        .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                        .orderByDesc(SysUser::getCreateTime));
        Page<UserVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return PageResult.of(voPage);
    }

    @Override
    public UserVO getDetail(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toVO(user);
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setRoleIds(userRoleMapper.selectList(
                        Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, user.getId()))
                .stream().map(SysUserRole::getRoleId).toList());
        return vo;
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    private boolean existsUsername(String username, Long excludeId) {
        return exists(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
    }

    private boolean isSuper(SysUser user) {
        return Integer.valueOf(1).equals(user.getIsSuper());
    }
}
