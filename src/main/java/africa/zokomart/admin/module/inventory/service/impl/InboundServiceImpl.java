package africa.zokomart.admin.module.inventory.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.service.InboundService;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.purchase.constant.PurchaseConst;
import africa.zokomart.admin.module.purchase.entity.ActualPurchaseOrder;
import africa.zokomart.admin.module.purchase.entity.ActualPurchaseOrderItem;
import africa.zokomart.admin.module.purchase.mapper.ActualPurchaseOrderItemMapper;
import africa.zokomart.admin.module.purchase.mapper.ActualPurchaseOrderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InboundServiceImpl implements InboundService {

    private final ActualPurchaseOrderMapper actualMapper;
    private final ActualPurchaseOrderItemMapper actualItemMapper;
    private final InventoryStockService stockService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inbound(Long actualOrderId, List<Long> actualItemIds) {
        ActualPurchaseOrder actual = actualMapper.selectById(actualOrderId);
        if (actual == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实际采购单不存在");
        }

        List<ActualPurchaseOrderItem> allItems = actualItemMapper.selectList(
                Wrappers.<ActualPurchaseOrderItem>lambdaQuery()
                        .eq(ActualPurchaseOrderItem::getActualOrderId, actualOrderId));

        // 目标明细：未指定则整单
        List<ActualPurchaseOrderItem> targets = (actualItemIds == null || actualItemIds.isEmpty())
                ? allItems
                : allItems.stream().filter(it -> actualItemIds.contains(it.getId())).toList();

        for (ActualPurchaseOrderItem item : targets) {
            if (PurchaseConst.INBOUND_DONE.equals(item.getInboundStatus())) {
                continue; // 幂等：已入库跳过
            }
            stockService.changeStock(item.getSupplierProductId(), item.getQty(),
                    InventoryConst.TYPE_PURCHASE_IN, InventoryConst.REF_ACTUAL_PURCHASE_ORDER,
                    actual.getId(), actual.getActualNo(), "采购入库");
            item.setInboundStatus(PurchaseConst.INBOUND_DONE);
            item.setInboundQty(item.getQty());
            item.setInboundTime(LocalDateTime.now());
            actualItemMapper.updateById(item);
        }

        // 整单全部 DONE → 实际采购单 INBOUND_DONE（以最新库状态为准）
        long pending = actualItemMapper.selectCount(Wrappers.<ActualPurchaseOrderItem>lambdaQuery()
                .eq(ActualPurchaseOrderItem::getActualOrderId, actualOrderId)
                .ne(ActualPurchaseOrderItem::getInboundStatus, PurchaseConst.INBOUND_DONE));
        if (pending == 0 && !PurchaseConst.ACTUAL_INBOUND_DONE.equals(actual.getStatus())) {
            actual.setStatus(PurchaseConst.ACTUAL_INBOUND_DONE);
            actualMapper.updateById(actual);
        }
    }
}
