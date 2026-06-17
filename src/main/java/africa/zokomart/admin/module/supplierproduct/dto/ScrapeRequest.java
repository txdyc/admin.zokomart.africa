package africa.zokomart.admin.module.supplierproduct.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScrapeRequest {
    @NotBlank(message = "URL 不能为空")
    private String url;
}
