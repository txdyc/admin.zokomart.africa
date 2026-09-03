package africa.zokomart.admin.module.wcsync.support;

import java.util.List;
import java.util.stream.Collectors;

/** 商品描述中的广告图标记区块：只替换标记内内容，标记外手写描述原样保留。 */
public final class AdDescriptionBlock {

    public static final String START = "<!-- ZOKO-AD:START -->";
    public static final String END = "<!-- ZOKO-AD:END -->";

    private AdDescriptionBlock() { }

    public static String apply(String existing, List<String> imageUrls) {
        String imgs = imageUrls.stream()
                .map(u -> "<img src=\"" + u + "\" style=\"max-width:100%\" />")
                .collect(Collectors.joining("\n"));
        String block = imgs.isEmpty() ? START + "\n" + END : START + "\n" + imgs + "\n" + END;
        String base = existing == null ? "" : existing;
        int s = base.indexOf(START);
        int e = base.indexOf(END);
        if (s >= 0 && e > s) {
            return base.substring(0, s) + block + base.substring(e + END.length());
        }
        return base.isBlank() ? block : base + "\n" + block;
    }
}
