package africa.zokomart.admin.module.purchase.service.impl;

import africa.zokomart.admin.common.base.BizNo;
import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.purchase.constant.PurchaseConst;
import africa.zokomart.admin.module.purchase.entity.ActualPurchaseOrder;
import africa.zokomart.admin.module.purchase.entity.ActualPurchaseOrderItem;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrder;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrderItem;
import africa.zokomart.admin.module.purchase.mapper.ActualPurchaseOrderItemMapper;
import africa.zokomart.admin.module.purchase.mapper.ActualPurchaseOrderMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderItemMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderMapper;
import africa.zokomart.admin.module.purchase.service.PurchaseOrderPaymentService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseOrderPaymentServiceImpl implements PurchaseOrderPaymentService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final ActualPurchaseOrderMapper actualMapper;
    private final ActualPurchaseOrderItemMapper actualItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mark(Long orderId, List<Long> itemIds, String paymentStatus) {
        PurchaseOrder order = requirePendingPayment(orderId);
        if (!Set.of(PurchaseConst.PAY_PAID, PurchaseConst.PAY_UNPAID).contains(paymentStatus)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "付款状态只能为 PAID 或 UNPAID");
        }
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未选择明细");
        }
        for (Long itemId : itemIds) {
            PurchaseOrderItem item = orderItemMapper.selectById(itemId);
            if (item == null || !item.getOrderId().equals(orderId)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "明细不属于该订单");
            }
            item.setPaymentStatus(paymentStatus);
            orderItemMapper.updateById(item);
        }
        refreshPaidAmount(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirm(Long orderId) {
        PurchaseOrder order = requirePendingPayment(orderId);

        List<PurchaseOrderItem> paidItems = orderItemMapper.selectList(
                Wrappers.<PurchaseOrderItem>lambdaQuery()
                        .eq(PurchaseOrderItem::getOrderId, orderId)
                        .eq(PurchaseOrderItem::getPaymentStatus, PurchaseConst.PAY_PAID));
        if (paidItems.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "至少一条已付款明细才能生成实际采购单");
        }

        ActualPurchaseOrder actual = new ActualPurchaseOrder();
        actual.setActualNo(BizNo.gen(PurchaseConst.NO_ACTUAL));
        actual.setPurchaseOrderId(orderId);
        actual.setSupplierId(order.getSupplierId());
        actual.setStatus(PurchaseConst.ACTUAL_PENDING_INBOUND);
        int totalQty = paidItems.stream().mapToInt(PurchaseOrderItem::getQty).sum();
        BigDecimal totalAmount = paidItems.stream().map(PurchaseOrderItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        actual.setTotalQty(totalQty);
        actual.setTotalAmount(totalAmount);
        actualMapper.insert(actual);

        for (PurchaseOrderItem src : paidItems) {
            ActualPurchaseOrderItem ai = new ActualPurchaseOrderItem();
            ai.setActualOrderId(actual.getId());
            ai.setPurchaseOrderItemId(src.getId());
            ai.setSupplierProductId(src.getSupplierProductId());
            ai.setProductName(src.getProductName());
            ai.setQty(src.getQty());
            ai.setWholesalePrice(src.getWholesalePrice());
            ai.setAmount(src.getAmount());
            ai.setInboundStatus(PurchaseConst.INBOUND_PENDING);
            ai.setInboundQty(0);
            actualItemMapper.insert(ai);
        }

        order.setStatus(PurchaseConst.ORDER_CONFIRMED);
        refreshPaidAmount(order);
        return actual.getId();
    }

    private void refreshPaidAmount(PurchaseOrder order) {
        List<PurchaseOrderItem> paid = orderItemMapper.selectList(
                Wrappers.<PurchaseOrderItem>lambdaQuery()
                        .eq(PurchaseOrderItem::getOrderId, order.getId())
                        .eq(PurchaseOrderItem::getPaymentStatus, PurchaseConst.PAY_PAID));
        BigDecimal paidAmount = paid.stream().map(PurchaseOrderItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setPaidAmount(paidAmount);
        orderMapper.updateById(order);
    }

    private PurchaseOrder requirePendingPayment(Long orderId) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        if (!PurchaseConst.ORDER_PENDING_PAYMENT.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "订单非待付款状态");
        }
        return order;
    }
}
