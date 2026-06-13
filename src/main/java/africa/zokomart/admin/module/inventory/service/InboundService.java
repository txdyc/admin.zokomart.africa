package africa.zokomart.admin.module.inventory.service;

import java.util.List;

public interface InboundService {

    /**
     * 对实际采购单的指定明细入库（itemIds 为空表示整单）：
     * 已 DONE 明细跳过（幂等）；其余增加库存 + 写 PURCHASE_IN 流水 + 明细置 DONE；
     * 整单全部 DONE 后实际采购单置 INBOUND_DONE。
     */
    void inbound(Long actualOrderId, List<Long> actualItemIds);
}
