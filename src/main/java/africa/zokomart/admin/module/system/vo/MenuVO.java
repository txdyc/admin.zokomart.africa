package africa.zokomart.admin.module.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuVO {
    private Long id;
    private Long parentId;
    private String name;
    private Integer type;
    private String permCode;
    private String routePath;
    private String component;
    private String icon;
    private Integer sort;
    private Integer visible;
    private Integer status;
    private List<MenuVO> children = new ArrayList<>();
}
