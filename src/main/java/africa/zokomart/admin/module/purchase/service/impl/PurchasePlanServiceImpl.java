package africa.zokomart.admin.module.purchase.service.impl;

import africa.zokomart.admin.common.base.BizNo;
import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.purchase.constant.PurchaseConst;
import africa.zokomart.admin.module.purchase.dto.PurchasePlanSaveDTO;
import africa.zokomart.admin.module.purchase.entity.PurchasePlan;
import africa.zokomart.admin.module.purchase.entity.PurchasePlanItem;
import africa.zokomart.admin.module.purchase.mapper.PurchasePlanItemMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchasePlanMapper;
import africa.zokomart.admin.module.purchase.service.PurchasePlanService;
import africa.zokomart.admin.module.purchase.vo.PurchasePlanItemVO;
import africa.zokomart.admin.module.purchase.vo.PurchasePlanVO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchasePlanServiceImpl extends ServiceImpl<PurchasePlanMapper, PurchasePlan>
        implements PurchasePlanService {

    private final PurchasePlanItemMapper itemMapper;
    private final SupplierProductMapper supplierProductMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurchasePlanSaveDTO dto) {
        List<PurchasePlanItem> items = buildItems(dto);

        PurchasePlan plan = new PurchasePlan();
        plan.setPlanNo(BizNo.gen(PurchaseConst.NO_PLAN));
        plan.setStatus(PurchaseConst.PLAN_DRAFT);
        plan.setRemark(dto.getRemark());
        applyTotals(plan, items);
        save(plan);

        items.forEach(it -> {
            it.setPlanId(plan.getId());
            itemMapper.insert(it);
        });
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(PurchasePlanSaveDTO dto) {
        PurchasePlan plan = getById(dto.getId());
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "采购计划不存在");
        }
        if (!isEditable(plan.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "仅草稿或退回状态可编辑");
        }
        List<PurchasePlanItem> items = buildItems(dto);

        // 替换明细
        itemMapper.delete(Wrappers.<PurchasePlanItem>lambdaQuery().eq(PurchasePlanItem::getPlanId, plan.getId()));
        items.forEach(it -> {
            it.setPlanId(plan.getId());
            itemMapper.insert(it);
        });
        plan.setRemark(dto.getRemark());
        applyTotals(plan, items);
        updateById(plan);
    }

    @Override
    public void submit(Long id) {
        PurchasePlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "采购计划不存在");
        }
        if (!isEditable(plan.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "仅草稿或退回状态可提交");
        }
        plan.setStatus(PurchaseConst.PLAN_PENDING);
        plan.setSubmitTime(LocalDateTime.now());
        updateById(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurchasePlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "采购计划不存在");
        }
        if (!isEditable(plan.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "仅草稿或退回状态可删除");
        }
        itemMapper.delete(Wrappers.<PurchasePlanItem>lambdaQuery().eq(PurchasePlanItem::getPlanId, id));
        removeById(id);
    }

    @Override
    public PurchasePlanVO getDetail(Long id) {
        PurchasePlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "采购计划不存在");
        }
        return toVO(plan, true);
    }

    @Override
    public PageResult<PurchasePlanVO> page(String status, Long supplierId, long current, long size) {
        List<Long> planIds = null;
        if (supplierId != null) {
            planIds = itemMapper.selectObjs(Wrappers.<PurchasePlanItem>query()
                            .select("DISTINCT plan_id").eq("supplier_id", supplierId))
                    .stream().filter(java.util.Objects::nonNull).map(o -> Long.valueOf(o.toString())).toList();
            if (planIds.isEmpty()) {
                return PageResult.of(new Page<>(current, size, 0));
            }
        }
        IPage<PurchasePlan> p = page(new Page<>(current, size),
                Wrappers.<PurchasePlan>lambdaQuery()
                        .eq(StringUtils.hasText(status), PurchasePlan::getStatus, status)
                        .in(planIds != null, PurchasePlan::getId, planIds == null ? List.of() : planIds)
                        .orderByDesc(PurchasePlan::getCreateTime));
        Page<PurchasePlanVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(pl -> toVO(pl, false)).toList());
        return PageResult.of(voPage);
    }

    // --- 内部 ---

    /** 校验并构建明细快照（不含 planId）。 */
    private List<PurchasePlanItem> buildItems(PurchasePlanSaveDTO dto) {
        List<PurchasePlanItem> items = new ArrayList<>();
        for (PurchasePlanSaveDTO.Item in : dto.getItems()) {
            int qty = in.getPurchaseQty() == null ? 0 : in.getPurchaseQty();
            if (qty <= 0) {
                continue; // qty=0 表示不采购，跳过该行
            }
            SupplierProduct sp = supplierProductMapper.selectById(in.getSupplierProductId());
            if (sp == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "供应商产品不存在");
            }
            int moq = sp.getMinPurchaseQty() == null ? 1 : sp.getMinPurchaseQty();
            if (qty < moq) {
                throw new BusinessException(ResultCode.BELOW_MIN_PURCHASE_QTY,
                        "产品[" + sp.getProductCode() + "] 采购数量 " + qty + " 低于最小采购量 " + moq);
            }
            PurchasePlanItem item = new PurchasePlanItem();
            item.setSupplierId(sp.getSupplierId());
            item.setSupplierProductId(sp.getId());
            item.setBrandId(sp.getBrandId());
            item.setCategoryId(sp.getCategoryId());
            item.setProductName(sp.getName());
            item.setProductCode(sp.getProductCode());
            item.setWholesalePrice(sp.getWholesalePrice() == null ? BigDecimal.ZERO : sp.getWholesalePrice());
            item.setMinPurchaseQty(moq);
            item.setPurchaseQty(qty);
            item.setAmount(item.getWholesalePrice().multiply(BigDecimal.valueOf(qty)));
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "至少一条明细采购数量需大于 0");
        }
        return items;
    }

    private void applyTotals(PurchasePlan plan, List<PurchasePlanItem> items) {
        int totalQty = items.stream().mapToInt(PurchasePlanItem::getPurchaseQty).sum();
        BigDecimal totalAmount = items.stream().map(PurchasePlanItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        plan.setTotalQty(totalQty);
        plan.setTotalAmount(totalAmount);
    }

    private boolean isEditable(String status) {
        return Set.of(PurchaseConst.PLAN_DRAFT, PurchaseConst.PLAN_REJECTED).contains(status);
    }

    private PurchasePlanVO toVO(PurchasePlan plan, boolean withItems) {
        PurchasePlanVO vo = new PurchasePlanVO();
        BeanUtils.copyProperties(plan, vo);
        if (withItems) {
            List<PurchasePlanItem> items = itemMapper.selectList(
                    Wrappers.<PurchasePlanItem>lambdaQuery().eq(PurchasePlanItem::getPlanId, plan.getId()));
            vo.setItems(items.stream().map(it -> {
                PurchasePlanItemVO iv = new PurchasePlanItemVO();
                BeanUtils.copyProperties(it, iv);
                return iv;
            }).toList());
        }
        return vo;
    }
}
