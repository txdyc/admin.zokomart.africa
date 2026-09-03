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

import java.io.ByteArrayOutputStream;
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
import java.util.UUID;

/**
 * 聚合平台生图客户端。按模型的 apiFormat 分发：
 *  - CHAT：OpenAI 兼容 chat/completions 多模态（参考图以 data-URI 入消息）
 *  - IMAGE：OpenAI Images API —— 有参考图走 /images/edits（multipart），
 *           无参考图走 /images/generations（JSON）；响应兼容 url / b64_json
 */
@Component
@Slf4j
public class OpenAiCompatImageClient implements AiImageClient {

    public static final String FORMAT_IMAGE = "IMAGE";

    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public List<byte[]> generate(AdAiModel model, String prompt, List<Path> refImages) {
        if (FORMAT_IMAGE.equalsIgnoreCase(model.getApiFormat())) {
            return generateViaImagesApi(model, prompt, refImages);
        }
        return generateViaChat(model, prompt, refImages);
    }

    // ---- CHAT：chat/completions 多模态 ----

    private List<byte[]> generateViaChat(AdAiModel model, String prompt, List<Path> refImages) {
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
        JsonNode resp = postForJson(model, "/chat/completions", "application/json",
                HttpRequest.BodyPublishers.ofString(toJsonString(body), StandardCharsets.UTF_8));

        List<String> payloads = AiImageResponseParser.extractPayloads(resp);
        if (payloads.isEmpty()) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "模型未返回图片");
        }
        List<byte[]> out = new ArrayList<>();
        for (String p : payloads) out.add(toBytes(p));
        return out;
    }

    // ---- IMAGE：OpenAI Images API ----

    private List<byte[]> generateViaImagesApi(AdAiModel model, String prompt, List<Path> refImages) {
        JsonNode resp;
        if (refImages.isEmpty()) {
            ObjectNode body = om.createObjectNode();
            body.put("model", model.getModelCode());
            body.put("prompt", prompt);
            body.put("n", 1);
            // 不传 response_format：部分中转站（如 packyapi gpt-image-2）报 Unknown parameter；
            // 默认返回 b64_json 或 url，解析端两者都兼容
            resp = postForJson(model, "/images/generations", "application/json",
                    HttpRequest.BodyPublishers.ofString(toJsonString(body), StandardCharsets.UTF_8));
        } else {
            // 中转站建议一次只传 1 张参考图，取第一张（多张时其余忽略）
            Path ref = refImages.get(0);
            String boundary = "----zokoad" + UUID.randomUUID().toString().replace("-", "");
            byte[] multipart = buildMultipart(boundary, model.getModelCode(), prompt, ref);
            resp = postForJson(model, "/images/edits", "multipart/form-data; boundary=" + boundary,
                    HttpRequest.BodyPublishers.ofByteArray(multipart));
        }

        List<byte[]> out = new ArrayList<>();
        for (JsonNode d : resp.path("data")) {
            if (d.hasNonNull("b64_json")) {
                out.add(Base64.getDecoder().decode(d.path("b64_json").asText()));
            } else if (d.hasNonNull("url")) {
                out.add(toBytes(d.path("url").asText()));
            }
        }
        if (out.isEmpty()) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "模型未返回图片");
        }
        return out;
    }

    private byte[] buildMultipart(String boundary, String modelCode, String prompt, Path image) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            writeFormField(buf, boundary, "model", modelCode);
            writeFormField(buf, boundary, "prompt", prompt);
            String filename = image.getFileName().toString();
            String mime = mimeOf(filename);
            buf.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            buf.write(Files.readAllBytes(image));
            buf.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return buf.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "读取参考图失败");
        }
    }

    private void writeFormField(ByteArrayOutputStream buf, String boundary, String name, String value)
            throws java.io.IOException {
        buf.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    // ---- 公共 HTTP：状态码 + JSON 守卫 ----

    private JsonNode postForJson(AdAiModel model, String path, String contentType,
                                 HttpRequest.BodyPublisher publisher) {
        String base = model.getBaseUrl().trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .header("Content-Type", contentType)
                    .POST(publisher)
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() < 200 || r.statusCode() >= 300) {
                // 透出 provider 的 error.message 便于排错（响应体不含我方 api key）
                String detail = extractErrorMessage(r.body());
                log.warn("AI generate HTTP {} from model {} path {}: {}",
                        r.statusCode(), model.getName(), path, detail);
                throw new BusinessException(ResultCode.AD_GENERATE_FAILED,
                        "模型接口返回 " + r.statusCode() + (detail.isEmpty() ? "" : ": " + detail));
            }
            try {
                return om.readTree(r.body());
            } catch (Exception je) {
                // 2xx 但响应体不是 JSON：典型为 base_url 配错（缺 /v1），打到了中转站官网页
                String ct = r.headers().firstValue("Content-Type").orElse("unknown");
                log.warn("AI generate non-JSON response from model {}: status={} contentType={}",
                        model.getName(), r.statusCode(), ct);
                throw new BusinessException(ResultCode.AD_GENERATE_FAILED,
                        "模型接口返回了非 JSON 内容（Content-Type: " + ct
                                + "）。请检查模型的 Base URL 是否为 OpenAI 兼容接口地址，通常需以 /v1 结尾");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "请求模型失败: " + reason);
        }
    }

    /** 从 provider 错误响应中提取人类可读说明（OpenAI 风格 error.message，或顶层 message），截断防超长。 */
    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode n = om.readTree(body);
            String msg = n.path("error").path("message").asText("");
            if (msg.isEmpty()) msg = n.path("message").asText("");
            return msg.length() > 300 ? msg.substring(0, 300) : msg;
        } catch (Exception e) {
            return "";   // 非 JSON 错误体（如 HTML）不透出
        }
    }

    private String toJsonString(JsonNode node) {
        try {
            return om.writeValueAsString(node);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "构造请求失败");
        }
    }

    private String toDataUri(Path p) {
        try {
            return "data:" + mimeOf(p.getFileName().toString()) + ";base64,"
                    + Base64.getEncoder().encodeToString(Files.readAllBytes(p));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AD_GENERATE_FAILED, "读取参考图失败");
        }
    }

    private String mimeOf(String filename) {
        String name = filename.toLowerCase();
        return name.endsWith(".png") ? "image/png"
                : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
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
