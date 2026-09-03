package africa.zokomart.admin.module.wcsync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** WooCommerce 同步配置（多站点；密钥放 application-local.yml）。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.wc")
public class WcSyncProperties {
    /** Regular Price = 批发价 × 此倍率（全局默认，站点可用同名字段覆盖）。 */
    private BigDecimal regularMultiplier = new BigDecimal("1.75");
    /** Sale Price = 批发价 × 此倍率（全局默认，站点可用同名字段覆盖）。 */
    private BigDecimal saleMultiplier = new BigDecimal("1.5");
    /** 公网访问本机文件的基础 URL（WC sideload 广告图需要可外网访问；与站点无关，全局一份）。 */
    private String publicFileBaseUrl;
    /** 目标站点列表；code 是写入数据库的稳定标识，一经使用不可改名。 */
    private List<WcSite> sites = new ArrayList<>();

    /** 有效 regular 倍率：站点值非空取站点值，否则回退全局（回退规则只实现这一处）。 */
    public BigDecimal effectiveRegularMultiplier(WcSite site) {
        return site != null && site.getRegularMultiplier() != null
                ? site.getRegularMultiplier() : regularMultiplier;
    }

    /** 有效 sale 倍率：站点值非空取站点值，否则回退全局（回退规则只实现这一处）。 */
    public BigDecimal effectiveSaleMultiplier(WcSite site) {
        return site != null && site.getSaleMultiplier() != null
                ? site.getSaleMultiplier() : saleMultiplier;
    }

    @Data
    public static class WcSite {
        private String code;
        private String name;
        private String baseUrl;
        private String consumerKey;
        private String consumerSecret;
        /** 可选：覆盖全局 regular 倍率。 */
        private BigDecimal regularMultiplier;
        /** 可选：覆盖全局 sale 倍率。 */
        private BigDecimal saleMultiplier;

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank()
                    && consumerKey != null && !consumerKey.isBlank()
                    && consumerSecret != null && !consumerSecret.isBlank();
        }
    }
}
