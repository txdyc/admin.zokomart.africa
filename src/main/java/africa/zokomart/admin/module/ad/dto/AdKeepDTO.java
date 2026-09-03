package africa.zokomart.admin.module.ad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class AdKeepDTO {

    @NotNull(message = "请选择关联产品")
    private Long supplierProductId;

    @NotEmpty(message = "无待保留图片")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotBlank
        private String tempUrl;
        private String prompt;
        private Long modelId;
    }
}
