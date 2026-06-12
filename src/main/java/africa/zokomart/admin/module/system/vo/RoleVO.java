package africa.zokomart.admin.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoleVO {
    private Long id;
    private String name;
    private String code;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private List<Long> menuIds;
}
