package africa.zokomart.admin.module.inventory.dto;

import lombok.Data;

import java.util.List;

@Data
public class InboundDTO {
    /** 为空表示整单入库。 */
    private List<Long> itemIds;
}
