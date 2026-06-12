package africa.zokomart.admin.module.system.service;

import africa.zokomart.admin.module.system.dto.MenuSaveDTO;
import africa.zokomart.admin.module.system.entity.SysMenu;
import africa.zokomart.admin.module.system.vo.MenuVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface SysMenuService extends IService<SysMenu> {

    Long createMenu(MenuSaveDTO dto);

    void updateMenu(MenuSaveDTO dto);

    void deleteMenu(Long id);

    List<MenuVO> tree();
}
