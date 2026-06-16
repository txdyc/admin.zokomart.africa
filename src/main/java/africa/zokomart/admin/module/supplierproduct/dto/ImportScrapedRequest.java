package africa.zokomart.admin.module.supplierproduct.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ImportScrapedRequest {
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    @NotNull(message = "品牌不能为空")
    private Long brandId;
    private String mode;
    private List<ScrapedProductRow> rows;
}
