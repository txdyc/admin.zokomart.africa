package africa.zokomart.admin.module.system.dto;

import lombok.Data;

@Data
public class UserQueryDTO {
    private String username;
    private Integer status;
    private long current = 1;
    private long size = 10;
}
