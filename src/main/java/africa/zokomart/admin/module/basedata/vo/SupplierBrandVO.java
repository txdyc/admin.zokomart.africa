package africa.zokomart.admin.module.basedata.vo;

import lombok.Data;

@Data
public class SupplierBrandVO {
    private Long id;
    private Long brandId;
    private String brandName;
    private String brandLogoUrl;
    private Integer status;
    private String remark;
}
