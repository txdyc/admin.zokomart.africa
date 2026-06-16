package africa.zokomart.admin.supplierproduct;

import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
import africa.zokomart.admin.module.supplierproduct.scrape.MorganPriceListParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MorganPriceListParserTest {

    private String fixture() throws Exception {
        return Files.readString(Path.of("src/test/resources/morgan-sample.html"), StandardCharsets.UTF_8);
    }

    @Test
    void parses_rows_skips_missing_name_and_absolutizes_image() throws Exception {
        List<ScrapedProductRow> rows =
                MorganPriceListParser.parse(fixture(), "https://morgan.dzncm.com/price81469/");

        assertEquals(2, rows.size(), "缺名称的第三行应被跳过");

        ScrapedProductRow a = rows.get(0);
        assertEquals("Electric Juicer", a.getProductName());
        assertEquals("JC-3028S", a.getProductCode());
        assertEquals(6, a.getQtyPerBox());
        assertEquals("https://morgan.dzncm.com/uploadfile/202601/eafe.jpg", a.getImageUrl());
        assertEquals(0, new BigDecimal("220").compareTo(a.getUnitPrice()));
        assertEquals(0, new BigDecimal("1320").compareTo(a.getBoxPrice()));
        assertEquals("Stock Sufficient", a.getStockStatus());

        ScrapedProductRow b = rows.get(1);
        assertEquals("Blender", b.getProductName());
        assertEquals(12, b.getQtyPerBox());
        assertEquals("Stock Less", b.getStockStatus());
    }
}
