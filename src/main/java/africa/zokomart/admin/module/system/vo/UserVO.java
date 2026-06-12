package africa.zokomart.admin.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private Integer status;
    private Integer isSuper;
    private String remark;
    private LocalDateTime createTime;
    private List<Long> roleIds;
}
