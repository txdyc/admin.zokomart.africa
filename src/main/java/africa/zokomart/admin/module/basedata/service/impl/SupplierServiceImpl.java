package africa.zokomart.admin.module.basedata.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.entity.Supplier;
import africa.zokomart.admin.module.basedata.mapper.SupplierMapper;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.basedata.vo.SupplierVO;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductService;
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
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    private final SupplierProductService supplierProductService;

    @Override
    public Long createSupplier(SupplierSaveDTO dto) {
        if (existsName(dto.getName(), null)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "供应商名已存在");
        }
        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier, "id");
        if (supplier.getStatus() == null) {
            supplier.setStatus(1);
        }
        save(supplier);
        return supplier.getId();
    }

    @Override
    public void updateSupplier(SupplierSaveDTO dto) {
        Supplier exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        if (existsName(dto.getName(), dto.getId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "供应商名已存在");
        }
        BeanUtils.copyProperties(dto, exist, "id");
        updateById(exist);
    }

    @Override
    public void deleteSupplier(Long id) {
        if (supplierProductService.existsBySupplierId(id)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该供应商已有产品，不能删除");
        }
        removeById(id);
    }

    @Override
    public PageResult<SupplierVO> pageSuppliers(String keyword, Integer status, long current, long size) {
        IPage<Supplier> page = page(new Page<>(current, size),
                Wrappers.<Supplier>lambdaQuery()
                        .like(StringUtils.hasText(keyword), Supplier::getName, keyword)
                        .eq(status != null, Supplier::getStatus, status)
                        .orderByDesc(Supplier::getCreateTime));
        Page<SupplierVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return PageResult.of(voPage);
    }

    @Override
    public SupplierVO getDetail(Long id) {
        Supplier supplier = getById(id);
        if (supplier == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        return toVO(supplier);
    }

    private SupplierVO toVO(Supplier supplier) {
        SupplierVO vo = new SupplierVO();
        BeanUtils.copyProperties(supplier, vo);
        return vo;
    }

    private boolean existsName(String name, Long excludeId) {
        return exists(Wrappers.<Supplier>lambdaQuery()
                .eq(Supplier::getName, name)
                .ne(excludeId != null, Supplier::getId, excludeId));
    }
}
