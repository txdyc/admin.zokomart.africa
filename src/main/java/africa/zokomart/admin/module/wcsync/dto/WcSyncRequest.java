package africa.zokomart.admin.module.wcsync.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WcSyncRequest {
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    @NotEmpty(message = "请至少选择一个品牌")
    private List<Long> brandIds;
    /** 目标站点 code 列表；省略/为空 = 全部已配置站点。 */
    private List<String> siteCodes;
}
