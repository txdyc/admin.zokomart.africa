package africa.zokomart.admin.module.wcsync.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WcSyncResultVO {
    private int total;
    private int created;
    private int updated;
    private int drafted;
    private int skipped;
    private int failed;
    private List<WcSyncRowError> errors = new ArrayList<>();
}
