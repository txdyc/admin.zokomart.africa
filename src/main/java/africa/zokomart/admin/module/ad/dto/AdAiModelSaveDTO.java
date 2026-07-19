package africa.zokomart.admin.module.ad.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdAiModelSaveDTO {
    private Long id;
    @NotBlank(message = "模型名称不能为空")
    private String name;
    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;
    /** 新建必填；编辑留空 = 不修改（service 校验）。 */
    private String apiKey;
    @NotBlank(message = "模型标识不能为空")
    private String modelCode;
    private Integer enabled;
    private Integer sort;
    private String remark;
}
