package africa.zokomart.admin.module.raworder.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.raworder.constant.RawOrderStatus;
import africa.zokomart.admin.module.raworder.dto.RawOrderUpdateDTO;
import africa.zokomart.admin.module.raworder.entity.RawOrder;
import africa.zokomart.admin.module.raworder.mapper.RawOrderMapper;
import africa.zokomart.admin.module.raworder.service.RawOrderService;
import africa.zokomart.admin.module.raworder.vo.RawOrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RawOrderServiceImpl implements RawOrderService {

    private final RawOrderMapper rawOrderMapper;

    @Override
    public PageResult<RawOrderVO> page(LocalDate dateStart, LocalDate dateEnd, String status,
                                       String brand, String keyword, long current, long size) {
        LambdaQueryWrapper<RawOrder> qw = new LambdaQueryWrapper<RawOrder>()
                .ge(dateStart != null, RawOrder::getOrderDate, dateStart)
                .le(dateEnd != null, RawOrder::getOrderDate, dateEnd)
                .eq(StringUtils.hasText(status), RawOrder::getStatus, status)
                .like(StringUtils.hasText(brand), RawOrder::getBrand,
                        brand == null ? null : brand.trim());
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            qw.and(w -> w.like(RawOrder::getCustomerName, k).or().like(RawOrder::getTelephone, k));
        }
        qw.orderByDesc(RawOrder::getOrderDate).orderByDesc(RawOrder::getId);
        IPage<RawOrderVO> page = rawOrderMapper.selectPage(new Page<>(current, size), qw)
                .convert(this::toVo);
        return PageResult.of(page);
    }

    private RawOrderVO toVo(RawOrder o) {
        RawOrderVO vo = new RawOrderVO();
        BeanUtils.copyProperties(o, vo);
        return vo;
    }

    @Override
    public void update(Long id, RawOrderUpdateDTO dto) {
        RawOrder existing = rawOrderMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原始订单不存在");
        }
        if (!RawOrderStatus.ALL.contains(dto.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "status 非法: " + dto.getStatus());
        }
        // DTO 与实体同名字段全量覆盖；id/审计/version 不在 DTO 上，保持原值（乐观锁沿用查出的 version）
        BeanUtils.copyProperties(dto, existing);
        rawOrderMapper.updateById(existing);
    }
}
