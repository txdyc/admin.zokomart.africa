package africa.zokomart.admin.module.wcsync.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WcSyncRowError {
    private Long supplierProductId;
    private String productCode;
    private String reason;
}
