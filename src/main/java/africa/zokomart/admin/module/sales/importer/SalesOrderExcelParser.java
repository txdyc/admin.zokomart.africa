package africa.zokomart.admin.module.sales.importer;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售订单 Excel(.xlsx) 解析：读第一个 sheet，第 1 行为表头。
 * 表头缺必需列 / 行数超限 / 非 xlsx → 整文件拒绝；
 * 单行字段非法 → 该行带 error 返回，由调用方决定让整个订单失败。
 */
public class SalesOrderExcelParser {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter SLASH =
            DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT);

    public List<SalesImportRow> parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        List<Row> raw = readRows(bytes);
        if (raw.isEmpty()) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        Map<String, Integer> idx = headerIndex(raw.get(0));
        List<Row> data = raw.subList(1, raw.size());
        if (data.size() > SalesImportConst.MAX_ROWS) {
            throw new BusinessException(ResultCode.IMPORT_TOO_MANY_ROWS);
        }
        List<SalesImportRow> out = new ArrayList<>(data.size());
        for (Row r : data) {
            if (isBlankRow(r, idx)) {
                continue;
            }
            out.add(toRow(r, idx));
        }
        return out;
    }

    private List<Row> readRows(byte[] bytes) {
        try (ReadableWorkbook wb = new ReadableWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getFirstSheet();
            if (sheet == null) {
                throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
            }
            return sheet.read();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
    }

    /** 表头列名 -> 列下标。缺任一必需列 → 整文件拒绝。 */
    private Map<String, Integer> headerIndex(Row header) {
        Map<String, Integer> idx = new HashMap<>();
        for (int c = 0; c < header.getCellCount(); c++) {
            String name = SalesImportConst.normalizeHeader(text(header, c));
            if (!name.isEmpty()) {
                idx.putIfAbsent(name, c);
            }
        }
        for (String required : SalesImportConst.REQUIRED_COLUMNS) {
            if (!idx.containsKey(SalesImportConst.normalizeHeader(required))) {
                throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
            }
        }
        return idx;
    }

    /** Excel 末尾常留空行；所有已知列都空的行直接丢弃，不计入行数也不报错。 */
    private boolean isBlankRow(Row r, Map<String, Integer> idx) {
        for (int col : idx.values()) {
            if (!text(r, col).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private SalesImportRow toRow(Row r, Map<String, Integer> idx) {
        int rowNum = r.getRowNum();
        String name = get(r, idx, SalesImportConst.COL_CUSTOMER_NAME);
        String phone = get(r, idx, SalesImportConst.COL_PHONE);
        String address = get(r, idx, SalesImportConst.COL_ADDRESS);
        LocalDate date = null;
        try {
            date = parseDate(r, idx);
            String orderId = require(get(r, idx, SalesImportConst.COL_ORDER_ID), SalesImportConst.COL_ORDER_ID);
            BigDecimal price = parsePrice(get(r, idx, SalesImportConst.COL_SALE_PRICE));
            require(name, SalesImportConst.COL_CUSTOMER_NAME);
            require(address, SalesImportConst.COL_ADDRESS);
            require(phone, SalesImportConst.COL_PHONE);
            String code = require(get(r, idx, SalesImportConst.COL_PRODUCT_CODE), SalesImportConst.COL_PRODUCT_CODE);
            int qty = parseQty(get(r, idx, SalesImportConst.COL_QUANTITY));
            return new SalesImportRow(rowNum, orderId, price, name,
                    get(r, idx, SalesImportConst.COL_CITY), address, phone,
                    get(r, idx, SalesImportConst.COL_PRODUCT_NAME), code, qty, date, null);
        } catch (IllegalArgumentException e) {
            return SalesImportRow.error(rowNum, name, phone, address, date, e.getMessage());
        }
    }

    private static String require(String v, String column) {
        if (v.isEmpty()) {
            throw new IllegalArgumentException(column + " 为空");
        }
        return v;
    }

    private static BigDecimal parsePrice(String s) {
        if (s.isEmpty()) {
            throw new IllegalArgumentException(SalesImportConst.COL_SALE_PRICE + " 为空");
        }
        BigDecimal v;
        try {
            v = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(SalesImportConst.COL_SALE_PRICE + " 非法: " + s);
        }
        if (v.signum() < 0) {
            throw new IllegalArgumentException(SalesImportConst.COL_SALE_PRICE + " 不能为负: " + s);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static int parseQty(String s) {
        if (s.isEmpty()) {
            throw new IllegalArgumentException(SalesImportConst.COL_QUANTITY + " 为空");
        }
        int v;
        try {
            v = new BigDecimal(s).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException(SalesImportConst.COL_QUANTITY + " 非法: " + s);
        }
        if (v < 1) {
            throw new IllegalArgumentException(SalesImportConst.COL_QUANTITY + " 不能小于 1: " + s);
        }
        return v;
    }

    /** 日期样式单元格 → 直接取；纯数字 → Excel 序列号；文本 → 只认 yyyy-MM-dd 与 yyyy/M/d。 */
    private LocalDate parseDate(Row r, Map<String, Integer> idx) {
        Integer c = idx.get(SalesImportConst.normalizeHeader(SalesImportConst.COL_ORDER_DATE));
        String s = get(r, idx, SalesImportConst.COL_ORDER_DATE);
        if (s.isEmpty()) {
            throw new IllegalArgumentException(SalesImportConst.COL_ORDER_DATE + " 为空");
        }
        if (c != null && r.getCellCount() > c) {
            Cell cell = r.getCell(c);
            if (cell != null && cell.getType() == CellType.NUMBER) {
                try {
                    return SalesImportConst.EXCEL_EPOCH.plusDays(new BigDecimal(s).longValueExact());
                } catch (ArithmeticException | NumberFormatException ignored) {
                    // 落到下面的文本解析
                }
            }
        }
        for (DateTimeFormatter f : List.of(ISO, SLASH)) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // 试下一个
            }
        }
        throw new IllegalArgumentException(
                SalesImportConst.COL_ORDER_DATE + " 无法解析（需 yyyy-MM-dd 或 yyyy/M/d）: " + s);
    }

    private String get(Row r, Map<String, Integer> idx, String column) {
        Integer c = idx.get(SalesImportConst.normalizeHeader(column));
        return c == null ? "" : text(r, c);
    }

    /** 取单元格文本：数字单元格去掉科学计数与多余小数零，保证电话/编码不被改写。 */
    private String text(Row r, int col) {
        if (col >= r.getCellCount()) {
            return "";
        }
        Cell cell = r.getCell(col);
        if (cell == null || cell.getType() == CellType.EMPTY) {
            return "";
        }
        if (cell.getType() == CellType.NUMBER) {
            BigDecimal v = new BigDecimal(cell.getRawValue());
            return v.stripTrailingZeros().toPlainString();
        }
        String raw = cell.getText();
        return raw == null ? "" : raw.trim();
    }
}
