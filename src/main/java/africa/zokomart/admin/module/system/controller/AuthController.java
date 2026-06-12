package africa.zokomart.admin.module.system.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.system.dto.LoginDTO;
import africa.zokomart.admin.module.system.service.AuthService;
import africa.zokomart.admin.module.system.vo.LoginUserVO;
import africa.zokomart.admin.module.system.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @GetMapping("/user-info")
    public Result<LoginUserVO> userInfo() {
        return Result.ok(authService.currentUserInfo());
    }
}
