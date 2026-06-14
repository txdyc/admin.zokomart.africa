package africa.zokomart.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上传配置：根目录、对外 URL 前缀、大小上限、允许的扩展名。
 * 可在 application-local.yml / 环境变量覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    private String dir = "./uploads";
    private String urlPrefix = "/files";
    private long maxSize = 2 * 1024 * 1024L; // 2MB
    private List<String> allowedTypes = List.of("png", "jpg", "jpeg", "webp", "gif");
}
