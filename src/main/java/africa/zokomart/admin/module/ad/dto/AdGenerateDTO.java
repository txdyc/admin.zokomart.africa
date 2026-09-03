package africa.zokomart.admin.module.ad.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AdGenerateDTO {
    @NotNull(message = "请选择模型")
    private Long modelId;
    @NotBlank(message = "prompt 不能为空")
    private String prompt;
    /** 参考图公开相对路径（/files/ad-source/...），可空。 */
    private List<String> sourceImageUrls;
    @Min(1) @Max(4)
    private Integer count = 1;
}
