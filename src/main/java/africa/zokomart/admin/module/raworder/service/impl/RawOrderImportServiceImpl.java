package africa.zokomart.admin.module.raworder.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.raworder.constant.RawOrderStatus;
import africa.zokomart.admin.module.raworder.entity.RawOrder;
import africa.zokomart.admin.module.raworder.mapper.RawOrderMapper;
import africa.zokomart.admin.module.raworder.service.RawOrderImportService;
import africa.zokomart.admin.module.raworder.vo.RawOrderImportResultVO;
import africa.zokomart.admin.module.raworder.vo.RawOrderRowError;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * 原始订单 CSV 导入：UTF-8（容忍 BOM），表头必须含全部 14 列，逐行尽力导入，
 * 坏行记录 {row, productCode, reason} 后继续，不整体回滚。
 */
@Service
@RequiredArgsConstructor
public class RawOrderImportServiceImpl implements RawOrderImportService {

    private static final int MAX_ROWS = 1000;
    /** CSV 表头是外部数据契约，不可改名。 */
    private static final List<String> REQUIRED_HEADERS = List.of(
            "date", "brand", "price", "customer_name", "city", "address", "telephone",
            "product_name", "product_code", "quantity", "status", "cod", "freight", "balance");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final RawOrderMapper rawOrderMapper;

    @Override
    public RawOrderImportResultVO importCsv(MultipartFile file) {
        List<CSVRecord> records = parse(file);
        RawOrderImportResultVO result = new RawOrderImportResultVO();
        result.setTotal(records.size());
        for (CSVRecord rec : records) {
            int row = (int) rec.getRecordNumber() + 1; // 表头为第 1 行
            String code = get(rec, "product_code");
            try {
                rawOrderMapper.insert(toEntity(rec));
                result.setSuccess(result.getSuccess() + 1);
            } catch (BusinessException e) {
                recordError(result, row, code, e.getMessage());
            } catch (Exception e) {
                recordError(result, row, code, "行处理异常: " + e.getMessage());
            }
        }
        return result;
    }

    private RawOrder toEntity(CSVRecord rec) {
        RawOrder o = new RawOrder();
        o.setOrderDate(parseDate(require(rec, "date")));
        o.setBrand(require(rec, "brand"));
        o.setPrice(parseAmount(require(rec, "price"), "price"));
        o.setCustomerName(require(rec, "customer_name"));
        o.setCity(require(rec, "city"));
        o.setAddress(require(rec, "address"));
        o.setTelephone(require(rec, "telephone"));
        o.setProductName(require(rec, "product_name"));
        o.setProductCode(require(rec, "product_code"));
        o.setQuantity(parseQuantity(require(rec, "quantity")));
        o.setStatus(parseStatusOrDefault(get(rec, "status")));
        o.setCod(parseAmount(require(rec, "cod"), "cod"));
        o.setFreight(parseAmountOrZero(get(rec, "freight"), "freight"));
        o.setBalance(parseAmountOrZero(get(rec, "balance"), "balance"));
        return o;
    }

    private List<CSVRecord> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(stripBom(file.getBytes())), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build())) {
            for (String h : REQUIRED_HEADERS) {
                if (!parser.getHeaderMap().containsKey(h)) {
                    throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
                }
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

    private static String require(CSVRecord rec, String column) {
        String v = get(rec, column);
        if (v.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, column + " 列为空");
        }
        return v;
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "date 非法（需 yyyy-MM-dd）: " + s);
        }
    }

    /** 金额列可留空：空则默认 0.00；非空按 parseAmount 规则校验（非负数字）。 */
    private static BigDecimal parseAmountOrZero(String s, String label) {
        if (s.isEmpty()) {
            return new BigDecimal("0.00");
        }
        return parseAmount(s, label);
    }

    private static BigDecimal parseAmount(String s, String label) {
        BigDecimal v;
        try {
            v = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, label + " 非法: " + s);
        }
        if (v.signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, label + " 不能为负: " + s);
        }
        return v;
    }

    private static Integer parseQuantity(String s) {
        int v;
        try {
            v = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "quantity 非法: " + s);
        }
        if (v < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "quantity 不能小于 1: " + s);
        }
        return v;
    }

    /** status 列可留空：空则默认 NOT_DISPATCHED；非空须为 RawOrderStatus.ALL 之一。 */
    private static String parseStatusOrDefault(String s) {
        if (s.isEmpty()) {
            return RawOrderStatus.DEFAULT;
        }
        if (!RawOrderStatus.ALL.contains(s)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "status 非法: " + s);
        }
        return s;
    }

    private static void recordError(RawOrderImportResultVO result, int row, String code, String reason) {
        result.setFailed(result.getFailed() + 1);
        result.getErrors().add(new RawOrderRowError(row, code, reason));
    }
}
