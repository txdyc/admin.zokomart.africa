package africa.zokomart.admin.module.sales.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.sales.constant.SalesConst;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.entity.SalesOrderItem;
import africa.zokomart.admin.module.sales.mapper.SalesOrderItemMapper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.sales.service.SalesLogisticsService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesLogisticsServiceImpl implements SalesLogisticsService {

    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper itemMapper;
    private final InventoryStockService stockService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(Long orderId, Long logisticsProviderId, BigDecimal deliveryFee) {
        SalesOrder order = requireOpen(orderId);
        if (!SalesConst.PENDING_DISPATCH.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "仅未派送订单可派送");
        }
        order.setLogisticsProviderId(logisticsProviderId);
        order.setDeliveryFee(deliveryFee);
        order.setStatus(SalesConst.DISPATCHING);
        order.setDispatchTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long orderId, String targetStatus, BigDecimal deliveryFee) {
        SalesOrder order = requireOpen(orderId);
        var allowed = SalesConst.TRANSITIONS.getOrDefault(order.getStatus(), java.util.Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION,
                    "非法状态流转 " + order.getStatus() + "→" + targetStatus);
        }
        // outcome 时补录/修正派送费；null=未提供，保留原值
        if (deliveryFee != null) {
            order.setDeliveryFee(deliveryFee);
        }
        if (SalesConst.REJECTED.equals(targetStatus)) {
            rejectWholeOrder(order);
            return;
        }
        order.setStatus(targetStatus);
        if (SalesConst.SIGNED_STATES.contains(targetStatus) && order.getSignTime() == null) {
            order.setSignTime(LocalDateTime.now());
        }
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReject(Long orderId, Long itemId, Integer rejectQty) {
        SalesOrder order = requireOpen(orderId);
        if (!SalesConst.SIGNED_STATES.contains(order.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "仅已签收订单可标记拒收");
        }
        SalesOrderItem item = itemMapper.selectById(itemId);
        if (item == null || !item.getOrderId().equals(orderId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "明细不属于该订单");
        }
        if (rejectQty == null || rejectQty < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "拒收数量不能小于 1");
        }
        int newReject = item.getRejectQty() + rejectQty;
        if (newReject > item.getQty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "累计拒收数量超过订购数量");
        }
        item.setRejectQty(newReject);
        itemMapper.updateById(item);
        // 回补库存
        stockService.changeStock(item.getSupplierProductId(), rejectQty,
                InventoryConst.TYPE_REJECT_RETURN, InventoryConst.REF_SALES_ORDER,
                order.getId(), order.getOrderNo(), "拒收回补");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long orderId) {
        SalesOrder order = requireOpen(orderId);
        if (!SalesConst.SIGNED_STATES.contains(order.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "仅已签收订单可确认完成");
        }
        List<SalesOrderItem> items = itemMapper.selectList(
                Wrappers.<SalesOrderItem>lambdaQuery().eq(SalesOrderItem::getOrderId, orderId));
        BigDecimal actual = BigDecimal.ZERO;
        for (SalesOrderItem item : items) {
            item.setActualAmount(itemActualAmount(item));
            itemMapper.updateById(item);
            actual = actual.add(item.getActualAmount());
        }
        finish(order, actual);
    }

    /**
     * 明细实收 = amount 按未拒收比例折算，而不是拿 unitPrice 重新相乘。
     * amount 是 Excel 行小计的原值（导入时 unitPrice = amount/qty 四舍五入到 2 位，
     * 除不尽会有尾差，比如 700/3=233.33，233.33*3=699.99≠700.00）；用 unitPrice 回乘
     * 会让「实收总额精确等于 Excel 之和」这个不变量在结算这一步失效。
     * 未拒收（reject=0，最常见）直接用 amount 原值，保证分毫不差；
     * 有拒收时才需要按比例折算，此时本就没有「精确等于原值」的基准可言。
     */
    private BigDecimal itemActualAmount(SalesOrderItem item) {
        if (item.getRejectQty() == 0) {
            return item.getAmount();
        }
        int remaining = item.getQty() - item.getRejectQty();
        return item.getAmount()
                .multiply(BigDecimal.valueOf(remaining))
                .divide(BigDecimal.valueOf(item.getQty()), 2, RoundingMode.HALF_UP);
    }

    /** REJECTED 全拒签：回补全部未拒收数量并自动完成、实收 0。 */
    private void rejectWholeOrder(SalesOrder order) {
        List<SalesOrderItem> items = itemMapper.selectList(
                Wrappers.<SalesOrderItem>lambdaQuery().eq(SalesOrderItem::getOrderId, order.getId()));
        for (SalesOrderItem item : items) {
            int remaining = item.getQty() - item.getRejectQty();
            if (remaining > 0) {
                stockService.changeStock(item.getSupplierProductId(), remaining,
                        InventoryConst.TYPE_REJECT_RETURN, InventoryConst.REF_SALES_ORDER,
                        order.getId(), order.getOrderNo(), "整单拒签回补");
                item.setRejectQty(item.getQty());
            }
            item.setActualAmount(BigDecimal.ZERO);
            itemMapper.updateById(item);
        }
        order.setStatus(SalesConst.REJECTED);
        finish(order, BigDecimal.ZERO);
    }

    private void finish(SalesOrder order, BigDecimal actualAmount) {
        order.setActualAmount(actualAmount);
        order.setCompleted(1);
        order.setCompleteTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /** 取订单并校验未完成（完成后只读）。 */
    private SalesOrder requireOpen(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "销售订单不存在");
        }
        if (order.getCompleted() != null && order.getCompleted() == 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单已完成，不可再操作");
        }
        return order;
    }
}
