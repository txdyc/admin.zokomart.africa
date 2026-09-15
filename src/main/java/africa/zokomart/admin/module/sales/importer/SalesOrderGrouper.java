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
