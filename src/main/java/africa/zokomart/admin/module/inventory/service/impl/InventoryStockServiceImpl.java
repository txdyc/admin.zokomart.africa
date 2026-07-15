package africa.zokomart.admin.module.inventory.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.entity.Brand;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.entity.Supplier;
import africa.zokomart.admin.module.basedata.mapper.BrandMapper;
import africa.zokomart.admin.module.basedata.mapper.CategoryMapper;
import africa.zokomart.admin.module.basedata.mapper.SupplierMapper;
import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.entity.InventoryStock;
import africa.zokomart.admin.module.inventory.entity.InventoryTransaction;
import africa.zokomart.admin.module.inventory.mapper.InventoryStockMapper;
import africa.zokomart.admin.module.inventory.mapper.InventoryTransactionMapper;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.inventory.vo.InventoryStockVO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryStockServiceImpl extends ServiceImpl<InventoryStockMapper, InventoryStock>
        implements InventoryStockService {

    private static final int MAX_RETRY = 3;

    private final InventoryTransactionMapper txMapper;
    private final SupplierProductMapper supplierProductMapper;
    private final SupplierMapper supplierMapper;
    private final BrandMapper brandMapper;
    private final CategoryMapper categoryMapper;

    @Override
    public int getQty(Long supplierProductId) {
        InventoryStock s = findStock(supplierProductId);
        return s == null ? 0 : s.getQuantity();
    }

    @Override
    public PageResult<InventoryStockVO> pageStocks(Long supplierId, Long brandId, Long categoryId,
                                                   String keyword, long current, long size) {
        // keyword 命中供应商产品的 name/product_code，取 id 集合过滤库存
        List<Long> matchedProductIds = null;
        if (StringUtils.hasText(keyword)) {
            matchedProductIds = supplierProductMapper.selectList(
                            Wrappers.<SupplierProduct>lambdaQuery()
                                    .like(SupplierProduct::getName, keyword)
                                    .or().like(SupplierProduct::getProductCode, keyword))
                    .stream().map(SupplierProduct::getId).toList();
            if (matchedProductIds.isEmpty()) {
                Page<InventoryStockVO> empty = new Page<>(current, size, 0);
                empty.setRecords(List.of());
                return PageResult.of(empty);
            }
        }
        final List<Long> finalMatchedIds = matchedProductIds;
        IPage<InventoryStock> page = page(new Page<>(current, size),
                Wrappers.<InventoryStock>lambdaQuery()
                        .eq(supplierId != null, InventoryStock::getSupplierId, supplierId)
                        .eq(brandId != null, InventoryStock::getBrandId, brandId)
                        .eq(categoryId != null, InventoryStock::getCategoryId, categoryId)
                        .in(finalMatchedIds != null, InventoryStock::getSupplierProductId, finalMatchedIds)
                        .orderByDesc(InventoryStock::getUpdateTime));

        List<InventoryStock> records = page.getRecords();
        // 批量取名，避免 N+1
        Map<Long, SupplierProduct> productMap = batchLoad(supplierProductMapper::selectBatchIds,
                records.stream().map(InventoryStock::getSupplierProductId), SupplierProduct::getId);
        Map<Long, Supplier> supplierMap = batchLoad(supplierMapper::selectBatchIds,
                records.stream().map(InventoryStock::getSupplierId), Supplier::getId);
        Map<Long, Brand> brandMap = batchLoad(brandMapper::selectBatchIds,
                records.stream().map(InventoryStock::getBrandId), Brand::getId);
        Map<Long, Category> categoryMap = batchLoad(categoryMapper::selectBatchIds,
                records.stream().map(InventoryStock::getCategoryId), Category::getId);

        Page<InventoryStockVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records.stream().map(s -> {
            InventoryStockVO vo = new InventoryStockVO();
            vo.setId(s.getId());
            vo.setSupplierProductId(s.getSupplierProductId());
            vo.setSupplierId(s.getSupplierId());
            vo.setBrandId(s.getBrandId());
            vo.setCategoryId(s.getCategoryId());
            vo.setQuantity(s.getQuantity());
            vo.setUpdateTime(s.getUpdateTime());
            SupplierProduct sp = productMap.get(s.getSupplierProductId());
            if (sp != null) {
                vo.setProductName(sp.getName());
                vo.setProductCode(sp.getProductCode());
            }
            Supplier sup = supplierMap.get(s.getSupplierId());
            if (sup != null) {
                vo.setSupplierName(sup.getName());
            }
            Brand b = brandMap.get(s.getBrandId());
            if (b != null) {
                vo.setBrandName(b.getName());
            }
            Category c = categoryMap.get(s.getCategoryId());
            if (c != null) {
                vo.setCategoryName(c.getName());
            }
            return vo;
        }).toList());
        return PageResult.of(voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long supplierProductId, Integer targetQuantity, String remark) {
        if (targetQuantity == null || targetQuantity < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "库存数量不能为负");
        }
        int current = getQty(supplierProductId);
        int delta = targetQuantity - current;
        changeStock(supplierProductId, delta, InventoryConst.TYPE_MANUAL_ADJUST,
                InventoryConst.REF_MANUAL, null, null, remark);
    }

    /** 按 id 列表批量查询并建 id->实体 映射；忽略 null id。 */
    private <T> Map<Long, T> batchLoad(Function<List<Long>, List<T>> loader,
                                       java.util.stream.Stream<Long> ids, Function<T, Long> keyFn) {
        List<Long> idList = ids.filter(Objects::nonNull).distinct().toList();
        if (idList.isEmpty()) {
            return new java.util.HashMap<>(); // 可空键安全：记录的外键可能为 null
        }
        return loader.apply(idList).stream().collect(Collectors.toMap(keyFn, Function.identity()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStock(Long supplierProductId, int qtyChange, String type,
                            String refType, Long refId, String refNo, String remark) {
        changeStock(supplierProductId, qtyChange, type, refType, refId, refNo, remark, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStock(Long supplierProductId, int qtyChange, String type,
                            String refType, Long refId, String refNo, String remark, boolean allowNegative) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            InventoryStock stock = findStock(supplierProductId);
            int before;
            int after;
            boolean ok;
            if (stock == null) {
                before = 0;
                after = qtyChange;
                if (after < 0 && !allowNegative) {
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "库存不足");
                }
                ok = createStock(supplierProductId, after);
            } else {
                before = stock.getQuantity();
                after = before + qtyChange;
                if (after < 0 && !allowNegative) {
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "库存不足");
                }
                stock.setQuantity(after);
                // MP 乐观锁：version 不匹配则 update 影响行数为 0
                ok = updateById(stock);
            }
            if (ok) {
                writeTx(supplierProductId, qtyChange, before, after, type, refType, refId, refNo, remark);
                return;
            }
        }
        throw new BusinessException(ResultCode.BUSINESS_ERROR, "库存更新冲突，请重试");
    }

    private InventoryStock findStock(Long supplierProductId) {
        return getOne(Wrappers.<InventoryStock>lambdaQuery()
                .eq(InventoryStock::getSupplierProductId, supplierProductId), false);
    }

    /** 新建库存记录，冗余 supplier/brand/category 取自供应商产品。唯一键冲突（并发首建）返回 false 以触发重试。 */
    private boolean createStock(Long supplierProductId, int quantity) {
        SupplierProduct sp = supplierProductMapper.selectById(supplierProductId);
        InventoryStock stock = new InventoryStock();
        stock.setSupplierProductId(supplierProductId);
        if (sp != null) {
            stock.setSupplierId(sp.getSupplierId());
            stock.setBrandId(sp.getBrandId());
            stock.setCategoryId(sp.getCategoryId());
        }
        stock.setQuantity(quantity);
        try {
            return save(stock);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false; // 并发下已被他人创建，重试走更新分支
        }
    }

    private void writeTx(Long supplierProductId, int qtyChange, int before, int after,
                         String type, String refType, Long refId, String refNo, String remark) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setSupplierProductId(supplierProductId);
        tx.setType(type);
        tx.setQtyChange(qtyChange);
        tx.setBeforeQty(before);
        tx.setAfterQty(after);
        tx.setRefType(refType);
        tx.setRefId(refId);
        tx.setRefNo(refNo);
        tx.setOperatorId(currentUserIdOrNull());
        tx.setRemark(remark);
        tx.setCreateTime(LocalDateTime.now());
        txMapper.insert(tx);
    }

    private Long currentUserIdOrNull() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
