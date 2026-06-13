package africa.zokomart.admin.module.purchase.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrder;
import africa.zokomart.admin.module.purchase.entity.PurchaseOrderItem;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderItemMapper;
import africa.zokomart.admin.module.purchase.mapper.PurchaseOrderMapper;
import africa.zokomart.admin.module.purchase.service.PurchaseOrderService;
import africa.zokomart.admin.module.purchase.vo.PurchaseOrderItemVO;
import africa.zokomart.admin.module.purchase.vo.PurchaseOrderVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder>
        implements PurchaseOrderService {

    private final PurchaseOrderItemMapper itemMapper;

    @Override
    public PageResult<PurchaseOrderVO> page(Long planId, Long supplierId, String status, long current, long size) {
        IPage<PurchaseOrder> p = page(new Page<>(current, size),
                Wrappers.<PurchaseOrder>lambdaQuery()
                        .eq(planId != null, PurchaseOrder::getPlanId, planId)
                        .eq(supplierId != null, PurchaseOrder::getSupplierId, supplierId)
                        .eq(StringUtils.hasText(status), PurchaseOrder::getStatus, status)
                        .orderByDesc(PurchaseOrder::getCreateTime));
        Page<PurchaseOrderVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(o -> toVO(o, false)).toList());
        return PageResult.of(voPage);
    }

    @Override
    public PurchaseOrderVO getDetail(Long id) {
        PurchaseOrder order = getById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        return toVO(order, true);
    }

    private PurchaseOrderVO toVO(PurchaseOrder order, boolean withItems) {
        PurchaseOrderVO vo = new PurchaseOrderVO();
        BeanUtils.copyProperties(order, vo);
        if (withItems) {
            vo.setItems(itemMapper.selectList(Wrappers.<PurchaseOrderItem>lambdaQuery()
                            .eq(PurchaseOrderItem::getOrderId, order.getId()))
                    .stream().map(it -> {
                        PurchaseOrderItemVO iv = new PurchaseOrderItemVO();
                        BeanUtils.copyProperties(it, iv);
                        return iv;
                    }).toList());
        }
        return vo;
    }
}
