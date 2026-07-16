package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 热销产品条目（按收入排序）。 */
@Data
public class TopProductVO {
    private String name;
    private String code;
    private Long qty;
    private BigDecimal revenue;
    private Long rejectQty;
}
