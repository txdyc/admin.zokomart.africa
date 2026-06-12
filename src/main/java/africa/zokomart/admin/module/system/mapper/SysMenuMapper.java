package africa.zokomart.admin.module.system.mapper;

import africa.zokomart.admin.module.system.entity.SysMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 某用户经其角色拥有的全部权限码（非空、按钮/菜单上配置的 perm_code）。 */
    @Select("""
            SELECT DISTINCT m.perm_code FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId}
              AND m.perm_code IS NOT NULL AND m.perm_code <> ''
              AND m.deleted = 0 AND m.status = 1
            """)
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);

    /** 某用户经其角色可见的全部菜单。 */
    @Select("""
            SELECT DISTINCT m.* FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.status = 1
            ORDER BY m.sort
            """)
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);
}
