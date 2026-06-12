package africa.zokomart.admin.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器：/api/** 默认要求登录；登录/文档/ping 等放行。
 * 按钮级权限在各 controller 方法上用 @SaCheckPermission 注解控制（Phase 1 起）。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/ping",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**"
                );
    }
}
