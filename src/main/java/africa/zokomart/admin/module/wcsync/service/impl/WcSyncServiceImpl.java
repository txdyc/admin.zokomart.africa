package africa.zokomart.admin.module.wcsync.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.entity.Brand;
import africa.zokomart.admin.module.basedata.service.BrandService;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.inventory.entity.InventoryStock;
import africa.zokomart.admin.module.inventory.mapper.InventoryStockMapper;
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
    private final SupplierProductMapper supplierProductMapper;
    private final InventoryStockMapper inventoryStockMapper;
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
        Map<Long, Long> brandCategoryCache = new HashMap<>();

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
                long categoryId = brandCategoryCache.computeIfAbsent(
                        p.getBrandId(), bid -> wc.ensureCategory(brandName(bid)));
                WcProduct wcProduct = build(p, categoryId, enabled);

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

    private WcProduct build(SupplierProduct p, long categoryId, boolean enabled) {
        BigDecimal price;
        if (p.getRetailPrice() != null && p.getRetailPrice().signum() > 0) {
            price = p.getRetailPrice();
        } else {
            BigDecimal wholesale = p.getWholesalePrice() == null ? BigDecimal.ZERO : p.getWholesalePrice();
            price = wholesale.multiply(props.getPriceMultiplier());
        }
        price = price.setScale(2, RoundingMode.HALF_UP);

        InventoryStock stock = inventoryStockMapper.selectOne(
                Wrappers.<InventoryStock>lambdaQuery().eq(InventoryStock::getSupplierProductId, p.getId()));
        int qty = stock == null || stock.getQuantity() == null ? 0 : stock.getQuantity();

        return new WcProduct(p.getName(), p.getProductCode(), price.toPlainString(),
                qty, enabled ? "publish" : "draft", categoryId, p.getImageUrl());
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
