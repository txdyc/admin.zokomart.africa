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
