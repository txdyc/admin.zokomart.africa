package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.wcsync.support.AdDescriptionBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdDescriptionBlockTest {

    @Test
    void appends_block_to_empty_and_existing_description() {
        String d1 = AdDescriptionBlock.apply("", List.of("https://wc/a.jpg"));
        assertTrue(d1.startsWith("<!-- ZOKO-AD:START -->"));
        assertTrue(d1.contains("<img src=\"https://wc/a.jpg\""));

        String d2 = AdDescriptionBlock.apply("<p>手写介绍</p>", List.of("https://wc/a.jpg"));
        assertTrue(d2.startsWith("<p>手写介绍</p>"));
        assertTrue(d2.contains("ZOKO-AD:START"));
    }

    @Test
    void replaces_existing_block_idempotently_preserving_manual_text() {
        String v1 = AdDescriptionBlock.apply("<p>前</p>", List.of("https://wc/a.jpg"));
        String withTail = v1 + "<p>后（手写追加）</p>";
        String v2 = AdDescriptionBlock.apply(withTail, List.of("https://wc/b.jpg"));
        assertTrue(v2.contains("<p>前</p>"));
        assertTrue(v2.contains("<p>后（手写追加）</p>"));
        assertTrue(v2.contains("https://wc/b.jpg"));
        assertFalse(v2.contains("https://wc/a.jpg"));                 // 旧图被替换
        assertEquals(v2, AdDescriptionBlock.apply(v2, List.of("https://wc/b.jpg")));   // 幂等
    }

    @Test
    void empty_urls_writes_empty_block() {
        String v1 = AdDescriptionBlock.apply("<p>X</p>", List.of("https://wc/a.jpg"));
        String v2 = AdDescriptionBlock.apply(v1, List.of());
        assertTrue(v2.contains("<p>X</p>"));
        assertFalse(v2.contains("<img"));
        assertTrue(v2.contains("ZOKO-AD:START"));
    }
}
