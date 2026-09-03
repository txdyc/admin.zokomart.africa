package africa.zokomart.admin.module.wcsync.vo;

import lombok.Data;

/** 目标站点出参（同步弹框选择用）。 */
@Data
public class WcSyncSiteVO {
    private String code;
    private String name;
    private boolean configured;
}
