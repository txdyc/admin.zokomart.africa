package africa.zokomart.admin.ad;

import africa.zokomart.admin.module.ad.client.AiImageResponseParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiImageResponseParserTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode j(String s) throws Exception { return om.readTree(s); }

    @Test
    void extracts_openrouter_style_images_array() throws Exception {
        JsonNode resp = j("""
            {"choices":[{"message":{"content":"here you go",
              "images":[{"type":"image_url","image_url":{"url":"data:image/png;base64,AAAA"}},
                        {"type":"image_url","image_url":{"url":"https://cdn.agg/img2.png"}}]}}]}
            """);
        assertEquals(List.of("data:image/png;base64,AAAA", "https://cdn.agg/img2.png"),
                AiImageResponseParser.extractPayloads(resp));
    }

    @Test
    void extracts_inline_data_uri_from_content() throws Exception {
        JsonNode resp = j("""
            {"choices":[{"message":{"content":"result: data:image/jpeg;base64,QkJC done"}}]}
            """);
        assertEquals(List.of("data:image/jpeg;base64,QkJC"),
                AiImageResponseParser.extractPayloads(resp));
    }

    @Test
    void extracts_markdown_and_bare_urls_from_content() throws Exception {
        JsonNode resp = j("""
            {"choices":[{"message":{"content":"![img](https://f.agg/a1.png) and https://f.agg/a2.jpeg"}}]}
            """);
        assertEquals(List.of("https://f.agg/a1.png", "https://f.agg/a2.jpeg"),
                AiImageResponseParser.extractPayloads(resp));
    }

    @Test
    void empty_or_textonly_response_yields_empty_list() throws Exception {
        assertTrue(AiImageResponseParser.extractPayloads(j("{}")).isEmpty());
        assertTrue(AiImageResponseParser.extractPayloads(
                j("{\"choices\":[{\"message\":{\"content\":\"sorry, no image\"}}]}")).isEmpty());
    }
}
