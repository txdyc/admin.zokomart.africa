package africa.zokomart.admin.module.ad.vo;

import lombok.Data;

@Data
public class AdAiModelVO {
    private Long id;
    private String name;
    private String baseUrl;
    /** 脱敏：**** + 尾 4 位。 */
    private String apiKeyMasked;
    private String modelCode;
    private Integer enabled;
    private Integer sort;
    private String remark;
}
