package africa.zokomart.admin.module.supplierproduct.scrape;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** 抓取白名单：仅允许这些 host 被抓取（防 SSRF）。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.scrape")
public class ScrapeProperties {
    private List<String> allowedHosts = List.of("morgan.dzncm.com");
}
