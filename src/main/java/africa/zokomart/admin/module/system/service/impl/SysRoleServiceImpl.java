package africa.zokomart.admin.module.system.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.system.dto.RoleSaveDTO;
import africa.zokomart.admin.module.system.entity.SysRole;
import africa.zokomart.admin.module.system.entity.SysRoleMenu;
import africa.zokomart.admin.module.system.entity.SysUserRole;
import africa.zokomart.admin.module.system.mapper.SysRoleMapper;
import africa.zokomart.admin.module.system.mapper.SysRoleMenuMapper;
import africa.zokomart.admin.module.system.mapper.SysUserRoleMapper;
import africa.zokomart.admin.module.system.service.SysRoleService;
import africa.zokomart.admin.module.system.vo.RoleVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    @Transactional
    public Long createRole(RoleSaveDTO dto) {
        if (exists(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, dto.getCode()))) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "角色编码已存在");
        }
        SysRole role = new SysRole();
        BeanUtils.copyProperties(dto, role, "id", "menuIds");
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        save(role);
        replaceMenus(role.getId(), dto.getMenuIds());
        return role.getId();
    }

    @Override
    @Transactional
    public void updateRole(RoleSaveDTO dto) {
        SysRole exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        if (exists(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getCode, dto.getCode()).ne(SysRole::getId, dto.getId()))) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "角色编码已存在");
        }
        BeanUtils.copyProperties(dto, exist, "id", "menuIds");
        updateById(exist);
        if (dto.getMenuIds() != null) {
            replaceMenus(exist.getId(), dto.getMenuIds());
        }
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        if (getById(id) == null) {
            return;
        }
        removeById(id);
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
    }

    @Override
    public PageResult<RoleVO> pageRoles(long current, long size, String keyword) {
        var wrapper = Wrappers.<SysRole>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysRole::getName, keyword).or().like(SysRole::getCode, keyword));
        }
        wrapper.orderByAsc(SysRole::getSort);
        IPage<SysRole> page = page(new Page<>(current, size), wrapper);
        Page<RoleVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return PageResult.of(voPage);
    }

    @Override
    public RoleVO getDetail(Long id) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return toVO(role);
    }

    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(role, vo);
        vo.setMenuIds(roleMenuMapper.selectList(
                        Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, role.getId()))
                .stream().map(SysRoleMenu::getMenuId).toList());
        return vo;
    }

    private void replaceMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }
}
