package africa.zokomart.admin.module.supplierproduct.service;

import africa.zokomart.admin.module.supplierproduct.vo.SupplierProductImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface SupplierProductImportService {

    /**
     * 批量导入供应商产品。
     * @param supplierId 目标供应商
     * @param brandId    目标品牌（必须已授权给该供应商）
     * @param mode       "skip"（默认，编码已存在则跳过）或 "overwrite"（覆盖更新）
     * @param file       CSV 文件（UTF-8，中文表头）
     */
    SupplierProductImportResultVO importCsv(Long supplierId, Long brandId, String mode, MultipartFile file);

    /** 导入抓取到的行（best-effort，skip/overwrite），复用与 CSV 相同的 upsert 规则。 */
    africa.zokomart.admin.module.supplierproduct.vo.SupplierProductImportResultVO importScrapedRows(
            Long supplierId, Long brandId, String mode,
            java.util.List<africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow> rows);
}
