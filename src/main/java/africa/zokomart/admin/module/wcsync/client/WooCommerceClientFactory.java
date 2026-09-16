package africa.zokomart.admin.module.wcsync.client;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.wcsync.client.impl.WooCommerceClientImpl;
import africa.zokomart.admin.module.wcsync.config.WcSyncProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 按站点创建/缓存 WC 客户端；共享 HttpClient 与 ObjectMapper。 */
@Component
public class WooCommerceClientFactory {

    private final WcSyncProperties props;
    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, WooCommerceClient> clients = new ConcurrentHashMap<>();

    public WooCommerceClientFactory(WcSyncProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
    }

    /** 按站点 code 取客户端；code 未知抛 WC_NOT_CONFIGURED。 */
    public WooCommerceClient forSite(String code) {
        return clients.computeIfAbsent(code, c -> new WooCommerceClientImpl(site(c), om, http));
    }

    /** 查站点配置；code 未知抛 WC_NOT_CONFIGURED。 */
    public WcSyncProperties.WcSite site(String code) {
        return props.getSites().stream()
                .filter(s -> s.getCode() != null && s.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.WC_NOT_CONFIGURED,
                        "未知或未配置的站点: " + code));
    }

    public List<WcSyncProperties.WcSite> sites() {
        return props.getSites();
    }

    /** 站点存在且 base-url/key/secret 齐全。 */
    public boolean configured(String code) {
        return props.getSites().stream()
                .anyMatch(s -> s.getCode() != null && s.getCode().equals(code) && s.configured());
    }
}
