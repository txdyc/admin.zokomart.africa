package africa.zokomart.admin.module.wcsync.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.ad.entity.AdProductImage;
import africa.zokomart.admin.module.ad.mapper.AdProductImageMapper;
import africa.zokomart.admin.module.basedata.entity.Brand;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.service.BrandService;
import africa.zokomart.admin.module.basedata.service.CategoryService;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import africa.zokomart.admin.module.wcsync.client.WcImage;
import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WcProductDetail;
import africa.zokomart.admin.module.wcsync.client.WcProductRef;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.support.AdDescriptionBlock;
import africa.zokomart.admin.module.wcsync.config.WcSyncProperties;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.entity.WcSyncRecord;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncRecordMapper;
import africa.zokomart.admin.module.wcsync.service.WcSyncJobService;
import africa.zokomart.admin.module.wcsync.service.WcSyncLock;
import africa.zokomart.admin.module.wcsync.service.WcSyncRunner;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import africa.zokomart.admin.module.wcsync.vo.WcSyncRowError;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class WcSyncServiceImpl implements WcSyncService {

    private static final int DEFAULT_STOCK_QUANTITY = 10;
    private static final int MAX_FAILED_ITEMS = 200;

    private final WooCommerceClient wc;
    private final WcSyncProperties props;
    private final SupplierService supplierService;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final SupplierProductMapper supplierProductMapper;
    private final WcSyncRecordMapper recordMapper;
    private final WcSyncJobMapper jobMapper;
    private final WcSyncJobService jobService;
    private final WcSyncLock lock;
    private final ObjectMapper om;
    private final AdProductImageMapper adProductImageMapper;

    // WcSyncRunner 依赖本 service，本 service 又依赖 runner —— @Lazy 打破循环。
    @Autowired
    @Lazy
    private WcSyncRunner runner;

    @Override
    public Long startSync(Long supplierId, List<Long> brandIds) {
        if (!wc.configured()) {
            throw new BusinessException(ResultCode.WC_NOT_CONFIGURED);
        }
        if (!lock.tryAcquire()) {
            throw new BusinessException(ResultCode.WC_SYNC_RUNNING);
        }
        try {
            if (supplierService.getById(supplierId) == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在");
            }
            if (brandIds == null || brandIds.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "请至少选择一个品牌");
            }
            List<SupplierProduct> products = loadProducts(supplierId, brandIds);
            String operator = currentOperator();
            WcSyncJob job = jobService.createRunning(supplierId, brandIds, products.size(), operator);
            runner.run(job.getId(), supplierId, brandIds);   // 异步派发，立即返回
            return job.getId();
        } catch (RuntimeException e) {
            lock.release();   // 派发前任何失败都要释放锁；成功派发后由 runSync 的 finally 释放
            throw e;
        }
    }

    @Override
    public void runSync(Long jobId, Long supplierId, List<Long> brandIds) {
        WcSyncJob job = null;
        try {
            job = new WcSyncJob();
            job.setId(jobId);   // 复用 id 做 updateById；只 set 需要更新的字段
            List<SupplierProduct> products = loadProducts(supplierId, brandIds);
            Map<Long, Long> categoryCache = new HashMap<>();
            Map<Long, Long> brandCache = new HashMap<>();
            List<WcSyncRowError> failures = new ArrayList<>();
            int created = 0, updated = 0, drafted = 0, failed = 0, processed = 0;

            for (SupplierProduct p : products) {
                try {
                    String outcome = upsertOne(p, categoryCache, brandCache);
                    switch (outcome) {
                        case "CREATED" -> created++;
                        case "UPDATED" -> updated++;
                        case "DRAFTED" -> drafted++;
                        default -> { }
                    }
                } catch (Exception e) {
                    failed++;
                    if (failures.size() < MAX_FAILED_ITEMS) {
                        failures.add(new WcSyncRowError(p.getId(), p.getProductCode(), e.getMessage()));
                    }
                    saveRecord(p.getId(), null, p.getProductCode(), "FAILED", e.getMessage(), null, null);
                }
                processed++;
                writeProgress(jobId, products.size(), processed, created, updated, drafted, failed, failures, null, null);
            }
            String status = failed == 0 ? WcSyncJobStatus.SUCCESS
                    : (failed >= products.size() ? WcSyncJobStatus.FAILED : WcSyncJobStatus.PARTIAL);
            writeProgress(jobId, products.size(), processed, created, updated, drafted, failed, failures,
                    status, LocalDateTime.now());
        } catch (Exception fatal) {
            // 致命失败：保留已落地的 total/processed/计数等诊断状态，只标记失败 + 记录原因，并打日志。
            log.error("WC sync job {} failed fatally", jobId, fatal);
            LocalDateTime now = LocalDateTime.now();
            WcSyncJob existing = jobMapper.selectById(jobId);
            if (existing != null) {
                existing.setStatus(WcSyncJobStatus.FAILED);
                existing.setEndTime(now);
                existing.setFailedItems(toJson(List.of(
                        new WcSyncRowError(null, null, fatal.getMessage()))));
                jobService.save(existing);   // updateById：existing 各字段非空，原有计数原样保留
            } else {
                // 任务行读不出（极少见）→ 退化为只更新状态/结束时间的最小更新。
                WcSyncJob minimal = new WcSyncJob();
                minimal.setId(jobId);
                minimal.setStatus(WcSyncJobStatus.FAILED);
                minimal.setEndTime(now);
                jobService.save(minimal);
            }
        } finally {
            lock.release();
        }
    }

    @Override
    public WcSyncJobVO getJob(Long jobId) {
        return jobService.getVO(jobId);
    }

    @Override
    public IPage<WcSyncJobVO> listJobs(Long supplierId, long current, long size) {
        return jobService.page(supplierId, current, size);
    }

    // ---- 内部 ----

    private List<SupplierProduct> loadProducts(Long supplierId, List<Long> brandIds) {
        return supplierProductMapper.selectList(
                Wrappers.<SupplierProduct>lambdaQuery()
                        .eq(SupplierProduct::getSupplierId, supplierId)
                        .in(SupplierProduct::getBrandId, brandIds));
    }

    private String currentOperator() {
        try {
            return cn.dev33.satoken.stp.StpUtil.isLogin()
                    ? String.valueOf(cn.dev33.satoken.stp.StpUtil.getLoginId()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 处理单个产品的 upsert + 图片决策，返回 CREATED/UPDATED/DRAFTED。 */
    private String upsertOne(SupplierProduct p, Map<Long, Long> categoryCache, Map<Long, Long> brandCache) {
        boolean enabled = p.getStatus() != null && p.getStatus() == 1;
        WcSyncRecord record = recordMapper.selectById(p.getId());
        Long wcId = record != null ? record.getWcProductId() : null;
        boolean isNew = (wcId == null);
        if (isNew) {
            wcId = wc.findProductIdBySku(p.getProductCode());
            if (wcId != null) {
                isNew = false;   // WC 已有该 SKU，本地无记录 → 视为已存在（收编路径）
            }
        }

        long wcCategoryId = resolveWcCategory(p.getCategoryId(), categoryCache);
        long wcBrandId = resolveWcBrand(p.getBrandId(), brandCache);

        // ---- 图片决策 ----
        String url = p.getImageUrl();
        String imageSrc;          // 传给 WC 的 src；null = 不传 images
        Long adoptImageId = null; // 收编已有图 id（不重传）
        if (!StringUtils.hasText(url)) {
            imageSrc = null;
        } else if (record != null && record.getWcImageId() != null && url.equals(record.getSyncedImageUrl())) {
            imageSrc = null;      // 源未变 → 不重传
        } else if (!isNew && (record == null || record.getWcImageId() == null)) {
            // 历史脏记录：产品已存在但本地没存过图 id → 收编 WC 现有主图，不重传
            adoptImageId = (wcId != null) ? wc.findProductMainImageId(wcId) : null;
            imageSrc = (adoptImageId == null) ? url : null;   // WC 也没图才上传
        } else {
            imageSrc = url;       // 新建带图 / 图源变了 → 上传
        }

        WcProduct wcProduct = build(p, wcCategoryId, wcBrandId, enabled, imageSrc);

        // ---- 广告图（AI 生图保留图）：产品有/有过广告图才走此分支，否则行为与旧版完全一致 ----
        List<AdProductImage> adImages = adProductImageMapper.selectList(
                Wrappers.<AdProductImage>lambdaQuery()
                        .eq(AdProductImage::getSupplierProductId, p.getId())
                        .orderByAsc(AdProductImage::getSort));
        boolean adActive = !adImages.isEmpty()
                || adProductImageMapper.countIncludingDeleted(p.getId()) > 0;
        if (adActive) {
            List<WcImage> imgs = new ArrayList<>();
            if (imageSrc != null) {
                imgs.add(new WcImage(null, imageSrc));
            } else {
                Long mainId = (record != null && record.getWcImageId() != null) ? record.getWcImageId()
                        : adoptImageId != null ? adoptImageId
                        : (wcId != null ? wc.findProductMainImageId(wcId) : null);
                if (mainId != null) imgs.add(new WcImage(mainId, null));
            }
            for (AdProductImage ai : adImages) {
                imgs.add(ai.getWcMediaId() != null
                        ? new WcImage(ai.getWcMediaId(), null)
                        : new WcImage(null, publicAdUrl(ai.getFileUrl())));
            }
            wcProduct.setImagesOverride(imgs);
        }

        String outcome;
        WcProductRef ref;
        if (wcId == null) {
            ref = wc.createProduct(wcProduct);
            wcId = ref.getProductId();
            outcome = "CREATED";
        } else {
            ref = wc.updateProduct(wcId, wcProduct);
            outcome = enabled ? "UPDATED" : "DRAFTED";
        }

        // ---- 回写图片 id / 源 url ----
        Long finalImageId;
        String finalSyncedUrl;
        if (imageSrc != null) {                 // 本次上传了 → 用返回的新 id
            finalImageId = ref.getImageId();
            finalSyncedUrl = url;
        } else if (adoptImageId != null) {      // 收编了已有图
            finalImageId = adoptImageId;
            finalSyncedUrl = url;
        } else {                                // 没动图 → 保留旧值（或用返回里读到的 id 兜底）
            finalImageId = (record != null && record.getWcImageId() != null)
                    ? record.getWcImageId() : ref.getImageId();
            finalSyncedUrl = (record != null) ? record.getSyncedImageUrl() : null;
        }

        saveRecord(p.getId(), wcId, p.getProductCode(), outcome, null, finalImageId, finalSyncedUrl);

        // ---- 广告图回写 media id + 描述标记区块 ----
        if (adActive) {
            List<WcImage> respImgs = ref.getImages() == null ? List.of() : ref.getImages();
            int offset = respImgs.size() - adImages.size();   // 前面是主图（0 或 1 张）
            List<String> wcAdUrls = new ArrayList<>();
            for (int i = 0; i < adImages.size(); i++) {
                int idx = offset + i;
                if (idx < 0 || idx >= respImgs.size()) continue;   // 响应缺图则跳过，下次同步重试
                WcImage r = respImgs.get(idx);
                if (r.src() != null) wcAdUrls.add(r.src());
                AdProductImage ai = adImages.get(i);
                if (ai.getWcMediaId() == null && r.id() != null) {
                    ai.setWcMediaId(r.id());
                    adProductImageMapper.updateById(ai);
                }
            }
            // 本次 upsert 未动描述，读到的是手写内容；以 WC 媒体 URL 写入标记区块。
            WcProductDetail detail = wc.getProduct(wcId);
            wc.updateProductDescription(wcId, AdDescriptionBlock.apply(detail.description(), wcAdUrls));
        }

        return outcome;
    }

    /** 广告图公网绝对 URL：WC sideload 需要能从外部访问到本机文件。 */
    private String publicAdUrl(String fileUrl) {
        String base = props.getPublicFileBaseUrl();
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(ResultCode.WC_NOT_CONFIGURED,
                    "未配置 app.wc.public-file-base-url，无法上传广告图");
        }
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + fileUrl;
    }

    private WcProduct build(SupplierProduct p, long wcCategoryId, long wcBrandId, boolean enabled, String imageSrc) {
        BigDecimal wholesale = p.getWholesalePrice() == null ? BigDecimal.ZERO : p.getWholesalePrice();
        String regularPrice = wholesale.multiply(props.getRegularMultiplier())
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
        String salePrice = wholesale.multiply(props.getSaleMultiplier())
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
        // imagesOverride 默认 null：沿用 imageSrc 旧语义；upsertOne 中广告图分支会按需覆盖。
        return new WcProduct(p.getName(), p.getProductCode(), regularPrice, salePrice,
                DEFAULT_STOCK_QUANTITY, enabled ? "publish" : "draft", wcCategoryId, wcBrandId, imageSrc, null);
    }

    private long resolveWcCategory(Long categoryId, Map<Long, Long> cache) {
        if (categoryId == null) return 0L;
        return cache.computeIfAbsent(categoryId, cid -> {
            Category c = categoryService.getById(cid);
            if (c == null) return 0L;
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

    private long resolveWcBrand(Long brandId, Map<Long, Long> cache) {
        if (brandId == null) return 0L;
        return cache.computeIfAbsent(brandId, bid -> {
            Brand b = brandService.getById(bid);
            if (b == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "品牌不存在: " + bid);
            }
            return wc.ensureBrand(b.getName());
        });
    }

    private void saveRecord(Long supplierProductId, Long wcId, String sku, String status,
                            String error, Long imageId, String syncedUrl) {
        WcSyncRecord rec = recordMapper.selectById(supplierProductId);
        boolean isNew = rec == null;
        if (isNew) {
            rec = new WcSyncRecord();
            rec.setSupplierProductId(supplierProductId);
        }
        if (wcId != null) rec.setWcProductId(wcId);
        rec.setSku(sku);
        rec.setLastStatus(status);
        rec.setLastSyncedTime(LocalDateTime.now());
        rec.setLastError(error);
        if (imageId != null) rec.setWcImageId(imageId);
        if (syncedUrl != null) rec.setSyncedImageUrl(syncedUrl);
        if (isNew) recordMapper.insert(rec);
        else recordMapper.updateById(rec);
    }

    private void writeProgress(Long jobId, int total, int processed, int created, int updated,
                               int drafted, int failed, List<WcSyncRowError> failures,
                               String status, LocalDateTime endTime) {
        WcSyncJob job = new WcSyncJob();
        job.setId(jobId);
        // version 故意保持为 null：MP 默认 NOT_NULL 更新策略会忽略它，发出
        // 普通的 UPDATE ... WHERE id=?（绕过乐观锁谓词）。本任务由单线程 executor
        // 顺序写进度，无并发竞争，无需乐观锁。
        job.setTotal(total);
        job.setProcessed(processed);
        job.setCreatedCount(created);
        job.setUpdatedCount(updated);
        job.setDraftedCount(drafted);
        job.setFailedCount(failed);
        job.setFailedItems(toJson(failures));
        if (status != null) job.setStatus(status);
        if (endTime != null) job.setEndTime(endTime);
        jobService.save(job);
    }

    private String toJson(List<WcSyncRowError> items) {
        try { return om.writeValueAsString(items); }
        catch (Exception e) { return "[]"; }
    }
}
