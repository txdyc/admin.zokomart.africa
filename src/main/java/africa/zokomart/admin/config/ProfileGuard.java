package africa.zokomart.admin.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 启动期兜底：prod 与 local 不得同时激活。
 * <p>
 * local 是开发机的密钥 profile（backend/config/application-local.yml，不入库、不进 jar）。
 * 若生产漏设或写错 SPRING_PROFILES_ACTIVE，application.yml 的默认值 dev,local 会生效，
 * 服务不报错就用开发机的库和密钥跑生产流量——宁可启动失败也不能静默跑错配置。
 */
@Component
public class ProfileGuard {

    private final Environment env;

    public ProfileGuard(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void check() {
        verify(env.getActiveProfiles());
    }

    static void verify(String[] activeProfiles) {
        List<String> active = Arrays.asList(activeProfiles);
        if (active.contains("prod") && active.contains("local")) {
            throw new IllegalStateException(
                    "启动配置错误：profile prod 与 local 同时激活（当前：" + String.join(",", active)
                            + "）。local 仅供开发机注入密钥，生产请只激活 prod，"
                            + "密钥用环境变量注入（见 deploy/backend/zokomart-admin.env.example）。");
        }
    }
}
