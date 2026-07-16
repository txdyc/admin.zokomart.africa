package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 每日销售趋势点（下单口径：按 create_time 分组）。 */
@Data
public class DailyTrendVO {
    private LocalDate day;
    /** 当日已完成订单实收金额。 */
    private BigDecimal revenue;
    /** 当日下单数。 */
    private Long orders;
}
