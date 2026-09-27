package africa.zokomart.admin.module.sales.service.impl;

import africa.zokomart.admin.common.base.BizNo;
import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.sales.constant.SalesConst;
import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.dto.SalesOrderUpdateDTO;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.entity.SalesOrderItem;
import africa.zokomart.admin.module.sales.mapper.SalesOrderItemMapper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.sales.service.SalesOrderService;
import africa.zokomart.admin.module.sales.vo.SalesOrderItemVO;
import africa.zokomart.admin.module.sales.vo.SalesOrderLabelItemVO;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder>
        implements SalesOrderService {

    private final SalesOrderItemMapper itemMapper;
    private final SupplierProductMapper supplierProductMapper;
    private final InventoryStockService stockService;
    private final africa.zokomart.admin.module.sales.mapper.OrderableProductMapper orderableProductMapper;

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
            item.setAmount(in.getAmount() != null
                    ? in.getAmount()
                    : unitPrice.multiply(BigDecimal.valueOf(qty)));
            item.setExternalOrderId(in.getExternalOrderId());
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
        order.setCity(dto.getCity());
        // orderDate 非空 = 导入历史订单：create_time 一并归到业务日期，
        // 使仪表盘/列表（均以 create_time 为口径）按订单实际发生日统计。
        // 这是对「审计字段不手动 set」约定的一处刻意例外，范围仅限本分支；
        // update_time 仍由自动填充写入真实时刻，导入时间不会丢失。
        if (dto.getOrderDate() != null) {
            order.setOrderDate(dto.getOrderDate());
            order.setCreateTime(dto.getOrderDate().atStartOfDay());
        } else {
            order.setOrderDate(LocalDate.now());
        }
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
                        .orderByDesc(SalesOrder::getCreateTime)
                        .orderByDesc(SalesOrder::getId));
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
        // 按业务日期 order_date 过滤，而不是 create_time 范围：手工下单二者当天一致，
        // 但导入订单的 create_time 被刻意回填成 orderDate（见 create() 注释），
        // 直接查 order_date 才是语义正确的口径，且有 idx_sales_order_date 支撑，
        // 不依赖 create_time 回填这个“刻意例外”才能定位到历史订单。
        List<SalesOrder> orders = list(Wrappers.<SalesOrder>lambdaQuery()
                .eq(salespersonId != null, SalesOrder::getSalespersonId, salespersonId)
                .eq(status != null && !status.isBlank(), SalesOrder::getStatus, status)
                .eq(SalesOrder::getOrderDate, day)
                .orderByAsc(SalesOrder::getCreateTime));
        if (orders.isEmpty()) {
            return List.of();
        }
        // 明细一次性按 order_id IN (...) 批量查出再分组，不逐单查（面单一次可能几十单，避免 N+1）。
        // 按 id 升序 = 下单顺序，前端据此逐件展开贴纸，贴纸顺序才和实物对得上。
        Map<Long, List<SalesOrderLabelItemVO>> itemsByOrder = itemMapper.selectList(
                        Wrappers.<SalesOrderItem>lambdaQuery()
                                .in(SalesOrderItem::getOrderId, orders.stream().map(SalesOrder::getId).toList())
                                .orderByAsc(SalesOrderItem::getId))
                .stream()
                .collect(Collectors.groupingBy(SalesOrderItem::getOrderId,
                        Collectors.mapping(it -> {
                            SalesOrderLabelItemVO iv = new SalesOrderLabelItemVO();
                            iv.setProductCode(it.getProductCode());
                            iv.setProductName(it.getProductName());
                            iv.setQty(it.getQty());
                            return iv;
                        }, Collectors.toList())));
        return orders.stream().map(o -> {
            SalesOrderLabelVO vo = new SalesOrderLabelVO();
            BeanUtils.copyProperties(o, vo);
            vo.setItems(itemsByOrder.getOrDefault(o.getId(), List.of()));
            return vo;
        }).toList();
    }

    @Override
    public PageResult<africa.zokomart.admin.module.sales.vo.OrderableProductVO> orderableProducts(
            Long supplierId, Long brandId, Long categoryId, String keyword, long current, long size) {
        Page<africa.zokomart.admin.module.sales.vo.OrderableProductVO> page = new Page<>(current, size);
        return PageResult.of(orderableProductMapper.pageOrderable(page, supplierId, brandId, categoryId, keyword));
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Long salespersonId, SalesOrderUpdateDTO dto) {
        SalesOrder order = requireOrder(id, salespersonId);
        order.setCustomerName(dto.getCustomerName().trim());
        order.setCustomerPhone(dto.getCustomerPhone().trim());
        order.setCustomerAddress(dto.getCustomerAddress().trim());
        if (dto.getOrderDate() != null && !dto.getOrderDate().equals(order.getOrderDate())) {
            // 列表/仪表盘以 create_time 为口径：改业务日期时 create_time 跟着挪到新日期（保留时分秒），
            // 维持「create_time 的日期 = order_date」这一不变量（同 create() 导入分支的刻意例外）。
            LocalTime timeOfDay = order.getCreateTime() != null
                    ? order.getCreateTime().toLocalTime() : LocalTime.MIDNIGHT;
            order.setOrderDate(dto.getOrderDate());
            order.setCreateTime(dto.getOrderDate().atTime(timeOfDay));
        }
        if (dto.getItems() != null) {
            replaceItems(order, dto.getItems());
        }
        // city / remark 允许清空：实体 updateById 会跳过 null 字段，故改由 wrapper 显式 set
        String city = dto.getCity() == null || dto.getCity().isBlank() ? null : dto.getCity().trim();
        String remark = dto.getRemark() == null || dto.getRemark().isBlank() ? null : dto.getRemark();
        order.setCity(null);
        order.setRemark(null);
        // 实体带 version → 乐观锁拦截器追加 version 条件；并发修改时影响行数为 0
        boolean ok = update(order, Wrappers.<SalesOrder>lambdaUpdate()
                .eq(SalesOrder::getId, order.getId())
                .set(SalesOrder::getCity, city)
                .set(SalesOrder::getRemark, remark));
        if (!ok) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单已被他人修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long salespersonId) {
        SalesOrder order = requireOrder(id, salespersonId);
        if (!isPendingDispatch(order)) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "仅待派送订单可删除");
        }
        // 先按乐观锁更新一次：锁住该行，并挡住与派送等操作的并发（被抢先改过则 version 不符）
        if (!updateById(order)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单已被他人修改，请刷新后重试");
        }
        List<SalesOrderItem> items = itemMapper.selectList(
                Wrappers.<SalesOrderItem>lambdaQuery().eq(SalesOrderItem::getOrderId, id));
        // 待派送订单不存在拒收，整行数量全部回补
        for (SalesOrderItem item : items) {
            stockService.changeStock(item.getSupplierProductId(), item.getQty(),
                    InventoryConst.TYPE_SALES_CANCEL, InventoryConst.REF_SALES_ORDER,
                    order.getId(), order.getOrderNo(), "订单删除回补", true);
        }
        itemMapper.delete(Wrappers.<SalesOrderItem>lambdaQuery().eq(SalesOrderItem::getOrderId, id));
        removeById(id);
    }

    /** 待派送且未完成：明细可改、订单可删的唯一状态。 */
    private boolean isPendingDispatch(SalesOrder order) {
        return SalesConst.PENDING_DISPATCH.equals(order.getStatus())
                && (order.getCompleted() == null || order.getCompleted() == 0);
    }

    /**
     * 整体替换明细（仅未派送订单）：按 id 匹配就地更新、无 id 新增、未出现的旧行逻辑删除；
     * 库存按「每个产品的数量净变化」一次性调整，而非先全退再全扣，流水更干净。
     */
    private void replaceItems(SalesOrder order, List<SalesOrderUpdateDTO.Item> inputs) {
        if (!isPendingDispatch(order)) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "仅未派送订单可修改商品明细");
        }
        Map<Long, SalesOrderItem> oldById = itemMapper.selectList(
                        Wrappers.<SalesOrderItem>lambdaQuery().eq(SalesOrderItem::getOrderId, order.getId()))
                .stream().collect(Collectors.toMap(SalesOrderItem::getId, it -> it));
        // 产品 -> 库存变化量：旧行 +qty（退回），新行 -qty（出库）
        Map<Long, Integer> stockDelta = new LinkedHashMap<>();
        oldById.values().forEach(it -> stockDelta.merge(it.getSupplierProductId(), it.getQty(), Integer::sum));

        Set<Long> keptIds = new HashSet<>();
        List<SalesOrderItem> result = new ArrayList<>();
        for (SalesOrderUpdateDTO.Item in : inputs) {
            SalesOrderItem item;
            if (in.getId() != null) {
                item = oldById.get(in.getId());
                if (item == null || !keptIds.add(in.getId())) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "明细不属于该订单");
                }
            } else {
                item = new SalesOrderItem();
                item.setOrderId(order.getId());
                item.setRejectQty(0);
            }
            if (!in.getSupplierProductId().equals(item.getSupplierProductId())) {
                SupplierProduct sp = supplierProductMapper.selectById(in.getSupplierProductId());
                if (sp == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "供应商产品不存在");
                }
                item.setSupplierProductId(sp.getId());
                item.setProductName(sp.getName());
                item.setProductCode(sp.getProductCode());
            }
            item.setQty(in.getQty());
            item.setUnitPrice(in.getUnitPrice());
            item.setAmount(in.getAmount() != null
                    ? in.getAmount()
                    : in.getUnitPrice().multiply(BigDecimal.valueOf(in.getQty())));
            if (item.getId() != null) {
                itemMapper.updateById(item);
            } else {
                itemMapper.insert(item);
            }
            stockDelta.merge(item.getSupplierProductId(), -item.getQty(), Integer::sum);
            result.add(item);
        }
        oldById.keySet().stream().filter(oid -> !keptIds.contains(oid)).forEach(itemMapper::deleteById);

        stockDelta.forEach((productId, delta) -> {
            if (delta != 0) {
                stockService.changeStock(productId, delta,
                        InventoryConst.TYPE_SALES_EDIT, InventoryConst.REF_SALES_ORDER,
                        order.getId(), order.getOrderNo(), "订单修改", true);
            }
        });
        order.setTotalQty(result.stream().mapToInt(SalesOrderItem::getQty).sum());
        order.setTotalAmount(result.stream().map(SalesOrderItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** 取订单；salespersonId 非 null 时仅限本人订单，否则按 NOT_FOUND 处理避免 id 枚举。 */
    private SalesOrder requireOrder(Long id, Long salespersonId) {
        SalesOrder order = getById(id);
        if (order == null || (salespersonId != null && !salespersonId.equals(order.getSalespersonId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "销售订单不存在");
        }
        return order;
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
