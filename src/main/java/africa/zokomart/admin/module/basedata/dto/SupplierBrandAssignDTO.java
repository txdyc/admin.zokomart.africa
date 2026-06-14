package africa.zokomart.admin.module.basedata.dto;

import lombok.Data;

import java.util.List;

@Data
public class SupplierBrandAssignDTO {
    /** 整集设置：该供应商最终授权的品牌 id 列表。 */
    private List<Long> brandIds;
}
