package africa.zokomart.admin.module.sales.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 面单专用出参：只含贴纸所需字段。 */
@Data
public class SalesOrderLabelVO {
    private Long id;
    private String orderNo;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private Integer totalQty;
    private BigDecimal totalAmount;
    /** 明细，按下单顺序；前端据此逐件展开贴纸，每张印对应商品的 product code。 */
    private List<SalesOrderLabelItemVO> items;
}
