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
