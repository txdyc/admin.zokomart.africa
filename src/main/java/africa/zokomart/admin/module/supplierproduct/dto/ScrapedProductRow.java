package africa.zokomart.admin.module.supplierproduct.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 从供应商价格表 URL 抓取到的一行（scrape 出参，import-scraped 入参元素）。 */
@Data
public class ScrapedProductRow {
    private String productName;
    private String productCode;
    private Integer qtyPerBox;
    private String imageUrl;
    private BigDecimal unitPrice;
    private BigDecimal boxPrice;
    private String stockStatus;
}
