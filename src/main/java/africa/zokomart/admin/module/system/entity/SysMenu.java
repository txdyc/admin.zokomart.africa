package africa.zokomart.admin.module.system.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
    private Long parentId;
    private String name;
    /** 1目录 2菜单 3按钮 */
    private Integer type;
    private String permCode;
    private String routePath;
    private String component;
    private String icon;
    private Integer sort;
    private Integer visible;
    private Integer status;
}
