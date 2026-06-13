package africa.zokomart.admin.module.purchase.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.purchase.entity.ActualPurchaseOrder;
import africa.zokomart.admin.module.purchase.entity.ActualPurchaseOrderItem;
import africa.zokomart.admin.module.purchase.mapper.ActualPurchaseOrderItemMapper;
import africa.zokomart.admin.module.purchase.mapper.ActualPurchaseOrderMapper;
import africa.zokomart.admin.module.purchase.service.ActualPurchaseOrderService;
import africa.zokomart.admin.module.purchase.vo.ActualPurchaseOrderItemVO;
import africa.zokomart.admin.module.purchase.vo.ActualPurchaseOrderVO;
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
public class ActualPurchaseOrderServiceImpl extends ServiceImpl<ActualPurchaseOrderMapper, ActualPurchaseOrder>
        implements ActualPurchaseOrderService {

    private final ActualPurchaseOrderItemMapper itemMapper;

    @Override
    public PageResult<ActualPurchaseOrderVO> page(Long purchaseOrderId, String status, long current, long size) {
        IPage<ActualPurchaseOrder> p = page(new Page<>(current, size),
                Wrappers.<ActualPurchaseOrder>lambdaQuery()
                        .eq(purchaseOrderId != null, ActualPurchaseOrder::getPurchaseOrderId, purchaseOrderId)
                        .eq(StringUtils.hasText(status), ActualPurchaseOrder::getStatus, status)
                        .orderByDesc(ActualPurchaseOrder::getCreateTime));
        Page<ActualPurchaseOrderVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(o -> toVO(o, false)).toList());
        return PageResult.of(voPage);
    }

    @Override
    public ActualPurchaseOrderVO getDetail(Long id) {
        ActualPurchaseOrder order = getById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实际采购单不存在");
        }
        return toVO(order, true);
    }

    private ActualPurchaseOrderVO toVO(ActualPurchaseOrder order, boolean withItems) {
        ActualPurchaseOrderVO vo = new ActualPurchaseOrderVO();
        BeanUtils.copyProperties(order, vo);
        if (withItems) {
            vo.setItems(itemMapper.selectList(Wrappers.<ActualPurchaseOrderItem>lambdaQuery()
                            .eq(ActualPurchaseOrderItem::getActualOrderId, order.getId()))
                    .stream().map(it -> {
                        ActualPurchaseOrderItemVO iv = new ActualPurchaseOrderItemVO();
                        BeanUtils.copyProperties(it, iv);
                        return iv;
                    }).toList());
        }
        return vo;
    }
}
