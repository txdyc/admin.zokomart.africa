package africa.zokomart.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档（Knife4j / OpenAPI3）。访问 /doc.html 查看。
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI zokomartOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("ZokoMart Admin API")
                .description("ZokoMart 独立站后台管理系统接口文档")
                .version("1.0.0"));
    }
}
