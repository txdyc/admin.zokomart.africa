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

    @Test
    void numeric_cells_render_as_plain_digits() throws Exception {
        List<SalesImportRow> rows = new SalesOrderExcelParser().parse(xlsx(FULL_HEADER,
                new Object[]{"N1", 100, "N", "Accra", "addr", 244292054, "P", 55, 1, "", 46280},
                new Object[]{"N2", 100, "N", "Accra", "addr", 12345678901L, "P", 999999999, 1, "", 46280}));

        assertEquals(2, rows.size());
        assertNull(rows.get(0).error(), "数字单元格不应报错: " + rows.get(0).error());
        assertEquals("244292054", rows.get(0).phone(), "9 位数字电话应保留原样，不带科学计数或小数点");
        assertEquals("55", rows.get(0).productCode(), "数字产品编码应保留原样");

        assertNull(rows.get(1).error(), "大数字不应报错: " + rows.get(1).error());
        assertEquals("12345678901", rows.get(1).phone(), "大数字不应变成科学计数法");
        assertEquals("999999999", rows.get(1).productCode());
    }
}
