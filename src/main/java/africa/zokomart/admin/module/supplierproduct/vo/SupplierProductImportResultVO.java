package africa.zokomart.admin.module.supplierproduct.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 导入结果汇总。total=数据行数；created/updated/skipped/failed 之和应等于 total。 */
@Data
public class SupplierProductImportResultVO {
    private int total;
    private int created;
    private int updated;
    private int skipped;
    private int failed;
    private List<ImportRowError> errors = new ArrayList<>();
}
