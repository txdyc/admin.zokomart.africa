# Sales Order Excel Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `/sales/order` 页面增加「导入订单」，上传 `.xlsx` 后把商品行按「客户 + 日期」归并成正式销售订单，并像手工下单一样扣减库存。

**Architecture:** 解析与归并是**纯函数**（`module/sales/importer/` 下，无 Spring 依赖，可脱离数据库单测）；落库复用现有 `SalesOrderService.create()`。导入服务是独立 bean，注入 `SalesOrderService` 后调用，走 Spring 代理让 `create()` 的 `@Transactional` 生效，于是「每单一个事务、单失败只回滚该单」天然成立。

**Tech Stack:** Java 21 / SpringBoot 3.5.15 / MyBatis-Plus / Sa-Token / Flyway；`org.dhatim:fastexcel-reader` 读 xlsx。前端 Vue3 + Vite + TS + Ant Design Vue + Vitest。

**Spec:** [`backend/docs/superpowers/specs/2026-09-15-sales-order-import-design.md`](../specs/2026-09-15-sales-order-import-design.md)

## Global Constraints

- 基础包名 `africa.zokomart.admin`；主代码 `module/sales/...`，**测试包为 `africa.zokomart.admin.sales`**（现有约定，不带 `.module.`）。
- 统一返回 `Result<T>`；业务异常抛 `BusinessException`，controller 不 try/catch。
- 分层：controller 不写业务；entity / dto / vo 不共用。
- 文件上限 **1000 数据行**；超限 → `ResultCode.IMPORT_TOO_MANY_ROWS`（40010）。解析失败/空文件/缺必需列 → `ResultCode.IMPORT_FILE_INVALID`（40009）。
- 必需列（10，大小写与内部空白不敏感，列序不限）：`Order ID` `Sale Price` `Customer Name` `City` `Shipping Address` `Phone Number` `Product Name` `Product Code` `Quantity` `Order Date`。可选列：`Status`（存在则忽略其值）。
- 分组键 = `归一化电话 | 归一化姓名 | 归一化地址 | orderDate`。查重键与分组键**同一定义**。
- 金额：`unitPrice = SalePrice / Quantity`（scale 2, HALF_UP）；`item.amount = SalePrice 原值`。
- Excel 日期 epoch = `1899-12-30`；文本日期只接受 `yyyy-MM-dd` 与 `yyyy/M/d`。
- 新权限码 `sales:order:import`，菜单 id **2078**，父菜单 **1114**，授予角色 **904 (SALES)**。
- 迁移 **V23**（依赖 `feat/wc-multi-site-sync` 的 V22 先合并）。
- 后端每次改完跑 `mvn test`；前端跑 `pnpm test:unit` + `pnpm build`。
- 两仓分支均为 `feat/sales-order-import`。**不要跨仓库混提交。**

---

### Task 1: Excel 解析器（依赖 + 纯单测）

把 `.xlsx` 字节解析成一组 `SalesImportRow`。**不涉及数据库、不涉及 Spring。**

**Files:**
- Modify: `backend/pom.xml`（`<dependencies>` 末尾，`jsoup` 之后）
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/importer/SalesImportConst.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/importer/SalesImportRow.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/importer/SalesOrderExcelParser.java`
- Test: `backend/src/test/java/africa/zokomart/admin/sales/SalesOrderExcelParserTest.java`

**Interfaces:**
- Produces:
  - `SalesImportRow`（record）：`int rowNum, String externalOrderId, BigDecimal salePrice, String customerName, String city, String address, String phone, String productName, String productCode, Integer quantity, LocalDate orderDate, String error`
    —— `error != null` 表示该行字段有问题，`error` 是中文原因；此时其余字段可能为 null。
  - `SalesOrderExcelParser.parse(byte[] bytes) -> List<SalesImportRow>`
    —— 抛 `BusinessException(IMPORT_FILE_INVALID)` / `(IMPORT_TOO_MANY_ROWS)`。
  - `SalesImportConst.MAX_ROWS = 1000`、`SalesImportConst.EXCEL_EPOCH = LocalDate.of(1899, 12, 30)`

- [ ] **Step 1: 确认依赖版本可用**

```bash
cd backend
mvn -q dependency:get -Dartifact=org.dhatim:fastexcel-reader:0.18.4
mvn -q dependency:get -Dartifact=org.dhatim:fastexcel:0.18.4
```

Expected: 两条都成功下载。
**若 0.18.4 拉不到**：改用 Maven Central 上实际存在的最新 `0.1x.y` 版本（两个 artifact 用同一版本号）。
**若 `org.dhatim` 整体不可用**：退回 `org.apache.poi:poi-ooxml:5.2.5`，解析代码改用 `XSSFWorkbook`，本任务其余步骤的**测试用例与断言完全不变**。

- [ ] **Step 2: 加依赖**

`backend/pom.xml`，在 `jsoup` 依赖之后、`</dependencies>` 之前插入：

```xml
    <dependency>
      <groupId>org.dhatim</groupId>
      <artifactId>fastexcel-reader</artifactId>
      <version>0.18.4</version>
    </dependency>
    <!-- 仅测试用：构造 xlsx 夹具 -->
    <dependency>
      <groupId>org.dhatim</groupId>
      <artifactId>fastexcel</artifactId>
      <version>0.18.4</version>
      <scope>test</scope>
    </dependency>
```

- [ ] **Step 3: 写失败测试**

`backend/src/test/java/africa/zokomart/admin/sales/SalesOrderExcelParserTest.java`：

```java
package africa.zokomart.admin.sales;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.module.sales.importer.SalesImportRow;
import africa.zokomart.admin.module.sales.importer.SalesOrderExcelParser;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SalesOrderExcelParserTest {

    static final String[] FULL_HEADER = {
            "Order ID", "Sale Price", "Customer Name", "City", "Shipping Address",
            "Phone Number", "Product Name", "Product Code", "Quantity", "Status", "Order Date"};

    /** 造一个 xlsx：header 为表头，rows 为数据行（元素 null 表示留空单元格）。 */
    static byte[] xlsx(String[] header, Object[]... rows) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Workbook wb = new Workbook(os, "test", "1.0")) {
            Worksheet ws = wb.newWorksheet("Sheet1");
            for (int c = 0; c < header.length; c++) {
                ws.value(0, c, header[c]);
            }
            for (int r = 0; r < rows.length; r++) {
                Object[] row = rows[r];
                for (int c = 0; c < row.length; c++) {
                    Object v = row[c];
                    if (v == null) continue;
                    if (v instanceof Number n) ws.value(r + 1, c, n);
                    else ws.value(r + 1, c, String.valueOf(v));
                }
            }
        }
        return os.toByteArray();
    }

    /** 一行合法数据，列序同 FULL_HEADER；46280 = 2026-09-15 的 Excel 序列号。 */
    static Object[] goodRow(String orderId, Number price, String name, String code, Number qty) {
        return new Object[]{orderId, price, name, "Accra", "Pantang junction,Accra",
                "0244292054", "Some Product", code, qty, "未发货", 46280};
    }

    @Test
    void parses_serial_date_and_all_columns() throws Exception {
        List<SalesImportRow> rows = new SalesOrderExcelParser()
                .parse(xlsx(FULL_HEADER, goodRow("SSK001", 600, "Baaba Maison", "BD-55", 2)));

        assertEquals(1, rows.size());
        SalesImportRow r = rows.get(0);
        assertNull(r.error(), "合法行不应有 error: " + r.error());
        assertEquals(2, r.rowNum(), "表头是第 1 行，首个数据行是第 2 行");
        assertEquals("SSK001", r.externalOrderId());
        assertEquals(0, new BigDecimal("600.00").compareTo(r.salePrice()));
        assertEquals("Baaba Maison", r.customerName());
        assertEquals("Accra", r.city());
        assertEquals("0244292054", r.phone(), "电话必须保留前导 0，不能被当成数字");
        assertEquals("BD-55", r.productCode());
        assertEquals(2, r.quantity());
        assertEquals(LocalDate.of(2026, 9, 15), r.orderDate(), "46280 应解析为 2026-09-15");
    }

    @Test
    void header_matching_is_case_and_whitespace_insensitive_and_order_free() throws Exception {
        String[] shuffled = {"Order Date", "  order   id ", "SALE PRICE", "Customer Name",
                "City", "Shipping Address", "Phone Number", "Product Name",
                "Product Code", "Quantity"};
        Object[] row = {46280, "SSK001", 600, "Baaba Maison", "Accra",
                "Pantang junction,Accra", "0244292054", "Some Product", "BD-55", 1};

        List<SalesImportRow> rows = new SalesOrderExcelParser().parse(xlsx(shuffled, row));

        assertEquals(1, rows.size());
        assertNull(rows.get(0).error());
        assertEquals("SSK001", rows.get(0).externalOrderId());
        assertEquals(LocalDate.of(2026, 9, 15), rows.get(0).orderDate());
    }

    @Test
    void status_column_is_optional() throws Exception {
        String[] noStatus = {"Order ID", "Sale Price", "Customer Name", "City",
                "Shipping Address", "Phone Number", "Product Name", "Product Code",
                "Quantity", "Order Date"};
        Object[] row = {"SSK001", 600, "Baaba Maison", "Accra", "Pantang junction,Accra",
                "0244292054", "Some Product", "BD-55", 1, 46280};

        List<SalesImportRow> rows = new SalesOrderExcelParser().parse(xlsx(noStatus, row));

        assertEquals(1, rows.size());
        assertNull(rows.get(0).error());
    }

    @Test
    void missing_required_column_rejects_whole_file() throws Exception {
        String[] noPhone = {"Order ID", "Sale Price", "Customer Name", "City",
                "Shipping Address", "Product Name", "Product Code", "Quantity", "Order Date"};
        byte[] bytes = xlsx(noPhone, new Object[]{"SSK001", 600, "A", "Accra", "addr", "P", "BD-55", 1, 46280});

        BusinessException e = assertThrows(BusinessException.class,
                () -> new SalesOrderExcelParser().parse(bytes));
        assertEquals(40009, e.getCode());
    }

    @Test
    void too_many_rows_rejected() throws Exception {
        Object[][] rows = new Object[1001][];
        for (int i = 0; i < 1001; i++) {
            rows[i] = goodRow("SSK" + i, 100, "A", "BD-55", 1);
        }
        byte[] bytes = xlsx(FULL_HEADER, rows);

        BusinessException e = assertThrows(BusinessException.class,
                () -> new SalesOrderExcelParser().parse(bytes));
        assertEquals(40010, e.getCode());
    }

    @Test
    void not_an_xlsx_rejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> new SalesOrderExcelParser().parse("not,a,spreadsheet".getBytes()));
        assertEquals(40009, e.getCode());
    }

    @Test
    void text_date_accepts_iso_and_slash_but_not_ambiguous() throws Exception {
        List<SalesImportRow> rows = new SalesOrderExcelParser().parse(xlsx(FULL_HEADER,
                new Object[]{"A", 100, "N", "Accra", "addr", "0244292054", "P", "C1", 1, "", "2026-09-15"},
                new Object[]{"B", 100, "N", "Accra", "addr", "0244292054", "P", "C2", 1, "", "2026/9/15"},
                new Object[]{"C", 100, "N", "Accra", "addr", "0244292054", "P", "C3", 1, "", "15/09/2026"}));

        assertEquals(LocalDate.of(2026, 9, 15), rows.get(0).orderDate());
        assertEquals(LocalDate.of(2026, 9, 15), rows.get(1).orderDate());
        assertNotNull(rows.get(2).error(), "dd/MM/yyyy 有歧义，必须报错而不是猜");
        assertTrue(rows.get(2).error().contains("Order Date"));
    }

    @Test
    void bad_fields_become_row_errors_not_exceptions() throws Exception {
        List<SalesImportRow> rows = new SalesOrderExcelParser().parse(xlsx(FULL_HEADER,
                new Object[]{"A", 100, "N", "Accra", "addr", "0244292054", "P", "C1", 0, "", 46280},
                new Object[]{"B", -5, "N", "Accra", "addr", "0244292054", "P", "C2", 1, "", 46280},
                new Object[]{"C", 100, "", "Accra", "addr", "0244292054", "P", "C3", 1, "", 46280},
                new Object[]{"D", 100, "N", "Accra", "addr", "0244292054", "P", "C4", 1, "", null},
                new Object[]{null, 100, "N", "Accra", "addr", "0244292054", "P", "C5", 1, "", 46280}));

        assertEquals(5, rows.size());
        assertTrue(rows.get(0).error().contains("Quantity"), "数量 0 应报错");
        assertTrue(rows.get(1).error().contains("Sale Price"), "负金额应报错");
        assertTrue(rows.get(2).error().contains("Customer Name"), "姓名为空应报错");
        assertTrue(rows.get(3).error().contains("Order Date"), "日期为空应报错");
        assertTrue(rows.get(4).error().contains("Order ID"), "Order ID 为空应报错");
    }

    @Test
    void city_may_be_blank() throws Exception {
        List<SalesImportRow> rows = new SalesOrderExcelParser().parse(xlsx(FULL_HEADER,
                new Object[]{"A", 100, "N", null, "addr", "0244292054", "P", "C1", 1, "", 46280}));

        assertNull(rows.get(0).error(), "City 可为空");
        assertEquals("", rows.get(0).city());
    }
}
```

- [ ] **Step 4: 跑测试确认失败**

```bash
cd backend && mvn -q test -Dtest=SalesOrderExcelParserTest
```

Expected: 编译失败 —— `SalesImportRow` / `SalesOrderExcelParser` 不存在。

- [ ] **Step 5: 写 `SalesImportConst`**

```java
package africa.zokomart.admin.module.sales.importer;

import java.time.LocalDate;
import java.util.List;

/** 销售订单 Excel 导入的外部数据契约：列名与上限。列名是对外契约，不可随意改。 */
public final class SalesImportConst {

    private SalesImportConst() {
    }

    public static final int MAX_ROWS = 1000;

    /** Excel 序列号 1 对应 1900-01-01，因 1900 闰年 bug，基准取 1899-12-30。 */
    public static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    public static final String COL_ORDER_ID = "Order ID";
    public static final String COL_SALE_PRICE = "Sale Price";
    public static final String COL_CUSTOMER_NAME = "Customer Name";
    public static final String COL_CITY = "City";
    public static final String COL_ADDRESS = "Shipping Address";
    public static final String COL_PHONE = "Phone Number";
    public static final String COL_PRODUCT_NAME = "Product Name";
    public static final String COL_PRODUCT_CODE = "Product Code";
    public static final String COL_QUANTITY = "Quantity";
    public static final String COL_ORDER_DATE = "Order Date";
    /** 可选列：存在则忽略其值（系统状态一律 PENDING_DISPATCH）。 */
    public static final String COL_STATUS = "Status";

    public static final List<String> REQUIRED_COLUMNS = List.of(
            COL_ORDER_ID, COL_SALE_PRICE, COL_CUSTOMER_NAME, COL_CITY, COL_ADDRESS,
            COL_PHONE, COL_PRODUCT_NAME, COL_PRODUCT_CODE, COL_QUANTITY, COL_ORDER_DATE);

    /** 列名匹配：trim + 折叠内部空白 + 转小写。 */
    public static String normalizeHeader(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
```

- [ ] **Step 6: 写 `SalesImportRow`**

```java
package africa.zokomart.admin.module.sales.importer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Excel 一行的解析结果。
 * error != null 表示该行字段非法，其余字段可能为 null；调用方据此让整个订单失败。
 */
public record SalesImportRow(
        int rowNum,
        String externalOrderId,
        BigDecimal salePrice,
        String customerName,
        String city,
        String address,
        String phone,
        String productName,
        String productCode,
        Integer quantity,
        LocalDate orderDate,
        String error) {

    public static SalesImportRow error(int rowNum, String customerName, String phone,
                                       String address, LocalDate orderDate, String reason) {
        return new SalesImportRow(rowNum, null, null, customerName, null, address, phone,
                null, null, null, orderDate, reason);
    }
}
```

- [ ] **Step 7: 写 `SalesOrderExcelParser`**

```java
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
```

- [ ] **Step 8: 跑测试确认通过**

```bash
cd backend && mvn -q test -Dtest=SalesOrderExcelParserTest
```

Expected: 9 个用例全部 PASS。
**若 `r.getRowNum()` 返回 0-based**（版本差异），把 `toRow` 里的 `rowNum` 改成 `r.getRowNum() + 1`，以 `parses_serial_date_and_all_columns` 断言的「首个数据行 = 2」为准。

- [ ] **Step 9: 提交**

```bash
cd backend
git add pom.xml src/main/java/africa/zokomart/admin/module/sales/importer/ src/test/java/africa/zokomart/admin/sales/SalesOrderExcelParserTest.java
git commit -m "feat(sales): Excel 导入解析器（fastexcel-reader，表头容错 + 序列号日期）

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: 归一化与分组（纯单测）

把 `List<SalesImportRow>` 按「归一化电话 | 归一化姓名 | 归一化地址 | orderDate」归并成订单组。

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/importer/SalesImportKey.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/importer/SalesOrderGrouper.java`
- Test: `backend/src/test/java/africa/zokomart/admin/sales/SalesOrderGrouperTest.java`

**Interfaces:**
- Consumes: `SalesImportRow`（Task 1）
- Produces:
  - `SalesImportKey`（record）：`String phone, String name, String address, LocalDate orderDate`
    - `SalesImportKey.of(String phone, String name, String address, LocalDate date) -> SalesImportKey`（构造时归一化）
    - `SalesImportKey.normalizePhone(String raw) -> String`
    - `SalesImportKey.normalizeText(String raw) -> String`
  - `SalesOrderGrouper.group(List<SalesImportRow> rows) -> LinkedHashMap<SalesImportKey, List<SalesImportRow>>`
    - 日期为 null 的行归到 `orderDate = null` 的键上，保持在同一组便于整组报错。

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/africa/zokomart/admin/sales/SalesOrderGrouperTest.java`：

```java
package africa.zokomart.admin.sales;

import africa.zokomart.admin.module.sales.importer.SalesImportKey;
import africa.zokomart.admin.module.sales.importer.SalesImportRow;
import africa.zokomart.admin.module.sales.importer.SalesOrderGrouper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SalesOrderGrouperTest {

    static final LocalDate D1 = LocalDate.of(2026, 9, 15);
    static final LocalDate D2 = LocalDate.of(2026, 9, 16);

    static SalesImportRow row(int n, String phone, String name, String addr,
                              LocalDate date, String code, int qty, String price) {
        return new SalesImportRow(n, "OID" + n, new BigDecimal(price), name, "Accra",
                addr, phone, "P", code, qty, date, null);
    }

    @Test
    void normalizes_ghana_phone_formats_to_same_key() {
        assertEquals("244239227", SalesImportKey.normalizePhone("0244239227"));
        assertEquals("244239227", SalesImportKey.normalizePhone("233244239227"));
        assertEquals("244239227", SalesImportKey.normalizePhone("+233 244 239 227"));
        assertEquals("244239227", SalesImportKey.normalizePhone("00233244239227"));
        assertEquals("244239227", SalesImportKey.normalizePhone("244239227"));
        assertEquals("244239227", SalesImportKey.normalizePhone("0244-239-227"));
    }

    @Test
    void odd_length_phone_kept_as_digits() {
        assertEquals("12345", SalesImportKey.normalizePhone("1-2345"));
        assertEquals("", SalesImportKey.normalizePhone("  "));
    }

    @Test
    void normalizes_name_and_address_by_case_and_whitespace() {
        assertEquals("baaba maison", SalesImportKey.normalizeText("  Baaba   MAISON "));
        assertEquals("pantang junction,accra", SalesImportKey.normalizeText("Pantang junction,Accra"));
    }

    @Test
    void same_customer_same_day_merges_into_one_group() {
        List<SalesImportRow> rows = List.of(
                row(4, "0244292054", "Baaba Maison", "Pantang junction,Accra", D1, "BD-55", 1, "320"),
                row(5, "0244292054", "Baaba Maison", "Pantang junction,Accra", D1, "RC-50-A", 1, "430"),
                row(6, "233244292054", "baaba  maison", "PANTANG JUNCTION,ACCRA", D1, "YD-533", 1, "280"));

        LinkedHashMap<SalesImportKey, List<SalesImportRow>> g = new SalesOrderGrouper().group(rows);

        assertEquals(1, g.size(), "同客户同日期（含格式差异）应归为一组");
        assertEquals(3, g.values().iterator().next().size());
    }

    @Test
    void same_customer_different_day_splits_into_two_groups() {
        List<SalesImportRow> rows = List.of(
                row(2, "0244292054", "Baaba Maison", "Pantang junction,Accra", D1, "BD-55", 1, "320"),
                row(3, "0244292054", "Baaba Maison", "Pantang junction,Accra", D2, "RC-50-A", 1, "430"));

        LinkedHashMap<SalesImportKey, List<SalesImportRow>> g = new SalesOrderGrouper().group(rows);

        assertEquals(2, g.size(), "同客户跨天必须拆成两组，而不是报错");
        g.values().forEach(v -> assertEquals(1, v.size()));
    }

    @Test
    void different_address_does_not_merge() {
        List<SalesImportRow> rows = List.of(
                row(2, "0244292054", "Baaba Maison", "Pantang junction,Accra", D1, "BD-55", 1, "320"),
                row(3, "0244292054", "Baaba Maison", "Somewhere else", D1, "RC-50-A", 1, "430"));

        assertEquals(2, new SalesOrderGrouper().group(rows).size(),
                "保守策略：宁可漏合并，不可错合并");
    }

    @Test
    void preserves_file_order() {
        List<SalesImportRow> rows = List.of(
                row(2, "0200000001", "A", "addr a", D1, "C1", 1, "100"),
                row(3, "0200000002", "B", "addr b", D1, "C2", 1, "100"),
                row(4, "0200000001", "A", "addr a", D1, "C3", 1, "100"));

        List<SalesImportKey> keys = new ArrayList<>(new SalesOrderGrouper().group(rows).keySet());

        assertEquals(2, keys.size());
        assertEquals("a", keys.get(0).name(), "首次出现顺序决定组顺序");
        assertEquals("b", keys.get(1).name());
    }

    @Test
    void rows_with_null_date_group_together_per_customer() {
        List<SalesImportRow> rows = List.of(
                SalesImportRow.error(2, "A", "0200000001", "addr a", null, "Order Date 为空"),
                SalesImportRow.error(3, "A", "0200000001", "addr a", null, "Order Date 为空"));

        LinkedHashMap<SalesImportKey, List<SalesImportRow>> g = new SalesOrderGrouper().group(rows);

        assertEquals(1, g.size());
        assertNull(g.keySet().iterator().next().orderDate());
        assertEquals(2, g.values().iterator().next().size());
    }

    @Test
    void sample_file_shape_14_rows_to_9_orders() {
        // utils/OrdersTemplate.xlsx 的真实形状：Baaba Maison 4 行、Nuhu yahaya 2 行、ohannes 2 行，其余 6 行各自成单
        List<SalesImportRow> rows = List.of(
                row(2, "0244239227", "Lord Godfriend Goodman", "Total Fuel Station Haatso", D1, "BD-8801", 1, "600"),
                row(3, "0244352007", "Charles Dontoh", "No 8 Adei Crescent East Legon", D1, "MG-E202", 1, "1100"),
                row(4, "0244292054", "Baaba Maison", "Pantang junction,Accra", D1, "BD-55", 1, "320"),
                row(5, "0244292054", "Baaba Maison", "Pantang junction,Accra", D1, "RC-50-A", 1, "430"),
                row(6, "0244292054", "Baaba Maison", "Pantang junction,Accra", D1, "YD-533", 1, "280"),
                row(7, "0244292054", "Baaba Maison", "Pantang junction,Accra", D1, "FK-0313-W", 1, "320"),
                row(8, "0204403525", "Solomon Kwame Anancy", "GD 098 1823 Newlegon", D1, "DF-030", 1, "550"),
                row(9, "0243077119", "Prince Owusu", "Legon geography department", D1, "JT-8177HJ", 1, "350"),
                row(10, "0243554453", "John Tetteh", "ECG Office Accra East", D1, "OKO-865002G", 1, "1200"),
                row(11, "0248406711", "Nuhu yahaya", "Speedaf Express Tarkwa Site", D1, "JT-8177", 2, "600"),
                row(12, "0248406711", "Nuhu yahaya", "Speedaf Express Tarkwa Site", D1, "IM-006", 1, "800"),
                row(13, "0543080650", "Edward mensah", "CMB Flats North Kaneshie", D1, "CC-7032", 1, "360"),
                row(14, "0244598352", "ohannes", "Modiba Hills Kwabenya", D1, "MG-E202", 1, "1000"),
                row(15, "0244598352", "ohannes", "Modiba Hills Kwabenya", D1, "CC-7032", 1, "400"));

        LinkedHashMap<SalesImportKey, List<SalesImportRow>> g = new SalesOrderGrouper().group(rows);

        assertEquals(9, g.size(), "14 行应归并为 9 单");
        assertEquals(4, g.get(SalesImportKey.of("0244292054", "Baaba Maison", "Pantang junction,Accra", D1)).size());
        assertEquals(2, g.get(SalesImportKey.of("0248406711", "Nuhu yahaya", "Speedaf Express Tarkwa Site", D1)).size());
        assertEquals(2, g.get(SalesImportKey.of("0244598352", "ohannes", "Modiba Hills Kwabenya", D1)).size());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd backend && mvn -q test -Dtest=SalesOrderGrouperTest
```

Expected: 编译失败 —— `SalesImportKey` / `SalesOrderGrouper` 不存在。

- [ ] **Step 3: 写 `SalesImportKey`**

```java
package africa.zokomart.admin.module.sales.importer;

import java.time.LocalDate;

/**
 * 订单分组键 = 归一化电话 + 归一化姓名 + 归一化地址 + 订单日期。
 * 归一化只在内存里用于分组与查重，入库仍写 Excel 原值。
 */
public record SalesImportKey(String phone, String name, String address, LocalDate orderDate) {

    public static SalesImportKey of(String phone, String name, String address, LocalDate orderDate) {
        return new SalesImportKey(normalizePhone(phone), normalizeText(name),
                normalizeText(address), orderDate);
    }

    /**
     * 加纳号码国际区号归一：0244239227 ≡ 233244239227 ≡ +233 244 239 227 ≡ 244239227。
     * 归一不出 9 位时原样保留数字串——仍可作键，只是不跨格式归并。
     */
    public static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        String d = raw.replaceAll("\\D", "");
        if (d.startsWith("00233")) {
            return d.substring(5);
        }
        if (d.startsWith("233") && d.length() == 12) {
            return d.substring(3);
        }
        if (d.startsWith("0") && d.length() == 10) {
            return d.substring(1);
        }
        return d;
    }

    /** trim + 折叠内部连续空白 + 转小写。刻意不做全半角转换。 */
    public static String normalizeText(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
```

- [ ] **Step 4: 写 `SalesOrderGrouper`**

```java
package africa.zokomart.admin.module.sales.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** 按 SalesImportKey 归并行；LinkedHashMap 保留文件原始顺序，让导入结果可预期。 */
public class SalesOrderGrouper {

    public LinkedHashMap<SalesImportKey, List<SalesImportRow>> group(List<SalesImportRow> rows) {
        LinkedHashMap<SalesImportKey, List<SalesImportRow>> out = new LinkedHashMap<>();
        for (SalesImportRow r : rows) {
            SalesImportKey key = SalesImportKey.of(r.phone(), r.customerName(), r.address(), r.orderDate());
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        return out;
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

```bash
cd backend && mvn -q test -Dtest=SalesOrderGrouperTest
```

Expected: 8 个用例全部 PASS。

- [ ] **Step 6: 提交**

```bash
cd backend
git add src/main/java/africa/zokomart/admin/module/sales/importer/ src/test/java/africa/zokomart/admin/sales/SalesOrderGrouperTest.java
git commit -m "feat(sales): 导入分组键与归并（国际区号归一，跨天拆单）

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: 迁移 V23 + `create()` 支持 city / orderDate / amount / externalOrderId

数据库加列加权限，`SalesOrderCreateDTO` 与 `create()` 支持导入需要的三个新语义。**手工下单行为必须保持不变。**

**Files:**
- Create: `backend/src/main/resources/db/migration/V23__sales_order_import.sql`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/entity/SalesOrder.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/entity/SalesOrderItem.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/vo/SalesOrderVO.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/vo/SalesOrderItemVO.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/dto/SalesOrderCreateDTO.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/service/impl/SalesOrderServiceImpl.java`（`create()` 约 49-93 行、`page()` 约 96-105 行）
- Test: `backend/src/test/java/africa/zokomart/admin/sales/SalesOrderCreateFieldsTest.java`

**Interfaces:**
- Produces（后续任务依赖）：
  - `SalesOrderCreateDTO`：新增 `String city`、`LocalDate orderDate`
  - `SalesOrderCreateDTO.Item`：新增 `BigDecimal amount`、`String externalOrderId`
  - `SalesOrderService.create(SalesOrderCreateDTO) -> Long`（签名不变）
  - `SalesOrderVO`：新增 `String city`、`LocalDate orderDate`

- [ ] **Step 1: 写迁移**

`backend/src/main/resources/db/migration/V23__sales_order_import.sql`：

```sql
-- ===========================================================================
-- V23: 销售订单 Excel 导入
--   1. sales_order 增加 city / order_date（业务日期，区别于 create_time 语义）
--   2. sales_order_item 增加 external_order_id（源 Excel 的 Order ID，行级，仅溯源）
--   3. 历史数据回填 order_date
--   4. 新增按钮权限 sales:order:import，授予销售员 SALES(904)
-- ===========================================================================
ALTER TABLE sales_order
    ADD COLUMN city       VARCHAR(128) NULL COMMENT '城市'                         AFTER customer_address,
    ADD COLUMN order_date DATE         NULL COMMENT '订单日期（业务日期，非创建时刻）' AFTER city,
    ADD KEY idx_sales_order_date (order_date);

ALTER TABLE sales_order_item
    ADD COLUMN external_order_id VARCHAR(64) NULL COMMENT '来源 Excel 的 Order ID（行级，仅溯源）' AFTER order_id;

UPDATE sales_order SET order_date = DATE(create_time) WHERE order_date IS NULL;

INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2078, 1114, '导入销售订单', 3, 'sales:order:import', NULL, NULL, NULL, 4, 1, 1, NOW(), 0, 0);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 904 * 100000 + m.id, 904, m.id, NOW() FROM sys_menu m WHERE m.id = 2078;
```

- [ ] **Step 2: 写失败测试**

`backend/src/test/java/africa/zokomart/admin/sales/SalesOrderCreateFieldsTest.java`：

```java
package africa.zokomart.admin.sales;

import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.entity.SalesOrderItem;
import africa.zokomart.admin.module.sales.mapper.SalesOrderItemMapper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.sales.service.SalesOrderService;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** create() 的新字段语义：city / orderDate / amount 覆盖 / externalOrderId / create_time 归到业务日期。 */
@SpringBootTest
class SalesOrderCreateFieldsTest {

    @Autowired
    SalesOrderService salesOrderService;
    @Autowired
    SalesOrderMapper orderMapper;
    @Autowired
    SalesOrderItemMapper itemMapper;
    @Autowired
    SupplierProductMapper supplierProductMapper;

    /** 取库里任意一个可用的供应商产品，避免测试依赖特定种子数据。 */
    private Long anySupplierProductId() {
        List<SupplierProduct> all = supplierProductMapper.selectList(
                new LambdaQueryWrapper<SupplierProduct>().last("limit 1"));
        assertFalse(all.isEmpty(), "dev 库需要至少一个 supplier_product 才能跑本测试");
        return all.get(0).getId();
    }

    private SalesOrderCreateDTO dto(String phone) {
        SalesOrderCreateDTO d = new SalesOrderCreateDTO();
        d.setCustomerName("Fields Test");
        d.setCustomerPhone(phone);
        d.setCustomerAddress("test addr");
        SalesOrderCreateDTO.Item it = new SalesOrderCreateDTO.Item();
        it.setSupplierProductId(anySupplierProductId());
        it.setQty(3);
        it.setUnitPrice(new BigDecimal("233.33"));
        d.setItems(List.of(it));
        return d;
    }

    private void cleanup(Long orderId) {
        itemMapper.delete(new LambdaQueryWrapper<SalesOrderItem>().eq(SalesOrderItem::getOrderId, orderId));
        orderMapper.deleteById(orderId);
    }

    @Test
    void manual_create_defaults_order_date_to_today_and_amount_to_price_times_qty() {
        Long id = salesOrderService.create(dto("0550000001"));
        try {
            SalesOrder o = orderMapper.selectById(id);
            assertEquals(LocalDate.now(), o.getOrderDate(), "手工下单 orderDate 默认今天");
            assertNull(o.getCity(), "未传 city 应为 null");
            assertEquals(LocalDate.now(), o.getCreateTime().toLocalDate(), "手工下单 create_time 仍是此刻");

            SalesOrderItem item = itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItem>()
                    .eq(SalesOrderItem::getOrderId, id)).get(0);
            assertEquals(0, new BigDecimal("699.99").compareTo(item.getAmount()),
                    "未传 amount 时仍按 unitPrice * qty");
            assertNull(item.getExternalOrderId());
        } finally {
            cleanup(id);
        }
    }

    @Test
    void import_style_create_honours_city_order_date_amount_and_external_id() {
        LocalDate past = LocalDate.of(2026, 9, 15);
        SalesOrderCreateDTO d = dto("0550000002");
        d.setCity("Accra");
        d.setOrderDate(past);
        d.getItems().get(0).setAmount(new BigDecimal("700.00"));
        d.getItems().get(0).setExternalOrderId("SSK202609151503");

        Long id = salesOrderService.create(d);
        try {
            SalesOrder o = orderMapper.selectById(id);
            assertEquals("Accra", o.getCity());
            assertEquals(past, o.getOrderDate());
            assertEquals(past, o.getCreateTime().toLocalDate(),
                    "orderDate 非空时 create_time 归到业务日期，仪表盘才能按日统计");
            assertNotNull(o.getUpdateTime(), "update_time 仍是真实导入时刻");
            assertEquals(LocalDate.now(), o.getUpdateTime().toLocalDate());
            assertEquals(0, new BigDecimal("700.00").compareTo(o.getTotalAmount()),
                    "订单总额用传入的 amount 原值，不回乘（233.33*3=699.99 会漂移）");

            SalesOrderItem item = itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItem>()
                    .eq(SalesOrderItem::getOrderId, id)).get(0);
            assertEquals(0, new BigDecimal("700.00").compareTo(item.getAmount()));
            assertEquals("SSK202609151503", item.getExternalOrderId());
        } finally {
            cleanup(id);
        }
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

```bash
cd backend && mvn -q test -Dtest=SalesOrderCreateFieldsTest
```

Expected: 编译失败 —— `setCity` / `setOrderDate` / `setAmount` / `setExternalOrderId` / `getCity` 等不存在。

- [ ] **Step 4: 加实体与 VO 字段**

`SalesOrder.java`，在 `customerAddress` 之后加：

```java
    private String city;
    private java.time.LocalDate orderDate;
```

`SalesOrderItem.java`，在 `orderId` 之后加：

```java
    private String externalOrderId;
```

`SalesOrderVO.java` 加 `city` 与 `orderDate`（类型同实体）；`SalesOrderItemVO.java` 加 `externalOrderId`。
两个 VO 都靠 `BeanUtils.copyProperties` 填充，字段名一致即可，无需改赋值代码。

- [ ] **Step 5: 扩展 DTO**

`SalesOrderCreateDTO.java`：在 `remark` 之后加

```java
    /** 可空：导入时来自 Excel 的 City；手工下单不传。 */
    @Size(max = 128, message = "城市长度不能超过 128")
    private String city;

    /** 可空：业务订单日期。为空则取今天（手工下单）。 */
    private LocalDate orderDate;
```

`Item` 内部类里，在 `unitPrice` 之后加

```java
        /** 可空：行金额。为空则按 unitPrice * qty。导入时传 Excel 的 Sale Price 原值，避免除不尽漂移。 */
        @DecimalMin(value = "0", message = "行金额不能为负")
        private BigDecimal amount;

        /** 可空：来源 Excel 的 Order ID，仅溯源。 */
        @Size(max = 64, message = "外部单号长度不能超过 64")
        private String externalOrderId;
```

补 import：`jakarta.validation.constraints.DecimalMin`、`jakarta.validation.constraints.Size`、`java.time.LocalDate`。

- [ ] **Step 6: 改 `create()` 与 `page()`**

`SalesOrderServiceImpl.java`。在 `create()` 的 for 循环里，把 `item.setAmount(...)` 那行换成：

```java
            item.setAmount(in.getAmount() != null
                    ? in.getAmount()
                    : unitPrice.multiply(BigDecimal.valueOf(qty)));
            item.setExternalOrderId(in.getExternalOrderId());
```

在 `order.setCompleted(0);` 之后、`order.setTotalQty(...)` 之前插入：

```java
        order.setCity(dto.getCity());
        // orderDate 非空 = 导入历史订单：create_time 一并归到业务日期，
        // 使仪表盘/列表（均以 create_time 为口径）按订单实际发生日统计。
        // 这是对「审计字段不手动 set」约定的一处刻意例外，范围仅限本分支；
        // update_time 仍由自动填充写入真实时刻，导入时间不会丢失。
        if (dto.getOrderDate() != null) {
            order.setOrderDate(dto.getOrderDate());
            order.setCreateTime(dto.getOrderDate().atStartOfDay());
        } else {
            order.setOrderDate(LocalDate.now());
        }
```

`page()` 的排序追加 id 兜底（导入订单 create_time 同秒，只按时间排序在 MySQL 里顺序不确定）：

```java
                        .orderByDesc(SalesOrder::getCreateTime)
                        .orderByDesc(SalesOrder::getId));
```

- [ ] **Step 7: 跑迁移与测试**

```bash
cd backend && mvn -q test -Dtest=SalesOrderCreateFieldsTest
```

Expected: 2 个用例 PASS（Flyway 自动应用 V23）。
**若 Flyway 报 out-of-order**：确认 `feat/wc-multi-site-sync` 的 V22 已在当前分支历史里；若不在，把迁移重命名为 V22 并同步改本计划里所有 V23 引用。

- [ ] **Step 8: 跑全量回归，确认手工下单未被破坏**

```bash
cd backend && mvn test
```

Expected: 全部 PASS —— 特别是 `SalesOrderApiTest`、`SalesFlowServiceTest`、`SalesOrderBackorderApiTest`、`MenuPermSeedTest`。
`MenuPermSeedTest` 若断言了菜单总数，把数量 +1。

- [ ] **Step 9: 提交**

```bash
cd backend
git add src/main/resources/db/migration/V23__sales_order_import.sql src/main/java/africa/zokomart/admin/module/sales/ src/test/java/africa/zokomart/admin/sales/SalesOrderCreateFieldsTest.java
git commit -m "feat(sales): V23 增加 city/order_date/external_order_id 与导入权限，create() 支持新字段

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: 导入服务（选品 / 查重 / 逐单事务）

把分组结果落成订单。**这是业务核心。**

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/vo/SalesOrderImportResultVO.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/vo/SalesOrderImportError.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/service/SalesOrderImportService.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/service/impl/SalesOrderImportServiceImpl.java`
- Test: `backend/src/test/java/africa/zokomart/admin/sales/SalesOrderImportServiceTest.java`

**Interfaces:**
- Consumes: `SalesOrderExcelParser.parse`、`SalesOrderGrouper.group`、`SalesImportKey`（Task 1-2）；`SalesOrderService.create`、`SalesOrderCreateDTO`（Task 3）
- Produces:
  - `SalesOrderImportService.importExcel(MultipartFile file) -> SalesOrderImportResultVO`
  - `SalesOrderImportResultVO`：`int totalRows, orderCount, success, skipped, failed; List<SalesOrderImportError> errors`
  - `SalesOrderImportError`：`String rows, externalOrderIds, customerName, productCode, reason`

- [ ] **Step 1: 写结果 VO**

`SalesOrderImportError.java`：

```java
package africa.zokomart.admin.module.sales.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 导入失败的一张订单（一单多行合成一条）。rows 为源文件行号，逗号分隔，表头为第 1 行。 */
@Data
@AllArgsConstructor
public class SalesOrderImportError {
    private String rows;
    private String externalOrderIds;
    private String customerName;
    private String productCode;
    private String reason;
}
```

`SalesOrderImportResultVO.java`：

```java
package africa.zokomart.admin.module.sales.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 导入结果。orderCount = 归并后的订单数；success + skipped + failed 应等于 orderCount。 */
@Data
public class SalesOrderImportResultVO {
    private int totalRows;
    private int orderCount;
    private int success;
    private int skipped;
    private int failed;
    private List<SalesOrderImportError> errors = new ArrayList<>();
}
```

- [ ] **Step 2: 写 service 接口**

```java
package africa.zokomart.admin.module.sales.service;

import africa.zokomart.admin.module.sales.vo.SalesOrderImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface SalesOrderImportService {

    /** 解析 .xlsx，按客户+日期归并成销售订单并落库（扣库存）。整单失败不影响其它订单。 */
    SalesOrderImportResultVO importExcel(MultipartFile file);
}
```

- [ ] **Step 3: 写失败测试**

`backend/src/test/java/africa/zokomart/admin/sales/SalesOrderImportServiceTest.java`：

```java
package africa.zokomart.admin.sales;

import africa.zokomart.admin.module.inventory.entity.InventoryStock;
import africa.zokomart.admin.module.inventory.mapper.InventoryStockMapper;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.entity.SalesOrderItem;
import africa.zokomart.admin.module.sales.mapper.SalesOrderItemMapper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.sales.service.SalesOrderImportService;
import africa.zokomart.admin.module.sales.vo.SalesOrderImportResultVO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 导入服务集成测试。跑的是真实 dev 库，因此：
 *  - 用带时间戳的唯一电话号标识本次测试数据
 *  - @AfterEach 物理清理订单与明细，不留垃圾
 */
@SpringBootTest
class SalesOrderImportServiceTest {

    static final String[] HEADER = {
            "Order ID", "Sale Price", "Customer Name", "City", "Shipping Address",
            "Phone Number", "Product Name", "Product Code", "Quantity", "Status", "Order Date"};
    /** 46280 = 2026-09-15。 */
    static final int SERIAL_D1 = 46280;
    static final int SERIAL_D2 = 46281;

    @Autowired
    SalesOrderImportService importService;
    @Autowired
    SalesOrderMapper orderMapper;
    @Autowired
    SalesOrderItemMapper itemMapper;
    @Autowired
    SupplierProductMapper supplierProductMapper;
    @Autowired
    InventoryStockMapper stockMapper;

    final List<String> usedPhones = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String phone : usedPhones) {
            List<SalesOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<SalesOrder>().eq(SalesOrder::getCustomerPhone, phone));
            for (SalesOrder o : orders) {
                itemMapper.delete(new LambdaQueryWrapper<SalesOrderItem>()
                        .eq(SalesOrderItem::getOrderId, o.getId()));
                orderMapper.deleteById(o.getId());
            }
        }
        usedPhones.clear();
    }

    /**
     * 生成唯一电话。必须**恰好 10 位**（0 + 9 位）：normalizePhone 只在长度为 10 时
     * 剥前导 0，位数不对会让 international_phone_formats_merge_into_one_order 偶发失败。
     */
    private String uniquePhone(String prefix) {
        String p = prefix + String.format("%06d", System.nanoTime() % 1000000L);
        assertEquals(10, p.length(), "测试电话必须是 10 位");
        usedPhones.add(p);
        return p;
    }

    private SupplierProduct anyProduct(int skip) {
        List<SupplierProduct> all = supplierProductMapper.selectList(
                new LambdaQueryWrapper<SupplierProduct>().last("limit " + (skip + 1)));
        assertTrue(all.size() > skip, "dev 库需要至少 " + (skip + 1) + " 个 supplier_product");
        return all.get(skip);
    }

    private MockMultipartFile xlsx(Object[]... rows) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Workbook wb = new Workbook(os, "test", "1.0")) {
            Worksheet ws = wb.newWorksheet("Sheet1");
            for (int c = 0; c < HEADER.length; c++) {
                ws.value(0, c, HEADER[c]);
            }
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v == null) continue;
                    if (v instanceof Number n) ws.value(r + 1, c, n);
                    else ws.value(r + 1, c, String.valueOf(v));
                }
            }
        }
        return new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", os.toByteArray());
    }

    private Object[] row(String oid, Number price, String name, String phone,
                         String addr, String code, Number qty, Number dateSerial) {
        return new Object[]{oid, price, name, "Accra", addr, phone, "Any", code, qty, "未发货", dateSerial};
    }

    @Test
    void groups_rows_by_customer_and_sums_amount() throws Exception {
        SupplierProduct p1 = anyProduct(0);
        SupplierProduct p2 = anyProduct(1);
        String phone = uniquePhone("0551");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("A1", 320, "Baaba Maison", phone, "Pantang junction", p1.getProductCode(), 1, SERIAL_D1),
                row("A2", 430, "Baaba Maison", phone, "Pantang junction", p2.getProductCode(), 1, SERIAL_D1)));

        assertEquals(2, res.getTotalRows());
        assertEquals(1, res.getOrderCount(), "同客户同日两行归为一单");
        assertEquals(1, res.getSuccess());
        assertEquals(0, res.getFailed());

        SalesOrder o = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).get(0);
        assertEquals(0, new BigDecimal("750.00").compareTo(o.getTotalAmount()));
        assertEquals(2, o.getTotalQty());
        assertEquals("PENDING_DISPATCH", o.getStatus(), "Status 列被忽略，一律待派送");
        assertEquals("Accra", o.getCity());
        assertEquals(LocalDate.of(2026, 9, 15), o.getOrderDate());
        assertEquals(phone, o.getCustomerPhone(), "入库存 Excel 原值");
    }

    @Test
    void same_customer_across_days_becomes_two_orders() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0552");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("B1", 320, "Cross Day", phone, "addr", p.getProductCode(), 1, SERIAL_D1),
                row("B2", 430, "Cross Day", phone, "addr", p.getProductCode(), 1, SERIAL_D2)));

        assertEquals(2, res.getOrderCount(), "跨天必须拆成两单，不是报错");
        assertEquals(2, res.getSuccess());
        assertEquals(0, res.getFailed());

        List<SalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone));
        assertEquals(2, orders.size());
        assertTrue(orders.stream().anyMatch(o -> o.getOrderDate().equals(LocalDate.of(2026, 9, 15))));
        assertTrue(orders.stream().anyMatch(o -> o.getOrderDate().equals(LocalDate.of(2026, 9, 16))));
    }

    @Test
    void international_phone_formats_merge_into_one_order() throws Exception {
        SupplierProduct p = anyProduct(0);
        String local = uniquePhone("0244");
        String intl = "233" + local.substring(1);

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("C1", 100, "Intl Phone", local, "addr", p.getProductCode(), 1, SERIAL_D1),
                row("C2", 200, "Intl Phone", intl, "addr", p.getProductCode(), 1, SERIAL_D1)));

        assertEquals(1, res.getOrderCount(), "0xxx 与 233xxx 应归一为同一客户");
        assertEquals(1, res.getSuccess());
    }

    @Test
    void unknown_product_code_fails_that_order_only() throws Exception {
        SupplierProduct p = anyProduct(0);
        String goodPhone = uniquePhone("0553");
        String badPhone = uniquePhone("0554");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("D1", 100, "Good One", goodPhone, "addr", p.getProductCode(), 1, SERIAL_D1),
                row("D2", 100, "Bad One", badPhone, "addr", "NO-SUCH-CODE-ZZZ", 1, SERIAL_D1)));

        assertEquals(2, res.getOrderCount());
        assertEquals(1, res.getSuccess(), "好单照常导入");
        assertEquals(1, res.getFailed());
        assertEquals(1, res.getErrors().size());
        assertEquals("NO-SUCH-CODE-ZZZ", res.getErrors().get(0).getProductCode());
        assertTrue(res.getErrors().get(0).getReason().contains("不存在"));
        assertEquals("3", res.getErrors().get(0).getRows(), "错误应定位到源文件第 3 行");

        assertTrue(orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, badPhone)).isEmpty(), "失败单不得留残缺数据");
    }

    @Test
    void bad_row_fails_whole_order_even_if_other_rows_are_fine() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0555");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("E1", 100, "Partial", phone, "addr", p.getProductCode(), 1, SERIAL_D1),
                row("E2", 100, "Partial", phone, "addr", p.getProductCode(), 0, SERIAL_D1)));

        assertEquals(1, res.getOrderCount());
        assertEquals(0, res.getSuccess());
        assertEquals(1, res.getFailed(), "同组内一行非法 → 整单失败，不生成残缺订单");
        assertTrue(orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).isEmpty());
    }

    @Test
    void missing_order_date_fails_that_order() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0556");

        SalesOrderImportResultVO res = importService.importExcel(xlsx(
                row("F1", 100, "No Date", phone, "addr", p.getProductCode(), 1, null)));

        assertEquals(0, res.getSuccess());
        assertEquals(1, res.getFailed());
        assertTrue(res.getErrors().get(0).getReason().contains("Order Date"));
    }

    @Test
    void reimport_of_same_file_is_skipped() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0557");
        Object[] r = row("G1", 100, "Dup Guard", phone, "addr", p.getProductCode(), 1, SERIAL_D1);

        assertEquals(1, importService.importExcel(xlsx(r)).getSuccess());

        SalesOrderImportResultVO second = importService.importExcel(xlsx(r));
        assertEquals(0, second.getSuccess());
        assertEquals(1, second.getSkipped(), "同客户同日期已存在 → 跳过");
        assertEquals(0, second.getFailed());

        assertEquals(1, orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).size(), "不得重复建单");
    }

    @Test
    void unit_price_rounds_but_amount_keeps_excel_value() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0558");

        importService.importExcel(xlsx(
                row("H1", 700, "Rounding", phone, "addr", p.getProductCode(), 3, SERIAL_D1)));

        SalesOrder o = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).get(0);
        SalesOrderItem item = itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItem>()
                .eq(SalesOrderItem::getOrderId, o.getId())).get(0);

        assertEquals(0, new BigDecimal("233.33").compareTo(item.getUnitPrice()), "700/3 四舍五入到 2 位");
        assertEquals(0, new BigDecimal("700.00").compareTo(item.getAmount()), "行金额保留 Excel 原值");
        assertEquals(0, new BigDecimal("700.00").compareTo(o.getTotalAmount()), "总额精确等于 Excel 之和");
        assertEquals("H1", item.getExternalOrderId());
    }

    @Test
    void deducts_stock_like_manual_order() throws Exception {
        SupplierProduct p = anyProduct(0);
        String phone = uniquePhone("0559");
        int before = currentStock(p.getId());

        importService.importExcel(xlsx(
                row("I1", 100, "Stock Check", phone, "addr", p.getProductCode(), 2, SERIAL_D1)));

        assertEquals(before - 2, currentStock(p.getId()), "导入应与手工下单一样扣库存");
    }

    private int currentStock(Long supplierProductId) {
        InventoryStock s = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSupplierProductId, supplierProductId));
        return s == null ? 0 : s.getQuantity();
    }
}
```

- [ ] **Step 4: 跑测试确认失败**

```bash
cd backend && mvn -q test -Dtest=SalesOrderImportServiceTest
```

Expected: 编译失败 —— `SalesOrderImportService` bean 不存在。

- [ ] **Step 5: 写实现**

`SalesOrderImportServiceImpl.java`：

```java
package africa.zokomart.admin.module.sales.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.importer.SalesImportKey;
import africa.zokomart.admin.module.sales.importer.SalesImportRow;
import africa.zokomart.admin.module.sales.importer.SalesOrderExcelParser;
import africa.zokomart.admin.module.sales.importer.SalesOrderGrouper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.sales.service.SalesOrderImportService;
import africa.zokomart.admin.module.sales.service.SalesOrderService;
import africa.zokomart.admin.module.sales.vo.SalesOrderImportError;
import africa.zokomart.admin.module.sales.vo.SalesOrderImportResultVO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 销售订单 Excel 导入。
 *
 * <p>本类<b>刻意不加</b> {@code @Transactional}：落库通过注入的 {@link SalesOrderService} 代理调用，
 * 由 {@code create()} 自己的事务边界保证「每单一个事务，单失败只回滚该单」。
 * 若在本类上加事务，一单失败会把整批回滚，与「整单失败、其余照常」的需求冲突。
 */
@Service
@RequiredArgsConstructor
public class SalesOrderImportServiceImpl implements SalesOrderImportService {

    private static final String XLSX_SUFFIX = ".xlsx";

    private final SalesOrderService salesOrderService;
    private final SalesOrderMapper salesOrderMapper;
    private final SupplierProductMapper supplierProductMapper;

    private final SalesOrderExcelParser parser = new SalesOrderExcelParser();
    private final SalesOrderGrouper grouper = new SalesOrderGrouper();

    @Override
    public SalesOrderImportResultVO importExcel(MultipartFile file) {
        List<SalesImportRow> rows = parser.parse(readBytes(file));
        LinkedHashMap<SalesImportKey, List<SalesImportRow>> groups = grouper.group(rows);

        SalesOrderImportResultVO result = new SalesOrderImportResultVO();
        result.setTotalRows(rows.size());
        result.setOrderCount(groups.size());

        Set<SalesImportKey> existing = loadExistingKeys(groups.keySet());

        for (Map.Entry<SalesImportKey, List<SalesImportRow>> e : groups.entrySet()) {
            handleGroup(e.getKey(), e.getValue(), existing, result);
        }
        return result;
    }

    private void handleGroup(SalesImportKey key, List<SalesImportRow> group,
                             Set<SalesImportKey> existing, SalesOrderImportResultVO result) {
        try {
            String badRow = group.stream().map(SalesImportRow::error)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            if (badRow != null) {
                throw new IllegalArgumentException(badRow);
            }
            if (existing.contains(key)) {
                result.setSkipped(result.getSkipped() + 1);
                return;
            }
            salesOrderService.create(toDto(group));
            existing.add(key); // 防同一文件内后续重复
            result.setSuccess(result.getSuccess() + 1);
        } catch (IllegalArgumentException | BusinessException ex) {
            recordFailure(result, group, ex.getMessage());
        } catch (Exception ex) {
            recordFailure(result, group, "订单处理异常: " + ex.getMessage());
        }
    }

    private SalesOrderCreateDTO toDto(List<SalesImportRow> group) {
        SalesImportRow first = group.get(0);
        SalesOrderCreateDTO dto = new SalesOrderCreateDTO();
        dto.setCustomerName(first.customerName());
        dto.setCustomerPhone(first.phone());
        dto.setCustomerAddress(first.address());
        dto.setCity(blankToNull(first.city()));
        dto.setOrderDate(first.orderDate());

        List<SalesOrderCreateDTO.Item> items = new ArrayList<>(group.size());
        for (SalesImportRow r : group) {
            SupplierProduct sp = resolveProduct(r.productCode());
            SalesOrderCreateDTO.Item item = new SalesOrderCreateDTO.Item();
            item.setSupplierProductId(sp.getId());
            item.setQty(r.quantity());
            // Sale Price 是行小计：单价 = 小计 / 数量；amount 保留原值，避免除不尽导致总额漂移
            item.setUnitPrice(r.salePrice().divide(BigDecimal.valueOf(r.quantity()), 2, RoundingMode.HALF_UP));
            item.setAmount(r.salePrice());
            item.setExternalOrderId(r.externalOrderId());
            items.add(item);
        }
        dto.setItems(items);
        return dto;
    }

    /** product_code 在 supplier_product 里只是「供应商内唯一」，跨供应商可能重名 → 歧义视为失败。 */
    private SupplierProduct resolveProduct(String code) {
        List<SupplierProduct> found = supplierProductMapper.selectList(
                Wrappers.<SupplierProduct>lambdaQuery().eq(SupplierProduct::getProductCode, code));
        if (found.isEmpty()) {
            throw new IllegalArgumentException("产品编码不存在: " + code);
        }
        if (found.size() > 1) {
            throw new IllegalArgumentException(
                    "产品编码 " + code + " 匹配到 " + found.size() + " 个供应商产品，无法确定");
        }
        return found.get(0);
    }

    /** 一次性把相关日期的已有订单读进内存建查重集合，避免逐单查库。 */
    private Set<SalesImportKey> loadExistingKeys(Set<SalesImportKey> incoming) {
        List<LocalDate> dates = incoming.stream()
                .map(SalesImportKey::orderDate).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (dates.isEmpty()) {
            return new HashSet<>();
        }
        return salesOrderMapper.selectList(Wrappers.<SalesOrder>lambdaQuery()
                        .in(SalesOrder::getOrderDate, dates))
                .stream()
                .map(o -> SalesImportKey.of(o.getCustomerPhone(), o.getCustomerName(),
                        o.getCustomerAddress(), o.getOrderDate()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void recordFailure(SalesOrderImportResultVO result, List<SalesImportRow> group, String reason) {
        result.setFailed(result.getFailed() + 1);
        result.getErrors().add(new SalesOrderImportError(
                group.stream().map(r -> String.valueOf(r.rowNum())).collect(Collectors.joining(",")),
                group.stream().map(SalesImportRow::externalOrderId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.joining(",")),
                group.get(0).customerName(),
                group.stream().map(SalesImportRow::productCode)
                        .filter(java.util.Objects::nonNull).findFirst().orElse(null),
                reason));
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(XLSX_SUFFIX)) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
```

**注意**：`recordFailure` 里 `productCode` 取的是组内第一个非 null 编码，而 `unknown_product_code_fails_that_order_only` 断言的是出错的那个编码 —— 该用例里失败组只有一行，两者一致。

- [ ] **Step 6: 跑测试确认通过**

```bash
cd backend && mvn -q test -Dtest=SalesOrderImportServiceTest
```

Expected: 9 个用例全部 PASS。
**若 `InventoryStock` 的字段名不是 `quantity`/`supplierProductId`**，按 `backend/src/main/java/africa/zokomart/admin/module/inventory/entity/InventoryStock.java` 的实际字段改 `currentStock()`。

- [ ] **Step 7: 提交**

```bash
cd backend
git add src/main/java/africa/zokomart/admin/module/sales/ src/test/java/africa/zokomart/admin/sales/SalesOrderImportServiceTest.java
git commit -m "feat(sales): Excel 导入服务（按客户+日期归并、逐单事务、库内查重）

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: 导入接口与权限

**Files:**
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/controller/SalesOrderController.java`
- Test: `backend/src/test/java/africa/zokomart/admin/sales/SalesOrderImportApiTest.java`

**Interfaces:**
- Consumes: `SalesOrderImportService.importExcel`（Task 4）
- Produces: `POST /api/sales-orders/import`（multipart，字段名 `file`），权限 `sales:order:import`

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/africa/zokomart/admin/sales/SalesOrderImportApiTest.java`：

```java
package africa.zokomart.admin.sales;

import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.entity.SalesOrderItem;
import africa.zokomart.admin.module.sales.mapper.SalesOrderItemMapper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderImportApiTest {

    static final String[] HEADER = {
            "Order ID", "Sale Price", "Customer Name", "City", "Shipping Address",
            "Phone Number", "Product Name", "Product Code", "Quantity", "Status", "Order Date"};

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    SalesOrderMapper orderMapper;
    @Autowired
    SalesOrderItemMapper itemMapper;
    @Autowired
    SupplierProductMapper supplierProductMapper;

    String phone;

    @AfterEach
    void cleanup() {
        if (phone == null) return;
        for (SalesOrder o : orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone))) {
            itemMapper.delete(new LambdaQueryWrapper<SalesOrderItem>()
                    .eq(SalesOrderItem::getOrderId, o.getId()));
            orderMapper.deleteById(o.getId());
        }
        phone = null;
    }

    private String token(String user, String pwd) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pwd + "\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private MockMultipartFile oneRowXlsx(String code) throws Exception {
        phone = "0560" + (System.nanoTime() % 1000000L);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Workbook wb = new Workbook(os, "test", "1.0")) {
            Worksheet ws = wb.newWorksheet("Sheet1");
            for (int c = 0; c < HEADER.length; c++) ws.value(0, c, HEADER[c]);
            Object[] row = {"API1", 500, "Api Import", "Accra", "api addr", phone, "Any", code, 1, "未发货", 46280};
            for (int c = 0; c < row.length; c++) {
                if (row[c] instanceof Number n) ws.value(1, c, n);
                else ws.value(1, c, String.valueOf(row[c]));
            }
        }
        return new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", os.toByteArray());
    }

    private String anyCode() {
        List<SupplierProduct> all = supplierProductMapper.selectList(
                new LambdaQueryWrapper<SupplierProduct>().last("limit 1"));
        assertTrue(!all.isEmpty(), "dev 库需要至少一个 supplier_product");
        return all.get(0).getProductCode();
    }

    @Test
    void superadmin_can_import() throws Exception {
        mvc.perform(multipart("/api/sales-orders/import").file(oneRowXlsx(anyCode()))
                        .header("Authorization", token("superadmin", "Admin@123")))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.skipped").value(0))
                .andExpect(jsonPath("$.data.failed").value(0));
    }

    @Test
    void non_xlsx_rejected_with_40009() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "orders.csv", "text/csv",
                "Order ID,Sale Price\n1,2\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/sales-orders/import").file(csv)
                        .header("Authorization", token("superadmin", "Admin@123")))
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void request_without_token_is_rejected() throws Exception {
        // 不断言具体状态码/业务码（未登录走 Sa-Token 异常通道，形态可能是 401 或 Result）；
        // 只要求它没有成功，且没有建单。
        MvcResult r = mvc.perform(multipart("/api/sales-orders/import").file(oneRowXlsx(anyCode())))
                .andReturn();
        assertTrue(!r.getResponse().getContentAsString().contains("\"code\":0"),
                "未登录不得导入成功，实际响应: " + r.getResponse().getContentAsString());
        assertTrue(orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerPhone, phone)).isEmpty(), "未登录不得建单");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd backend && mvn -q test -Dtest=SalesOrderImportApiTest
```

Expected: FAIL —— `/api/sales-orders/import` 返回 404 或非 0 业务码。

- [ ] **Step 3: 加接口**

`SalesOrderController.java`：字段区加

```java
    private final africa.zokomart.admin.module.sales.service.SalesOrderImportService salesOrderImportService;
```

在 `create()` 之后加

```java
    @PostMapping("/import")
    @SaCheckPermission("sales:order:import")
    public Result<africa.zokomart.admin.module.sales.vo.SalesOrderImportResultVO> importExcel(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return Result.ok(salesOrderImportService.importExcel(file));
    }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
cd backend && mvn -q test -Dtest=SalesOrderImportApiTest
```

Expected: 3 个用例 PASS。

- [ ] **Step 5: 全量回归 + 提交**

```bash
cd backend && mvn test
```

Expected: 全部 PASS。

```bash
git add src/main/java/africa/zokomart/admin/module/sales/controller/SalesOrderController.java src/test/java/africa/zokomart/admin/sales/SalesOrderImportApiTest.java
git commit -m "feat(sales): POST /api/sales-orders/import 接口与 sales:order:import 权限

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: 前端 api / types / i18n

**Files:**
- Modify: `frontend/src/types/sales.d.ts`
- Modify: `frontend/src/api/sales/order.ts`
- Modify: `frontend/src/locales/lang/zh-CN.ts`（`sales.order` 块内）
- Modify: `frontend/src/locales/lang/en-US.ts`（同一块）
- Test: `frontend/tests/unit/locales.spec.ts`（已有，跑通即可）

**Interfaces:**
- Produces（Task 7 依赖）：
  - `SalesOrderImportError`：`{ rows: string; externalOrderIds: string; customerName: string; productCode: string | null; reason: string }`
  - `SalesOrderImportResult`：`{ totalRows: number; orderCount: number; success: number; skipped: number; failed: number; errors: SalesOrderImportError[] }`
  - `apiSalesOrderImport(form: FormData) => Promise<SalesOrderImportResult>`
  - i18n keys（`sales.order.` 前缀）：`importOrders` `importTitle` `pickXlsx` `xlsxOnly` `selectXlsx` `startImport` `importDone` `columnsHint` `resTotalRows` `resOrderCount` `resSuccess` `resSkipped` `resFailed` `errRows` `errOrderIds` `errCustomer` `errCode` `errReason`

- [ ] **Step 1: 加类型**

`frontend/src/types/sales.d.ts` 末尾追加：

```ts
export interface SalesOrderImportError {
  rows: string;
  externalOrderIds: string;
  customerName: string;
  productCode: string | null;
  reason: string;
}

export interface SalesOrderImportResult {
  totalRows: number;
  orderCount: number;
  success: number;
  skipped: number;
  failed: number;
  errors: SalesOrderImportError[];
}
```

同时给 `SalesOrderVO` 补两个可选字段：

```ts
  city?: string | null;
  orderDate?: string | null;
```

- [ ] **Step 2: 加 api**

`frontend/src/api/sales/order.ts`：import 里加 `SalesOrderImportResult`，文件末尾加

```ts
// 导入订单：multipart/form-data，字段名 file；后端按客户+日期归并成订单
export const apiSalesOrderImport = (form: FormData) =>
  http.post<SalesOrderImportResult>('/sales-orders/import', form);
```

- [ ] **Step 3: 加中文文案**

`frontend/src/locales/lang/zh-CN.ts` 的 `sales.order` 块内追加：

```ts
      importOrders: '导入订单',
      importTitle: '导入销售订单',
      pickXlsx: '选择 .xlsx 文件',
      xlsxOnly: '只支持 .xlsx 文件',
      selectXlsx: '请先选择 .xlsx 文件',
      startImport: '开始导入',
      importDone: '导入完成：成功 {success} 单，跳过 {skipped} 单，失败 {failed} 单',
      columnsHint:
        '必需列：Order ID、Sale Price、Customer Name、City、Shipping Address、Phone Number、Product Name、Product Code、Quantity、Order Date；可选列：Status（忽略）。同一客户（电话+姓名+地址）同一天的多行会合并为一张订单。',
      resTotalRows: '数据行',
      resOrderCount: '归并订单',
      resSuccess: '成功',
      resSkipped: '跳过(已存在)',
      resFailed: '失败',
      errRows: '源文件行',
      errOrderIds: 'Order ID',
      errCustomer: '客户',
      errCode: '产品编码',
      errReason: '原因',
```

- [ ] **Step 4: 加英文文案**

`frontend/src/locales/lang/en-US.ts` 的同一 `sales.order` 块内追加：

```ts
      importOrders: 'Import Orders',
      importTitle: 'Import Sales Orders',
      pickXlsx: 'Choose .xlsx file',
      xlsxOnly: 'Only .xlsx files are supported',
      selectXlsx: 'Please choose an .xlsx file first',
      startImport: 'Start Import',
      importDone: 'Done: {success} created, {skipped} skipped, {failed} failed',
      columnsHint:
        'Required: Order ID, Sale Price, Customer Name, City, Shipping Address, Phone Number, Product Name, Product Code, Quantity, Order Date. Optional: Status (ignored). Rows sharing phone + name + address on the same date are merged into one order.',
      resTotalRows: 'Rows',
      resOrderCount: 'Orders',
      resSuccess: 'Created',
      resSkipped: 'Skipped (exists)',
      resFailed: 'Failed',
      errRows: 'Source rows',
      errOrderIds: 'Order ID',
      errCustomer: 'Customer',
      errCode: 'Product code',
      errReason: 'Reason',
```

- [ ] **Step 5: 跑 locale 一致性测试与类型检查**

```bash
cd frontend && pnpm test:unit -- locales && pnpm exec vue-tsc --noEmit
```

Expected: PASS —— `locales.spec.ts` 会校验中英 key 对齐，缺任何一个都会红。

- [ ] **Step 6: 提交**

```bash
cd frontend
git add src/types/sales.d.ts src/api/sales/order.ts src/locales/lang/zh-CN.ts src/locales/lang/en-US.ts
git commit -m "feat(sales): 导入订单的 api/types/i18n

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: 前端导入弹窗与入口按钮

**Files:**
- Create: `frontend/src/views/sales/order/SalesOrderImportModal.vue`
- Modify: `frontend/src/views/sales/order/index.vue`（script 顶部 import、工具栏 `a-space` 约 188-199 行、模板末尾挂载弹窗）
- Test: `frontend/tests/unit/sales-order-import-modal.spec.ts`

**Interfaces:**
- Consumes: `apiSalesOrderImport`、`SalesOrderImportResult`（Task 6）
- Produces: `SalesOrderImportModal` 组件，props `{ visible: boolean }`，emits `update:visible`、`ok`；
  `defineExpose({ file, beforeUpload, onSubmit, result })`

- [ ] **Step 1: 写失败测试**

`frontend/tests/unit/sales-order-import-modal.spec.ts`：

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import SalesOrderImportModal from '@/views/sales/order/SalesOrderImportModal.vue';
import type { SalesOrderImportResult } from '@/types/sales';

const importRes: SalesOrderImportResult = {
  totalRows: 14,
  orderCount: 9,
  success: 8,
  skipped: 0,
  failed: 1,
  errors: [
    {
      rows: '4,5,6,7',
      externalOrderIds: 'SSK202609151503',
      customerName: 'Baaba Maison',
      productCode: 'BD-55',
      reason: '产品编码不存在: BD-55',
    },
  ],
};

const apiSalesOrderImport = vi.fn(async (..._a: any[]) => importRes);
vi.mock('@/api/sales/order', () => ({
  apiSalesOrderImport: (...a: any[]) => apiSalesOrderImport(...a),
}));

const stubs = {
  'a-modal': true, 'a-upload': true, 'a-button': true, 'a-table': true,
  'a-tag': true, 'a-space': true, 'a-alert': true, 'a-descriptions': true,
  'a-descriptions-item': true,
};
const mountModal = () =>
  mount(SalesOrderImportModal, { props: { visible: true }, global: { stubs } });

const xlsxFile = () =>
  new File(['x'], 'orders.xlsx', {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });

describe('销售订单导入弹窗', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    apiSalesOrderImport.mockClear();
  });

  it('未选文件时提交不调接口', async () => {
    const w = mountModal();
    await flushPromises();
    await w.vm.onSubmit();
    expect(apiSalesOrderImport).not.toHaveBeenCalled();
  });

  it('beforeUpload 接受 .xlsx 并填入 file', async () => {
    const w = mountModal();
    await flushPromises();
    const f = xlsxFile();
    expect(w.vm.beforeUpload(f)).toBe(false); // 阻止自动上传
    expect(w.vm.file).toBe(f);
  });

  it('beforeUpload 拒绝非 .xlsx，不填入 file', async () => {
    const w = mountModal();
    await flushPromises();
    const csv = new File(['x'], 'orders.csv', { type: 'text/csv' });
    expect(w.vm.beforeUpload(csv)).toBe(false);
    expect(w.vm.file).toBe(null);
  });

  it('提交后调接口、回填 result、emit ok 并清空 file', async () => {
    const w = mountModal();
    await flushPromises();
    w.vm.beforeUpload(xlsxFile());
    await w.vm.onSubmit();
    await flushPromises();
    expect(apiSalesOrderImport).toHaveBeenCalledTimes(1);
    expect(w.vm.result).toEqual(importRes);
    expect(w.emitted('ok')).toBeTruthy();
    expect(w.vm.file).toBe(null);
  });

  it('提交携带 FormData，字段名为 file', async () => {
    const w = mountModal();
    await flushPromises();
    const f = xlsxFile();
    w.vm.beforeUpload(f);
    await w.vm.onSubmit();
    await flushPromises();
    const form = apiSalesOrderImport.mock.calls[0][0] as FormData;
    expect(form.get('file')).toBe(f);
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd frontend && pnpm test:unit -- sales-order-import-modal
```

Expected: FAIL —— 找不到 `SalesOrderImportModal.vue`。

- [ ] **Step 3: 写弹窗组件**

`frontend/src/views/sales/order/SalesOrderImportModal.vue`：

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import { apiSalesOrderImport } from '@/api/sales/order';
import type { SalesOrderImportResult } from '@/types/sales';

const props = defineProps<{ visible: boolean }>();
const emit = defineEmits<{ 'update:visible': [boolean]; ok: [] }>();

const { t } = useI18n();

const file = ref<File | null>(null);
const result = ref<SalesOrderImportResult | null>(null);
const submitting = ref(false);

// 后缀校验放在前端，避免把明显不对的文件发到后端只换回一句笼统的 40009
const beforeUpload = (f: File) => {
  if (!f.name.toLowerCase().endsWith('.xlsx')) {
    message.warning(t('sales.order.xlsxOnly'));
    return false;
  }
  file.value = f;
  result.value = null;
  return false; // 阻止 a-upload 自动上传，由 onSubmit 手动提交
};

const onSubmit = async () => {
  if (!file.value) {
    message.warning(t('sales.order.selectXlsx'));
    return;
  }
  const form = new FormData();
  form.append('file', file.value);
  submitting.value = true;
  try {
    result.value = await apiSalesOrderImport(form);
    message.success(
      t('sales.order.importDone', {
        success: result.value.success,
        skipped: result.value.skipped,
        failed: result.value.failed,
      }),
    );
    emit('ok');
    file.value = null;
  } finally {
    submitting.value = false;
  }
};

const close = () => {
  emit('update:visible', false);
  file.value = null;
  result.value = null;
};

const errorColumns = [
  { title: t('sales.order.errRows'), dataIndex: 'rows', key: 'rows', width: 110 },
  { title: t('sales.order.errOrderIds'), dataIndex: 'externalOrderIds', key: 'externalOrderIds', width: 160 },
  { title: t('sales.order.errCustomer'), dataIndex: 'customerName', key: 'customerName', width: 140 },
  { title: t('sales.order.errCode'), dataIndex: 'productCode', key: 'productCode', width: 120 },
  { title: t('sales.order.errReason'), dataIndex: 'reason', key: 'reason' },
];

defineExpose({ file, beforeUpload, onSubmit, result });
</script>

<template>
  <a-modal
    :open="props.visible"
    :title="t('sales.order.importTitle')"
    :footer="null"
    :mask-closable="false"
    width="860"
    @cancel="close"
  >
    <a-space direction="vertical" style="width: 100%">
      <a-alert type="info" show-icon :message="t('sales.order.columnsHint')" />

      <a-upload :before-upload="beforeUpload" :max-count="1" accept=".xlsx">
        <a-button data-test="sales-import-pick">{{ t('sales.order.pickXlsx') }}</a-button>
      </a-upload>

      <a-button
        type="primary"
        :loading="submitting"
        :disabled="!file"
        data-test="sales-import-submit"
        @click="onSubmit"
      >
        {{ t('sales.order.startImport') }}
      </a-button>

      <template v-if="result">
        <a-space>
          <a-tag>{{ t('sales.order.resTotalRows') }}: {{ result.totalRows }}</a-tag>
          <a-tag color="blue">{{ t('sales.order.resOrderCount') }}: {{ result.orderCount }}</a-tag>
          <a-tag color="green">{{ t('sales.order.resSuccess') }}: {{ result.success }}</a-tag>
          <a-tag color="orange">{{ t('sales.order.resSkipped') }}: {{ result.skipped }}</a-tag>
          <a-tag color="red">{{ t('sales.order.resFailed') }}: {{ result.failed }}</a-tag>
        </a-space>
        <a-table
          v-if="result.errors.length"
          :columns="errorColumns"
          :data-source="result.errors"
          :pagination="false"
          :scroll="{ y: 280 }"
          row-key="rows"
          size="small"
          data-test="sales-import-errors"
        />
      </template>
    </a-space>
  </a-modal>
</template>
```

- [ ] **Step 4: 跑测试确认通过**

```bash
cd frontend && pnpm test:unit -- sales-order-import-modal
```

Expected: 5 个用例 PASS。

- [ ] **Step 5: 挂到页面上**

`frontend/src/views/sales/order/index.vue`：

script 里，`LabelPrintDrawer` 的 import 之后加

```ts
import SalesOrderImportModal from './SalesOrderImportModal.vue';
```

在 `const labelDrawerRef = ...` 之后加

```ts
const importVisible = ref(false);
```

`defineExpose` 里追加 `importVisible`。

模板工具栏 `a-space` 内，「打印今日面单」按钮之后、「新增销售订单」之前插入

```vue
          <a-button
            v-perm="'sales:order:import'"
            data-test="sales-import"
            @click="importVisible = true"
          >
            {{ t('sales.order.importOrders') }}
          </a-button>
```

模板里 `<LabelPrintDrawer ref="labelDrawerRef" />` 之后插入

```vue
    <SalesOrderImportModal v-model:visible="importVisible" @ok="tableRef?.reload()" />
```

- [ ] **Step 6: 跑页面测试与构建**

```bash
cd frontend && pnpm test:unit && pnpm exec vue-tsc --noEmit && pnpm build
```

Expected: 全部 PASS，build 成功。
`sales-order-page.spec.ts` 若因新增子组件报未知标签，在其 `stubs` 里加 `'SalesOrderImportModal': true`。

- [ ] **Step 7: 提交**

```bash
cd frontend
git add src/views/sales/order/ tests/unit/sales-order-import-modal.spec.ts
git commit -m "feat(sales): 销售订单导入弹窗与入口按钮

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: 模板修正与全链验证

**Files:**
- Modify: `utils/OrdersTemplate.xlsx`（工作区根，**不属于任何子仓库，不进 git**）
- Modify: `backend/docs/superpowers/specs/2026-09-15-sales-order-import-design.md`（只在实现与设计有出入时更新）

- [ ] **Step 1: 修正模板表头拼写**

`utils/OrdersTemplate.xlsx` 的 B1 从 `Sake Price` 改成 `Sale Price`。xlsx 是 zip，直接改 `xl/sharedStrings.xml`：

```bash
cd /tmp && rm -rf tplfix && mkdir tplfix && cd tplfix
unzip -q "D:/GHANA/claude/admin.zokomart.africa/utils/OrdersTemplate.xlsx"
sed -i 's|<si><t>Sake Price</t>|<si><t>Sale Price</t>|' xl/sharedStrings.xml
grep -c "Sale Price" xl/sharedStrings.xml
```

Expected: 输出 `1`。然后重新打包。

**注意**：本机 Git Bash **没有 `zip`**（`python`/`python3` 也是 Windows 商店占位符，不可用），
用 JDK 自带的 `jar` 打包 —— 它写出的就是标准 zip，xlsx 只需要这个：

```bash
cd /tmp/tplfix && rm -f out.xlsx && \
jar --create --file out.xlsx --no-manifest -C . . && \
unzip -l out.xlsx | grep -c "xl/worksheets/sheet1.xml" && \
cp out.xlsx "D:/GHANA/claude/admin.zokomart.africa/utils/OrdersTemplate.xlsx" && \
echo replaced
```

Expected: 先打印 `1`（确认打包结果里有 sheet），再打印 `replaced`。

- [ ] **Step 2: 确认修正后的模板能被解析器读懂**

```bash
cd /tmp && rm -rf verify && mkdir verify && cd verify
unzip -q "D:/GHANA/claude/admin.zokomart.africa/utils/OrdersTemplate.xlsx" && \
grep -o "Sake Price\|Sale Price" xl/sharedStrings.xml
```

Expected: 只输出 `Sale Price`，没有 `Sake Price`。

- [ ] **Step 3: 重建并重启后端 jar**

改了代码必须重建，否则旧 jar 上新端点会 500。

```bash
cd backend
# 先停掉占用 target/*.jar 的旧进程，否则 clean 失败
taskkill //F //IM java.exe 2>/dev/null || true
mvn clean package -DskipTests -q && ls -la target/*.jar
```

然后后台启动，用 `local` profile（密钥都在未提交的 `application-local.yml` 里）：

```bash
cd backend && java -jar target/*.jar --spring.profiles.active=local
```

启动前确认 MySQL 与 **Redis（需带密码启动）** 都在跑，否则应用起不来。

- [ ] **Step 4: 启动前端 dev server 并跑 UI 全链**

用 Browser pane 的 `preview_start`（`.claude/launch.json` 里的 `frontend-dev`，端口 5199），**不要用 Bash 起 dev server**。

依次验证：

1. 以 superadmin 登录 → 进「销售订单」→ 工具栏能看到「导入订单」按钮。
2. 点开弹窗 → 选 `utils/OrdersTemplate.xlsx` → 开始导入。
3. 摘要应为：**数据行 14 / 归并订单 9**。成功数取决于 dev 库里这些 `Product Code` 存不存在 ——
   把失败表里报"产品编码不存在"的编码记下来，这是**数据问题不是代码问题**，在验证报告里如实列出。
4. 列表里能看到新建的订单；抽查 Baaba Maison 那一单，详情应有 **4 件商品、应收合计 1350**。
5. **再导一次同一个文件** → 摘要里成功 0、跳过应等于第一次的成功数，列表订单数不变。
6. 切换到英文界面，确认导入按钮与弹窗文案都是英文（无中文残留）。

- [ ] **Step 5: 两仓全量测试收尾**

```bash
cd backend && mvn test
```

```bash
cd frontend && pnpm test:unit && pnpm exec vue-tsc --noEmit && pnpm build
```

Expected: 两边全绿。

- [ ] **Step 6: 如实记录验证结果**

把第 4 步每一项的实际结果（含失败的产品编码清单）写进最终汇报。
**任何一项没跑通就不要声称完成** —— 照 superpowers:verification-before-completion 的要求，先给证据再下结论。

- [ ] **Step 7: 提交（若 spec 有更新）**

```bash
cd backend
git add docs/
git commit -m "docs(sales): 按实现校准导入设计 spec

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```
