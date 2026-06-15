package africa.zokomart.admin.module.supplierproduct.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 导入失败行：行号（按 Excel 习惯，表头为第 1 行）、产品编码、原因。 */
@Data
@AllArgsConstructor
public class ImportRowError {
    private int row;
    private String productCode;
    private String reason;
}
