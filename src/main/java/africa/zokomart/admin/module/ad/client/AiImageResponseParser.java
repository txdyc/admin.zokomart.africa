package africa.zokomart.admin.module.ad.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聚合平台 chat/completions 生图响应解析：按序探测三种常见形态，
 * 返回图片载体列表（data-URI 或 http(s) URL）。找不到返回空 List。
 */
public final class AiImageResponseParser {

    private static final Pattern DATA_URI =
            Pattern.compile("data:image/[a-zA-Z+]+;base64,[A-Za-z0-9+/=]+");
    private static final Pattern MD_IMG =
            Pattern.compile("!\\[[^\\]]*]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BARE_URL =
            Pattern.compile("https?://\\S+?\\.(?:png|jpe?g|webp|gif)(?=\\s|$|[)\\]\"'])",
                    Pattern.CASE_INSENSITIVE);

    private AiImageResponseParser() { }

    public static List<String> extractPayloads(JsonNode resp) {
        List<String> out = new ArrayList<>();
        JsonNode message = resp.path("choices").path(0).path("message");

        // 形态 a：message.images[].image_url.url（OpenRouter 风格）
        for (JsonNode img : message.path("images")) {
            String url = img.path("image_url").path("url").asText("");
            if (!url.isEmpty()) out.add(url);
        }
        if (!out.isEmpty()) return out;

        String content = message.path("content").asText("");
        if (content.isEmpty()) return out;

        // 形态 b：content 内嵌 base64 data-URI
        Matcher m = DATA_URI.matcher(content);
        while (m.find()) out.add(m.group());
        if (!out.isEmpty()) return out;

        // 形态 c：markdown 图片 / 裸图片 URL
        Matcher md = MD_IMG.matcher(content);
        while (md.find()) out.add(md.group(1));
        String stripped = MD_IMG.matcher(content).replaceAll("");   // 防 markdown URL 被裸 URL 正则重复命中
        Matcher bare = BARE_URL.matcher(stripped);
        while (bare.find()) out.add(bare.group());
        return out;
    }
}
