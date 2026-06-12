package africa.zokomart.admin.module.system.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.system.dto.MenuSaveDTO;
import africa.zokomart.admin.module.system.entity.SysMenu;
import africa.zokomart.admin.module.system.mapper.SysMenuMapper;
import africa.zokomart.admin.module.system.service.SysMenuService;
import africa.zokomart.admin.module.system.support.MenuTreeBuilder;
import africa.zokomart.admin.module.system.vo.MenuVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Override
    public Long createMenu(MenuSaveDTO dto) {
        SysMenu menu = new SysMenu();
        BeanUtils.copyProperties(dto, menu, "id");
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getStatus() == null) {
            menu.setStatus(1);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(1);
        }
        save(menu);
        return menu.getId();
    }

    @Override
    public void updateMenu(MenuSaveDTO dto) {
        SysMenu exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        BeanUtils.copyProperties(dto, exist, "id");
        updateById(exist);
    }

    @Override
    public void deleteMenu(Long id) {
        boolean hasChild = exists(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, id));
        if (hasChild) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "存在子菜单，不能删除");
        }
        removeById(id);
    }

    @Override
    public List<MenuVO> tree() {
        List<SysMenu> all = list(Wrappers.<SysMenu>lambdaQuery().orderByAsc(SysMenu::getSort));
        return MenuTreeBuilder.build(all);
    }
}
