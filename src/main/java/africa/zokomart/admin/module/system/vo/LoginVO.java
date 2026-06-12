package africa.zokomart.admin.module.system.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private LoginUserVO user;

    public LoginVO(String token, LoginUserVO user) {
        this.token = token;
        this.user = user;
    }
}
