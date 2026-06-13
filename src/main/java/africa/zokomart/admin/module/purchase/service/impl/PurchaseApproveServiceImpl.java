package africa.zokomart.admin.module.purchase.service.impl;

import africa.zokomart.admin.common.base.BizNo;
import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.purchase.constant.PurchaseConst;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrder;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrderItem;
import africa.zokomart.admin.module.purchase.entity.PurchasePlan;
import africa.zokomart.admin.module.purchase.entity.PurchasePlanItem;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderItemMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchasePlanItemMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchasePlanMapper;
import africa.zokomart.admin.module.purchase.service.PurchaseApproveService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PurchaseApproveServiceImpl implements PurchaseApproveService {

    private final PurchasePlanMapper planMapper;
    private final PurchasePlanItemMapper planItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long planId, Long approverId) {
        PurchasePlan plan = requirePending(planId);

        List<PurchasePlanItem> items = planItemMapper.selectList(
                Wrappers.<PurchasePlanItem>lambdaQuery().eq(PurchasePlanItem::getPlanId, planId));
        if (items.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "采购计划无明细，无法审批");
        }

        // 按供应商分组（保持稳定顺序）
        Map<Long, List<PurchasePlanItem>> bySupplier = new LinkedHashMap<>();
        for (PurchasePlanItem it : items) {
            bySupplier.computeIfAbsent(it.getSupplierId(), k -> new java.util.ArrayList<>()).add(it);
        }

        bySupplier.forEach((supplierId, group) -> {
            PurchaseOrder order = new PurchaseOrder();
            order.setOrderNo(BizNo.gen(PurchaseConst.NO_ORDER));
            order.setPlanId(planId);
            order.setSupplierId(supplierId);
            order.setStatus(PurchaseConst.ORDER_PENDING_PAYMENT);
            int totalQty = group.stream().mapToInt(PurchasePlanItem::getPurchaseQty).sum();
            BigDecimal totalAmount = group.stream().map(PurchasePlanItem::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setTotalQty(totalQty);
            order.setTotalAmount(totalAmount);
            order.setPaidAmount(BigDecimal.ZERO);
            orderMapper.insert(order);

            for (PurchasePlanItem src : group) {
                PurchaseOrderItem oi = new PurchaseOrderItem();
                oi.setOrderId(order.getId());
                oi.setSupplierProductId(src.getSupplierProductId());
                oi.setProductName(src.getProductName());
                oi.setProductCode(src.getProductCode());
                oi.setWholesalePrice(src.getWholesalePrice());
                oi.setQty(src.getPurchaseQty());
                oi.setAmount(src.getAmount());
                oi.setPaymentStatus(PurchaseConst.PAY_UNSET);
                orderItemMapper.insert(oi);
            }
        });

        plan.setStatus(PurchaseConst.PLAN_APPROVED);
        plan.setApproverId(approverId);
        plan.setApproveTime(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long planId, Long approverId, String reason) {
        PurchasePlan plan = requirePending(planId);
        plan.setStatus(PurchaseConst.PLAN_REJECTED);
        plan.setApproverId(approverId);
        plan.setApproveTime(LocalDateTime.now());
        plan.setApproveRemark(reason);
        planMapper.updateById(plan);
    }

    private PurchasePlan requirePending(Long planId) {
        PurchasePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "采购计划不存在");
        }
        if (!PurchaseConst.PLAN_PENDING.equals(plan.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "仅待审批(PENDING)计划可审批");
        }
        return plan;
    }
}
