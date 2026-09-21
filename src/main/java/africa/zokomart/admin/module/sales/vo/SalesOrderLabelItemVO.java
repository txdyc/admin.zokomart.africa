package africa.zokomart.admin.module.sales.vo;

import lombok.Data;

/** 面单明细：贴纸按件展开时，每件要印的商品编码与品名。 */
@Data
public class SalesOrderLabelItemVO {
    private String productCode;
    private String productName;
    private Integer qty;
}
