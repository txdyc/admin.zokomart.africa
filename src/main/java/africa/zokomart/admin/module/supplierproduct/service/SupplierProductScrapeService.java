package africa.zokomart.admin.module.supplierproduct.service;

import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;

import java.util.List;

public interface SupplierProductScrapeService {

    /** 抓取并解析供应商价格表 URL（host 受白名单限制），返回产品行（不入库）。 */
    List<ScrapedProductRow> scrape(String url);
}
