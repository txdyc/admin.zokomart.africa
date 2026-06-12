package africa.zokomart.admin.module.system.support;

import africa.zokomart.admin.module.system.entity.SysMenu;
import africa.zokomart.admin.module.system.vo.MenuVO;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把扁平菜单列表组装成树。parentId=0 或父节点不在列表中的视为根。
 */
public final class MenuTreeBuilder {

    private MenuTreeBuilder() {
    }

    public static List<MenuVO> build(List<SysMenu> menus) {
        List<MenuVO> vos = menus.stream().map(MenuTreeBuilder::toVO).toList();
        Map<Long, MenuVO> byId = vos.stream().collect(Collectors.toMap(MenuVO::getId, v -> v));
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO vo : vos) {
            Long pid = vo.getParentId();
            MenuVO parent = pid == null ? null : byId.get(pid);
            if (parent != null) {
                parent.getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    private static MenuVO toVO(SysMenu m) {
        MenuVO vo = new MenuVO();
        BeanUtils.copyProperties(m, vo, "children");
        return vo;
    }
}
