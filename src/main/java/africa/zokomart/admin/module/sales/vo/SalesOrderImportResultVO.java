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
