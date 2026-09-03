package africa.zokomart.admin.module.ad.vo;

import lombok.Data;

@Data
public class AdProductImageVO {
    private Long id;
    private Long supplierProductId;
    private String fileUrl;
    private String prompt;
    private Long modelId;
    private Integer sort;
}
