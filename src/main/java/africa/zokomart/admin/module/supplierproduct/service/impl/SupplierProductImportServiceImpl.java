package africa.zokomart.admin.module.supplierproduct.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.mapper.CategoryMapper;
import africa.zokomart.admin.module.basedata.service.BrandService;
import africa.zokomart.admin.module.basedata.service.SupplierBrandService;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.basedata.util.CategoryPathResolver;
import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
import africa.zokomart.admin.module.supplierproduct.dto.SupplierProductSaveDTO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductImportService;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductService;
import africa.zokomart.admin.module.supplierproduct.vo.ImportRowError;
import africa.zokomart.admin.module.supplierproduct.vo.SupplierProductImportResultVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SupplierProductImportServiceImpl implements SupplierProductImportService {

    private static final int MAX_ROWS = 1000;
    private static final String H_NAME = "产品名称";
    private static final String H_CODE = "产品编码";
    private static final String H_CATEGORY = "分类路径";
    private static final String H_WHOLESALE = "批发价";
    private static final String H_RETAIL = "零售价";
    private static final String H_MOQ = "最小采购量";
    private static final String H_IMAGE = "图片URL";
    private static final String H_REMARK = "备注";

    private final SupplierProductService supplierProductService;
    private final SupplierBrandService supplierBrandService;
    private final SupplierService supplierService;
    private final BrandService brandService;
    private final CategoryMapper categoryMapper;

    @Override
    public SupplierProductImportResultVO importCsv(Long supplierId, Long brandId, String mode, MultipartFile file) {
        assertImportable(supplierId, brandId);
        boolean overwrite = "overwrite".equalsIgnoreCase(mode);

        List<CSVRecord> records = parse(file);
        List<Category> categories = categoryMapper.selectList(null);

        SupplierProductImportResultVO result = new SupplierProductImportResultVO();
        result.setTotal(records.size());
        Set<String> seenCodes = new HashSet<>();

        for (CSVRecord rec : records) {
            int line = (int) rec.getRecordNumber() + 1; // 表头为第 1 行
            String code = get(rec, H_CODE);
            try {
                SupplierProductSaveDTO dto = new SupplierProductSaveDTO();
                dto.setName(get(rec, H_NAME));
                dto.setProductCode(code);
                dto.setCategoryId(CategoryPathResolver.resolve(categories, get(rec, H_CATEGORY)));
                dto.setWholesalePrice(parsePrice(get(rec, H_WHOLESALE), "批发价"));
                dto.setRetailPrice(parsePrice(get(rec, H_RETAIL), "零售价"));
                dto.setMinPurchaseQty(parseMoq(get(rec, H_MOQ)));
                dto.setImageUrl(emptyToNull(get(rec, H_IMAGE)));
                dto.setRemark(emptyToNull(get(rec, H_REMARK)));
                applyOutcome(upsertRow(supplierId, brandId, overwrite, seenCodes, dto), result);
            } catch (BusinessException e) {
                recordError(result, line, code, e.getMessage());
            } catch (Exception e) {
                recordError(result, line, code, "行处理异常: " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public SupplierProductImportResultVO importScrapedRows(Long supplierId, Long brandId, String mode,
                                                           List<ScrapedProductRow> rows) {
        assertImportable(supplierId, brandId);
        boolean overwrite = "overwrite".equalsIgnoreCase(mode);

        SupplierProductImportResultVO result = new SupplierProductImportResultVO();
        result.setTotal(rows == null ? 0 : rows.size());
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        Set<String> seenCodes = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            ScrapedProductRow row = rows.get(i);
            int line = i + 1; // URL 导入行号 = 预览表第 i+1 行
            String code = row.getProductCode();
            try {
                SupplierProductSaveDTO dto = new SupplierProductSaveDTO();
                dto.setName(row.getProductName());
                dto.setProductCode(code);
                dto.setWholesalePrice(row.getUnitPrice());
                dto.setImageUrl(row.getImageUrl());
                dto.setQtyPerBox(row.getQtyPerBox());
                dto.setBoxPrice(row.getBoxPrice());
                dto.setStockStatus(row.getStockStatus());
                applyOutcome(upsertRow(supplierId, brandId, overwrite, seenCodes, dto), result);
            } catch (BusinessException e) {
                recordError(result, line, code, e.getMessage());
            } catch (Exception e) {
                recordError(result, line, code, "行处理异常: " + e.getMessage());
            }
        }
        return result;
    }

    private enum RowOutcome { CREATED, UPDATED, SKIPPED }

    private void assertImportable(Long supplierId, Long brandId) {
        if (supplierService.getById(supplierId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        if (brandService.getById(brandId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "品牌不存在");
        }
        if (!supplierBrandService.isAuthorized(supplierId, brandId)) {
            throw new BusinessException(ResultCode.BRAND_NOT_AUTHORIZED);
        }
    }

    /** 校验必填+批次内查重，按 skip/overwrite 落库；返回结果或抛业务异常。设置 supplierId/brandId/status。 */
    private RowOutcome upsertRow(Long supplierId, Long brandId, boolean overwrite,
                                 Set<String> seenCodes, SupplierProductSaveDTO dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        String code = dto.getProductCode() == null ? "" : dto.getProductCode().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品名称为空");
        }
        if (code.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品编码为空");
        }
        if (!seenCodes.add(code)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同一批次内编码重复");
        }
        dto.setSupplierId(supplierId);
        dto.setBrandId(brandId);
        if (dto.getStatus() == null) {
            dto.setStatus(1);
        }
        SupplierProduct existing = supplierProductService.findBySupplierAndCode(supplierId, code);
        if (existing != null) {
            if (!overwrite) {
                return RowOutcome.SKIPPED;
            }
            dto.setId(existing.getId());
            supplierProductService.updateSupplierProduct(dto);
            return RowOutcome.UPDATED;
        }
        supplierProductService.createSupplierProduct(dto);
        return RowOutcome.CREATED;
    }

    private void applyOutcome(RowOutcome o, SupplierProductImportResultVO result) {
        switch (o) {
            case CREATED -> result.setCreated(result.getCreated() + 1);
            case UPDATED -> result.setUpdated(result.getUpdated() + 1);
            case SKIPPED -> result.setSkipped(result.getSkipped() + 1);
        }
    }

    private void recordError(SupplierProductImportResultVO result, int line, String code, String reason) {
        result.setFailed(result.getFailed() + 1);
        result.getErrors().add(new ImportRowError(line, code, reason));
    }

    private List<CSVRecord> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(stripBom(file.getBytes())), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build())) {
            if (!parser.getHeaderMap().containsKey(H_NAME) || !parser.getHeaderMap().containsKey(H_CODE)) {
                throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
            }
            List<CSVRecord> records = parser.getRecords();
            if (records.size() > MAX_ROWS) {
                throw new BusinessException(ResultCode.IMPORT_TOO_MANY_ROWS);
            }
            return records;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
    }

    private static byte[] stripBom(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            byte[] out = new byte[b.length - 3];
            System.arraycopy(b, 3, out, 0, out.length);
            return out;
        }
        return b;
    }

    /** 取列值并 trim；列不存在或为空返回 ""。 */
    private static String get(CSVRecord rec, String column) {
        if (!rec.isMapped(column) || !rec.isSet(column)) {
            return "";
        }
        String v = rec.get(column);
        return v == null ? "" : v.trim();
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal parsePrice(String s, String label) {
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal v;
        try {
            v = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, label + "非法: " + s);
        }
        if (v.signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, label + "不能为负");
        }
        return v;
    }

    private static Integer parseMoq(String s) {
        if (s.isEmpty()) {
            return 1;
        }
        int v;
        try {
            v = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "最小采购量非法: " + s);
        }
        if (v < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "最小采购量不能小于 1");
        }
        return v;
    }
}
