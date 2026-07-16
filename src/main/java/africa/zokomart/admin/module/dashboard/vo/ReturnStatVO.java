package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

/** 明细层退货/拒收量统计。 */
@Data
public class ReturnStatVO {
    /** 拒收件数 Σ reject_qty。 */
    private Long rejectQty;
    /** 下单总件数 Σ qty。 */
    private Long totalQty;
}
