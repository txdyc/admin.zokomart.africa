package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

/** 销售订单按状态计数（用于漏斗与各类交付率）。 */
@Data
public class StatusCountVO {
    private String status;
    private Long cnt;
}
