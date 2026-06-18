package africa.zokomart.admin.module.wcsync.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.entity.Brand;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.service.BrandService;
import africa.zokomart.admin.module.basedata.service.CategoryService;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.config.WcSyncProperties;
import africa.zokomart.admin.module.wcsync.entity.WcSyncRecord;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncRecordMapper;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncResultVO;
import africa.zokomart.admin.module.wcsync.vo.WcSyncRowError;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WcSyncServiceImpl implements WcSyncService {

    private final WooCommerceClient wc;
    private final WcSyncProperties props;
    private final SupplierService supplierService;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final SupplierProductMapper supplierProductMapper;
    private final WcSyncRecordMapper recordMapper;

    @Override
    public WcSyncResultVO syncSupplierBrands(Long supplierId, List<Long> brandIds) {
        if (!wc.configured()) {
            throw new BusinessException(ResultCode.WC_NOT_CONFIGURED);
        }
        if (supplierService.getById(supplierId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        List<SupplierProduct> products = supplierProductMapper.selectList(
                Wrappers.<SupplierProduct>lambdaQuery()
                        .eq(SupplierProduct::getSupplierId, supplierId)
                        .in(SupplierProduct::getBrandId, brandIds));

        WcSyncResultVO result = new WcSyncResultVO();
        result.setTotal(products.size());
        Map<Long, Long> categoryCache = new HashMap<>();
        Map<Long, Long> brandCache = new HashMap<>();

        for (SupplierProduct p : products) {
            try {
                boolean enabled = p.getStatus() != null && p.getStatus() == 1;
                WcSyncRecord record = recordMapper.selectById(p.getId());
                Long wcId = record != null ? record.getWcProductId() : null;
                if (wcId == null) {
                    wcId = wc.findProductIdBySku(p.getProductCode());
                }
                if (wcId == null && !enabled) {
                    result.setSkipped(result.getSkipped() + 1); // 从未同步且停用 → 跳过
                    continue;
                }
                long wcCategoryId = resolveWcCategory(p.getCategoryId(), categoryCache);
                long wcBrandId = resolveWcBrand(p.getBrandId(), brandCache);
                WcProduct wcProduct = build(p, wcCategoryId, wcBrandId, enabled);

                String outcome;
                if (wcId == null) {
                    wcId = wc.createProduct(wcProduct);
                    outcome = "CREATED";
                    result.setCreated(result.getCreated() + 1);
                } else {
                    wc.updateProduct(wcId, wcProduct);
                    if (enabled) {
                        outcome = "UPDATED";
                        result.setUpdated(result.getUpdated() + 1);
                    } else {
                        outcome = "DRAFTED";
                        result.setDrafted(result.getDrafted() + 1);
                    }
                }
                saveRecord(p.getId(), wcId, p.getProductCode(), outcome, null);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add(new WcSyncRowError(p.getId(), p.getProductCode(), e.getMessage()));
                saveRecord(p.getId(), null, p.getProductCode(), "FAILED", e.getMessage());
            }
        }
        return result;
    }

    private String brandName(Long brandId) {
        Brand b = brandId == null ? null : brandService.getById(brandId);
        if (b == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "品牌不存在: " + brandId);
        }
        return b.getName();
    }

    // 固定库存：所有产品在独立站显示为有库存，库存数量默认 10。
    private static final int DEFAULT_STOCK_QUANTITY = 10;

    private WcProduct build(SupplierProduct p, long wcCategoryId, long wcBrandId, boolean enabled) {
        BigDecimal wholesale = p.getWholesalePrice() == null ? BigDecimal.ZERO : p.getWholesalePrice();
        String regularPrice = wholesale.multiply(props.getRegularMultiplier())
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
        String salePrice = wholesale.multiply(props.getSaleMultiplier())
                .setScale(2, RoundingMode.HALF_UP).toPlainString();

        return new WcProduct(p.getName(), p.getProductCode(), regularPrice, salePrice,
                DEFAULT_STOCK_QUANTITY, enabled ? "publish" : "draft", wcCategoryId, wcBrandId, p.getImageUrl());
    }

    /** 用产品真实分类在 WC 建/查（父→子层级），返回叶子分类 WC id；无分类返回 0。 */
    private long resolveWcCategory(Long categoryId, Map<Long, Long> cache) {
        if (categoryId == null) {
            return 0L;
        }
        return cache.computeIfAbsent(categoryId, cid -> {
            Category c = categoryService.getById(cid);
            if (c == null) {
                return 0L;
            }
            long parentWcId = 0L;
            if (c.getParentId() != null && c.getParentId() != 0L) {
                Category parent = categoryService.getById(c.getParentId());
                if (parent != null) {
                    parentWcId = wc.ensureCategory(parent.getName(), 0L);
                }
            }
            return wc.ensureCategory(c.getName(), parentWcId);
        });
    }

    /** 用产品真实品牌在 WC 建/查原生品牌，返回品牌 WC id；无品牌返回 0。 */
    private long resolveWcBrand(Long brandId, Map<Long, Long> cache) {
        if (brandId == null) {
            return 0L;
        }
        return cache.computeIfAbsent(brandId, bid -> wc.ensureBrand(brandName(bid)));
    }

    private void saveRecord(Long supplierProductId, Long wcId, String sku, String status, String error) {
        WcSyncRecord rec = recordMapper.selectById(supplierProductId);
        boolean isNew = rec == null;
        if (isNew) {
            rec = new WcSyncRecord();
            rec.setSupplierProductId(supplierProductId);
        }
        if (wcId != null) {
            rec.setWcProductId(wcId);
        }
        rec.setSku(sku);
        rec.setLastStatus(status);
        rec.setLastSyncedTime(LocalDateTime.now());
        rec.setLastError(error);
        if (isNew) {
            recordMapper.insert(rec);
        } else {
            recordMapper.updateById(rec);
        }
    }
}
