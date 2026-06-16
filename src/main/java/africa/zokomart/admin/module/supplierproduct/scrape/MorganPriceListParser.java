package africa.zokomart.admin.module.supplierproduct.scrape;

import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Morgan 价格表（morgan.dzncm.com）专用解析器：把表格 HTML 解析为产品行。
 * 弹框产品图取缩略图 img 的 data-image-large 属性，按 baseUrl 补全为绝对 URL。
 * 纯函数，不联网。
 */
public final class MorganPriceListParser {

    private static final Pattern INT = Pattern.compile("\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    private MorganPriceListParser() {
    }

    public static List<ScrapedProductRow> parse(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        List<ScrapedProductRow> out = new ArrayList<>();
        for (Element tr : doc.select("table tbody tr")) {
            Elements tds = tr.select("td");
            if (tds.size() < 8) {
                continue;
            }
            String name = tds.get(1).text().trim();
            String code = tds.get(2).text().trim();
            if (name.isEmpty() || code.isEmpty()) {
                continue;
            }
            ScrapedProductRow r = new ScrapedProductRow();
            r.setProductName(name);
            r.setProductCode(code);
            r.setQtyPerBox(firstInt(tds.get(3).text()));
            Element img = tds.get(4).selectFirst("img[data-image-large]");
            if (img != null) {
                r.setImageUrl(img.absUrl("data-image-large"));
            }
            r.setUnitPrice(firstDecimal(tds.get(5).text()));
            r.setBoxPrice(firstDecimal(tds.get(6).text()));
            String stock = tds.get(7).text().trim();
            r.setStockStatus(stock.isEmpty() ? null : stock);
            out.add(r);
        }
        return out;
    }

    private static Integer firstInt(String s) {
        Matcher m = INT.matcher(s);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    private static BigDecimal firstDecimal(String s) {
        Matcher m = DECIMAL.matcher(s);
        return m.find() ? new BigDecimal(m.group()) : null;
    }
}
