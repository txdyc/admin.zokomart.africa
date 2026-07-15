package africa.zokomart.admin.module.sales.service.impl;

import africa.zokomart.admin.common.base.BizNo;
import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.sales.constant.SalesConst;
import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.entity.SalesOrderItem;
import africa.zokomart.admin.module.sales.mapper.SalesOrderItemMapper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.sales.service.SalesOrderService;
import africa.zokomart.admin.module.sales.vo.SalesOrderItemVO;
import africa.zokomart.admin.module.sales.vo.SalesOrderLabelVO;
import africa.zokomart.admin.module.sales.vo.SalesOrderVO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder>
        implements SalesOrderService {

    private final SalesOrderItemMapper itemMapper;
    private final SupplierProductMapper supplierProductMapper;
    private final InventoryStockService stockService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SalesOrderCreateDTO dto) {
        List<SalesOrderItem> items = new ArrayList<>();
        for (SalesOrderCreateDTO.Item in : dto.getItems()) {
            SupplierProduct sp = supplierProductMapper.selectById(in.getSupplierProductId());
            if (sp == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "供应商产品不存在");
            }
            int qty = in.getQty();
            BigDecimal unitPrice = in.getUnitPrice() != null ? in.getUnitPrice()
                    : (sp.getRetailPrice() != null ? sp.getRetailPrice() : BigDecimal.ZERO);
            SalesOrderItem item = new SalesOrderItem();
            item.setSupplierProductId(sp.getId());
            item.setProductName(sp.getName());
            item.setProductCode(sp.getProductCode());
            item.setUnitPrice(unitPrice);
            item.setQty(qty);
            item.setRejectQty(0);
            item.setAmount(unitPrice.multiply(BigDecimal.valueOf(qty)));
            items.add(item);
        }

        SalesOrder order = new SalesOrder();
        order.setOrderNo(BizNo.gen(SalesConst.NO_SALES));
        order.setStatus(SalesConst.PENDING_DISPATCH);
        order.setCustomerName(dto.getCustomerName());
        order.setCustomerPhone(dto.getCustomerPhone());
        order.setCustomerAddress(dto.getCustomerAddress());
        order.setSalespersonId(currentUserIdOrNull());
        order.setRemark(dto.getRemark());
        order.setCompleted(0);
        order.setTotalQty(items.stream().mapToInt(SalesOrderItem::getQty).sum());
        order.setTotalAmount(items.stream().map(SalesOrderItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        save(order);

        for (SalesOrderItem item : items) {
            item.setOrderId(order.getId());
            itemMapper.insert(item);
            // 扣减库存 + SALES_OUT 流水（乐观锁防超卖；allowNegative=true 允许缺货欠货）
            stockService.changeStock(item.getSupplierProductId(), -item.getQty(),
                    InventoryConst.TYPE_SALES_OUT, InventoryConst.REF_SALES_ORDER,
                    order.getId(), order.getOrderNo(), "销售出库", true);
        }
        return order.getId();
    }

    @Override
    public PageResult<SalesOrderVO> page(Long salespersonId, Boolean completed, long current, long size) {
        IPage<SalesOrder> p = page(new Page<>(current, size),
                Wrappers.<SalesOrder>lambdaQuery()
                        .eq(salespersonId != null, SalesOrder::getSalespersonId, salespersonId)
                        .eq(completed != null, SalesOrder::getCompleted, completed != null && completed ? 1 : 0)
                        .orderByDesc(SalesOrder::getCreateTime));
        Page<SalesOrderVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(o -> toVO(o, false)).toList());
        return PageResult.of(voPage);
    }

    @Override
    public SalesOrderVO getDetail(Long id) {
        SalesOrder order = getById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "销售订单不存在");
        }
        return toVO(order, true);
    }

    @Override
    public List<SalesOrderLabelVO> labels(Long salespersonId, String status, LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now();
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        List<SalesOrder> orders = list(Wrappers.<SalesOrder>lambdaQuery()
                .eq(salespersonId != null, SalesOrder::getSalespersonId, salespersonId)
                .eq(status != null && !status.isBlank(), SalesOrder::getStatus, status)
                .ge(SalesOrder::getCreateTime, start)
                .lt(SalesOrder::getCreateTime, end)
                .orderByAsc(SalesOrder::getCreateTime));
        return orders.stream().map(o -> {
            SalesOrderLabelVO vo = new SalesOrderLabelVO();
            BeanUtils.copyProperties(o, vo);
            return vo;
        }).toList();
    }

    private SalesOrderVO toVO(SalesOrder order, boolean withItems) {
        SalesOrderVO vo = new SalesOrderVO();
        BeanUtils.copyProperties(order, vo);
        if (withItems) {
            vo.setItems(itemMapper.selectList(Wrappers.<SalesOrderItem>lambdaQuery()
                            .eq(SalesOrderItem::getOrderId, order.getId()))
                    .stream().map(it -> {
                        SalesOrderItemVO iv = new SalesOrderItemVO();
                        BeanUtils.copyProperties(it, iv);
                        return iv;
                    }).toList());
        }
        return vo;
    }

    private Long currentUserIdOrNull() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
