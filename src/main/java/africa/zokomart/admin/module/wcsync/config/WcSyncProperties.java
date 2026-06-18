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
    private BigDecimal priceMultiplier = new BigDecimal("1.7");
}
