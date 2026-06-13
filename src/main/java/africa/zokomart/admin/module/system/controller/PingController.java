package africa.zokomart.admin.module.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import africa.zokomart.admin.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康自检接口（无需登录）。
 */
@RestController
@RequestMapping("/api")
@Tag(name = "健康检查")
public class PingController {

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }
}
