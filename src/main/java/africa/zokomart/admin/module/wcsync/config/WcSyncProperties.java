package africa.zokomart.admin.module.wcsync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** WooCommerce 同步配置（密钥放 application-local.yml）。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.wc")
public class WcSyncProperties {
    private String baseUrl;
    private String consumerKey;
    private String consumerSecret;
    /** Regular Price = 批发价 × 此倍率。 */
    private BigDecimal regularMultiplier = new BigDecimal("1.75");
    /** Sale Price = 批发价 × 此倍率。 */
    private BigDecimal saleMultiplier = new BigDecimal("1.5");
    /** 公网访问本机文件的基础 URL（WC sideload 广告图需要可外网访问）。 */
    private String publicFileBaseUrl;
}
