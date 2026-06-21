package africa.zokomart.admin.module.wcsync.client.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WcProductRef;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.config.WcSyncProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class WooCommerceClientImpl implements WooCommerceClient {

    private final WcSyncProperties props;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public boolean configured() {
        return StringUtils.hasText(props.getBaseUrl())
                && StringUtils.hasText(props.getConsumerKey())
                && StringUtils.hasText(props.getConsumerSecret());
    }

    private String base() {
        String b = props.getBaseUrl().trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    private String authHeader() {
        String raw = props.getConsumerKey() + ":" + props.getConsumerSecret();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode send(String method, String path, JsonNode body) {
        try {
            return sendOnce(method, path, body);
        } catch (BusinessException e) {
            throw e;   // WC 返回的 4xx/5xx 已是业务异常，不重试
        } catch (Exception e) {
            // 网络/超时异常：仅对幂等方法（GET/PUT/DELETE）重试 1 次。
            // POST 非幂等：重试可能在 WC 创建重复产品，故直接以业务异常上抛，不重试。
            if ("POST".equalsIgnoreCase(method)) {
                throw new BusinessException(ResultCode.WC_API_ERROR, "WC 请求异常: " + e.getMessage());
            }
            try {
                return sendOnce(method, path, body);
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e2) {
                throw new BusinessException(ResultCode.WC_API_ERROR, "WC 请求异常: " + e2.getMessage());
            }
        }
    }

    private JsonNode sendOnce(String method, String path, JsonNode body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json");
        HttpRequest.BodyPublisher pub = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body), StandardCharsets.UTF_8);
        b.method(method, pub);
        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new BusinessException(ResultCode.WC_API_ERROR,
                    "WC " + method + " " + path + " -> " + resp.statusCode());
        }
        return resp.body() == null || resp.body().isEmpty() ? om.createObjectNode() : om.readTree(resp.body());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public long ensureCategory(String name, long parentWcId) {
        JsonNode arr = send("GET", "/wp-json/wc/v3/products/categories?search=" + enc(name) + "&per_page=100", null);
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                if (name.equalsIgnoreCase(n.path("name").asText()) && n.path("parent").asLong() == parentWcId) {
                    return n.path("id").asLong();
                }
            }
        }
        ObjectNode body = om.createObjectNode();
        body.put("name", name);
        body.put("parent", parentWcId);
        return send("POST", "/wp-json/wc/v3/products/categories", body).path("id").asLong();
    }

    @Override
    public long ensureBrand(String name) {
        JsonNode arr = send("GET", "/wp-json/wc/v3/products/brands?search=" + enc(name) + "&per_page=100", null);
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                if (name.equalsIgnoreCase(n.path("name").asText())) {
                    return n.path("id").asLong();
                }
            }
        }
        ObjectNode body = om.createObjectNode();
        body.put("name", name);
        return send("POST", "/wp-json/wc/v3/products/brands", body).path("id").asLong();
    }

    @Override
    public Long findProductIdBySku(String sku) {
        JsonNode arr = send("GET", "/wp-json/wc/v3/products?sku=" + enc(sku), null);
        if (arr.isArray() && arr.size() > 0) {
            return arr.get(0).path("id").asLong();
        }
        return null;
    }

    private ObjectNode toJson(WcProduct p) {
        ObjectNode body = om.createObjectNode();
        body.put("name", p.getName());
        body.put("type", "simple");
        body.put("sku", p.getSku());
        body.put("regular_price", p.getRegularPrice());
        body.put("sale_price", p.getSalePrice());
        body.put("manage_stock", true);
        body.put("stock_quantity", p.getStockQuantity());
        body.put("status", p.getStatus());
        if (p.getCategoryId() > 0) {
            ArrayNode cats = body.putArray("categories");
            cats.addObject().put("id", p.getCategoryId());
        }
        if (p.getBrandWcId() > 0) {
            ArrayNode brands = body.putArray("brands");
            brands.addObject().put("id", p.getBrandWcId());
        }
        // 仅当 imageSrc 非空才传 images（用 src 上传一次）；为 null 则不传，WC 保留现有图、不重复 sideload。
        if (StringUtils.hasText(p.getImageSrc())) {
            ArrayNode imgs = body.putArray("images");
            imgs.addObject().put("src", p.getImageSrc());
        }
        return body;
    }

    private WcProductRef parseRef(JsonNode resp) {
        long id = resp.path("id").asLong();
        JsonNode imgs = resp.path("images");
        Long imageId = (imgs.isArray() && imgs.size() > 0 && imgs.get(0).hasNonNull("id"))
                ? imgs.get(0).path("id").asLong() : null;
        return new WcProductRef(id, imageId);
    }

    @Override
    public WcProductRef createProduct(WcProduct product) {
        return parseRef(send("POST", "/wp-json/wc/v3/products", toJson(product)));
    }

    @Override
    public WcProductRef updateProduct(long wcProductId, WcProduct product) {
        return parseRef(send("PUT", "/wp-json/wc/v3/products/" + wcProductId, toJson(product)));
    }

    @Override
    public Long findProductMainImageId(long wcProductId) {
        JsonNode resp = send("GET", "/wp-json/wc/v3/products/" + wcProductId, null);
        JsonNode imgs = resp.path("images");
        return (imgs.isArray() && imgs.size() > 0 && imgs.get(0).hasNonNull("id"))
                ? imgs.get(0).path("id").asLong() : null;
    }
}
