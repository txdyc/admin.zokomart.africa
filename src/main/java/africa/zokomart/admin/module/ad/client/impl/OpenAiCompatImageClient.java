package africa.zokomart.admin.module.ad.client.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.ad.client.AiImageClient;
import africa.zokomart.admin.module.ad.client.AiImageResponseParser;
import africa.zokomart.admin.module.ad.entity.AdAiModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@Slf4j
public class OpenAiCompatImageClient implements AiImageClient {

    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public List<byte[]> generate(AdAiModel model, String prompt, List<Path> refImages) {
        ObjectNode body = om.createObjectNode();
        body.put("model", model.getModelCode());
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "text").put("text", prompt);
        for (Path p : refImages) {
            content.addObject().put("type", "image_url")
                    .putObject("image_url").put("url", toDataUri(p));
        }

        String base = model.getBaseUrl().trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        JsonNode resp;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() < 200 || r.statusCode() >= 300) {
                // 错误响应体可能含敏感信息，只记状态码 + 截断片段，绝不打印 api key
                log.warn("AI generate HTTP {} from model {}", r.statusCode(), model.getName());
                throw new BusinessException(ResultCode.AD_GENERATE_FAILED,
                        "模型接口返回 " + r.statusCode());
            }
            resp = om.readTree(r.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "请求模型失败: " + e.getMessage());
        }

        List<String> payloads = AiImageResponseParser.extractPayloads(resp);
        if (payloads.isEmpty()) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "模型未返回图片");
        }
        List<byte[]> out = new ArrayList<>();
        for (String p : payloads) out.add(toBytes(p));
        return out;
    }

    private String toDataUri(Path p) {
        try {
            String name = p.getFileName().toString().toLowerCase();
            String mime = name.endsWith(".png") ? "image/png"
                    : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(p));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "读取参考图失败");
        }
    }

    private byte[] toBytes(String payload) {
        if (payload.startsWith("data:")) {
            return Base64.getDecoder().decode(payload.substring(payload.indexOf(",") + 1));
        }
        try {   // http(s) URL：服务端下载
            HttpRequest req = HttpRequest.newBuilder(URI.create(payload))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() < 200 || r.statusCode() >= 300) {
                throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "下载生成图失败 " + r.statusCode());
            }
            return r.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "下载生成图失败: " + e.getMessage());
        }
    }
}
