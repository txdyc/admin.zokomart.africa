package africa.zokomart.admin.ad;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.ad.client.impl.OpenAiCompatImageClient;
import africa.zokomart.admin.module.ad.entity.AdAiModel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 纯 JUnit（不起 Spring）：用内置 HttpServer 模拟中转站的异常响应形态。 */
class OpenAiCompatImageClientTest {

    private final OpenAiCompatImageClient client = new OpenAiCompatImageClient();
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private AdAiModel model() {
        AdAiModel m = new AdAiModel();
        m.setName("t");
        m.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        m.setApiKey("sk-x");
        m.setModelCode("x");
        return m;
    }

    @Test
    void html_200_response_yields_actionable_base_url_hint_not_jackson_error() {
        // base_url 配错（如缺 /v1）时，中转站官网 SPA 对任意路径回 200 + HTML —— 真实事故形态
        server.createContext("/chat/completions", ex -> {
            byte[] b = "<!doctype html><html><head><title>Packy</title></head></html>"
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });

        BusinessException e = assertThrows(BusinessException.class,
                () -> client.generate(model(), "p", List.of()));
        assertEquals(ResultCode.AD_GENERATE_FAILED.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("/v1"), "应提示检查 base_url（如缺 /v1）：" + e.getMessage());
        assertFalse(e.getMessage().contains("Unexpected character"), "不应裸抛 Jackson 解析错误");
    }

    @Test
    void image_format_with_ref_posts_multipart_to_images_edits_and_downloads_url() throws Exception {
        AtomicReference<String> gotPath = new AtomicReference<>();
        AtomicReference<String> gotContentType = new AtomicReference<>();
        AtomicReference<String> gotBody = new AtomicReference<>();
        int port = server.getAddress().getPort();
        server.createContext("/images/edits", ex -> {
            gotPath.set(ex.getRequestURI().getPath());
            gotContentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
            gotBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] b = ("{\"data\":[{\"url\":\"http://127.0.0.1:" + port + "/img.png\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/img.png", ex -> {
            byte[] b = {1, 2, 3};
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });

        AdAiModel m = model();
        m.setApiFormat("IMAGE");
        Path ref = Files.createTempFile("zk-ref", ".png");
        Files.write(ref, new byte[]{9, 9});
        try {
            List<byte[]> out = client.generate(m, "add stamp", List.of(ref));
            assertEquals(1, out.size());
            assertArrayEquals(new byte[]{1, 2, 3}, out.get(0));
            assertEquals("/images/edits", gotPath.get());
            assertTrue(gotContentType.get().startsWith("multipart/form-data"), gotContentType.get());
            assertTrue(gotBody.get().contains("name=\"model\""));
            assertTrue(gotBody.get().contains("name=\"prompt\""));
            assertTrue(gotBody.get().contains("name=\"image\""));
            // 部分中转站对 response_format 报 Unknown parameter → 必须不发
            assertFalse(gotBody.get().contains("response_format"));
        } finally {
            Files.deleteIfExists(ref);
        }
    }

    @Test
    void image_format_without_ref_posts_json_to_images_generations_and_decodes_b64() {
        AtomicReference<String> gotPath = new AtomicReference<>();
        AtomicReference<String> gotBody = new AtomicReference<>();
        server.createContext("/images/generations", ex -> {
            gotPath.set(ex.getRequestURI().getPath());
            gotBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String b64 = Base64.getEncoder().encodeToString(new byte[]{5, 5});
            byte[] b = ("{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });

        AdAiModel m = model();
        m.setApiFormat("IMAGE");
        List<byte[]> out = client.generate(m, "a red teapot ad", List.of());
        assertEquals(1, out.size());
        assertArrayEquals(new byte[]{5, 5}, out.get(0));
        assertEquals("/images/generations", gotPath.get());
        assertTrue(gotBody.get().contains("\"prompt\""));
        assertFalse(gotBody.get().contains("response_format"));
    }

    @Test
    void non_2xx_surfaces_provider_error_message() {
        server.createContext("/images/generations", ex -> {
            byte[] b = "{\"error\":{\"message\":\"size 参数不合法 (request id: abc)\",\"type\":\"invalid_request_error\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(400, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        AdAiModel m = model();
        m.setApiFormat("IMAGE");
        BusinessException e = assertThrows(BusinessException.class,
                () -> client.generate(m, "p", List.of()));
        assertTrue(e.getMessage().contains("400"), e.getMessage());
        assertTrue(e.getMessage().contains("size 参数不合法"), "应透出中转站错误说明：" + e.getMessage());
    }

    @Test
    void connection_failure_message_names_exception_when_message_null() {
        // 连接被拒（ConnectException 的 getMessage 可能为 null）：报错应含异常类名而非 "null"
        AdAiModel m = model();
        m.setBaseUrl("http://127.0.0.1:9");   // 不可达端口
        BusinessException e = assertThrows(BusinessException.class,
                () -> client.generate(m, "p", List.of()));
        assertEquals(ResultCode.AD_GENERATE_FAILED.getCode(), e.getCode());
        assertFalse(e.getMessage().endsWith("null"), "报错不应以 null 结尾：" + e.getMessage());
    }
}
