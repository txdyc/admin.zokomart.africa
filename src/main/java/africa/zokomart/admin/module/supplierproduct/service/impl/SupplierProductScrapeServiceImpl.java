package africa.zokomart.admin.module.supplierproduct.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
import africa.zokomart.admin.module.supplierproduct.scrape.MorganPriceListParser;
import africa.zokomart.admin.module.supplierproduct.scrape.ScrapeProperties;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductScrapeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SupplierProductScrapeServiceImpl implements SupplierProductScrapeService {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

    private final ScrapeProperties props;

    @Override
    public List<ScrapedProductRow> scrape(String url) {
        URI uri = validate(url);
        String html = fetch(uri);
        List<ScrapedProductRow> rows = MorganPriceListParser.parse(html, url);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.SCRAPE_EMPTY);
        }
        return rows;
    }

    private URI validate(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SCRAPE_URL_NOT_ALLOWED);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null) {
            throw new BusinessException(ResultCode.SCRAPE_URL_NOT_ALLOWED);
        }
        boolean allowed = props.getAllowedHosts().stream()
                .anyMatch(h -> h.equalsIgnoreCase(host.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new BusinessException(ResultCode.SCRAPE_URL_NOT_ALLOWED);
        }
        return uri;
    }

    private String fetch(URI uri) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA)
                .GET()
                .build();
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BusinessException(ResultCode.SCRAPE_FETCH_FAILED);
            }
            return resp.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SCRAPE_FETCH_FAILED);
        }
    }
}
