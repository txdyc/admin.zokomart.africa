# 独立站同步（WooCommerce 商品同步）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在后台「供应商产品」页选定供应商+品牌，手动把这些产品单向推送到 WooCommerce 独立站（商品/价格/库存/图片/品牌分类），按 SKU 幂等。

**Architecture:** 新增后端 `module/wcsync`：`WooCommerceClient`（封装 WC REST API，Basic Auth）+ `WcSyncService`（选品→映射→逐条幂等 upsert，用 `wc_sync_record` 表记录映射/状态）。前端复用供应商产品页加「同步到独立站」对话框。配置/密钥在 `application-local.yml`。

**Tech Stack:** SpringBoot 3.5 + `java.net.http.HttpClient` + Jackson + MyBatis-Plus + Sa-Token；Vue3 + Ant Design Vue + Vitest。

**仓库根目录：** 后端 `D:\GHANA\claude\admin.zokomart.africa\backend`，前端 `D:\GHANA\claude\admin.zokomart.africa\frontend`。Maven 用 JDK 21：`JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" ...`。前端 `pnpm --dir "..." <script>`。用绝对路径，勿 `cd`。本机 MySQL/Redis 已运行；超管 `superadmin / Admin@123`。

**分支：** 两仓 `feat/wc-sync`（后端分支已建）。**已核对 id：** 新菜单按钮 **2065**（种子按钮到 2064）。

**实现取舍（相对 spec 的细化）：** 采用**逐条 upsert**（create/update 单条）而非 batch——更简单健壮、逐条错误清晰；行为（按 SKU 幂等、逐条结果）与 spec 一致，batch 留作后续优化。

---

## File Structure

### 后端 `backend/`
- Modify `src/main/resources/application.yml`（加 `app.wc` 占位）
- Modify `.../common/result/ResultCode.java`（WC_NOT_CONFIGURED/WC_API_ERROR）
- Create `.../module/wcsync/config/WcSyncProperties.java`
- Create `.../module/wcsync/client/WcProduct.java`
- Create `.../module/wcsync/client/WooCommerceClient.java`（接口，便于 mock）
- Create `.../module/wcsync/client/impl/WooCommerceClientImpl.java`
- Create `.../module/wcsync/entity/WcSyncRecord.java` + `mapper/WcSyncRecordMapper.java`
- Create `.../module/wcsync/vo/WcSyncResultVO.java` + `vo/WcSyncRowError.java`
- Create `.../module/wcsync/dto/WcSyncRequest.java`
- Create `.../module/wcsync/service/WcSyncService.java` + `impl/WcSyncServiceImpl.java`
- Create `.../module/wcsync/controller/WcSyncController.java`
- Create `src/main/resources/db/migration/V13__wc_sync.sql`
- Create `src/test/java/africa/zokomart/admin/wcsync/WcSyncServiceTest.java`

### 前端 `frontend/`
- Create `src/types/wcSync.d.ts`
- Create `src/api/wcSync.ts`
- Create `src/views/product/supplier-product/SupplierProductWcSyncModal.vue`
- Modify `src/views/product/supplier-product/index.vue`（按钮+挂载）
- Create `tests/unit/supplier-product-wc-sync-modal.spec.ts`

---

## 后端任务

### Task WB1: 配置 + 错误码

**Files:** Modify `application.yml`, `ResultCode.java`; Create `WcSyncProperties.java`

- [ ] **Step 1: ResultCode 加两码**

把 `ResultCode.java` 中 `SCRAPE_EMPTY(40013, "未从目标页解析到产品");` 末尾分号改逗号并追加：
```java
    SCRAPE_EMPTY(40013, "未从目标页解析到产品"),

    // WooCommerce 同步
    WC_NOT_CONFIGURED(40014, "未配置 WooCommerce 站点/密钥"),
    WC_API_ERROR(40015, "WooCommerce 接口调用失败");
```

- [ ] **Step 2: application.yml 加 app.wc**

在 `app:` 节点下（与 `upload:`/`scrape:` 同级）加：
```yaml
  wc:
    base-url: ${WC_BASE_URL:}
    consumer-key: ${WC_CONSUMER_KEY:}
    consumer-secret: ${WC_CONSUMER_SECRET:}
    price-multiplier: 1.7
```

- [ ] **Step 3: WcSyncProperties**

`.../module/wcsync/config/WcSyncProperties.java`
```java
package africa.zokomart.admin.module.wcsync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** WooCommerce 同步配置（密钥放 application-local.yml）。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.wc")
public class WcSyncProperties {
    private String baseUrl;
    private String consumerKey;
    private String consumerSecret;
    private BigDecimal priceMultiplier = new BigDecimal("1.7");
}
```

- [ ] **Step 4: 编译 + Commit**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -DskipTests compile` → BUILD SUCCESS
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/resources/application.yml src/main/java/africa/zokomart/admin/common/result/ResultCode.java src/main/java/africa/zokomart/admin/module/wcsync/config/WcSyncProperties.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(wcsync): config props + result codes

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task WB2: WcProduct + Client 接口 + 记录表实体/mapper + V13

**Files:** Create WcProduct, WooCommerceClient (interface), WcSyncRecord, WcSyncRecordMapper, V13 migration

- [ ] **Step 1: WcProduct（推送载体）**

`.../module/wcsync/client/WcProduct.java`
```java
package africa.zokomart.admin.module.wcsync.client;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 推往 WooCommerce 的单个商品载体。regularPrice 为字符串（WC 价格用字符串）。 */
@Data
@AllArgsConstructor
public class WcProduct {
    private String name;
    private String sku;
    private String regularPrice;
    private int stockQuantity;
    private String status;     // "publish" | "draft"
    private long categoryId;   // WC 商品分类 id（品牌）
    private String imageUrl;   // 可空
}
```

- [ ] **Step 2: WooCommerceClient 接口**

`.../module/wcsync/client/WooCommerceClient.java`
```java
package africa.zokomart.admin.module.wcsync.client;

public interface WooCommerceClient {

    /** 是否已配置（base-url/key/secret 齐全）。 */
    boolean configured();

    /** 按名查/建 WC 商品分类，返回分类 id。 */
    long ensureCategory(String name);

    /** 按 SKU 查 WC 商品 id；不存在返回 null。 */
    Long findProductIdBySku(String sku);

    /** 新建商品，返回新 WC 商品 id。 */
    long createProduct(WcProduct product);

    /** 更新已存在商品。 */
    void updateProduct(long wcProductId, WcProduct product);
}
```

- [ ] **Step 3: WcSyncRecord 实体 + mapper**

`.../module/wcsync/entity/WcSyncRecord.java`
```java
package africa.zokomart.admin.module.wcsync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** WooCommerce 同步记录：后台产品 ↔ WC 商品 id 映射 + 最近同步状态。主键=供应商产品id。 */
@Data
@TableName("wc_sync_record")
public class WcSyncRecord {
    @TableId(type = IdType.INPUT)
    private Long supplierProductId;
    private Long wcProductId;
    private String sku;
    private String lastStatus;       // CREATED/UPDATED/DRAFTED/FAILED
    private LocalDateTime lastSyncedTime;
    private String lastError;
}
```
`.../module/wcsync/mapper/WcSyncRecordMapper.java`
```java
package africa.zokomart.admin.module.wcsync.mapper;

import africa.zokomart.admin.module.wcsync.entity.WcSyncRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface WcSyncRecordMapper extends BaseMapper<WcSyncRecord> {
}
```

- [ ] **Step 4: V13 迁移**

`src/main/resources/db/migration/V13__wc_sync.sql`
```sql
-- ===========================================================================
-- V13: WooCommerce 同步记录表 + 同步权限按钮 wc:sync（挂"供应商产品"菜单 1110）。
-- ===========================================================================
CREATE TABLE wc_sync_record (
    supplier_product_id BIGINT       NOT NULL COMMENT '后台供应商产品(主键关联)',
    wc_product_id       BIGINT                DEFAULT NULL COMMENT 'WooCommerce 商品 id',
    sku                 VARCHAR(64)           DEFAULT NULL,
    last_status         VARCHAR(32)           DEFAULT NULL COMMENT 'CREATED/UPDATED/DRAFTED/FAILED',
    last_synced_time    DATETIME              DEFAULT NULL,
    last_error          VARCHAR(512)          DEFAULT NULL,
    PRIMARY KEY (supplier_product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'WooCommerce 同步记录';

INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2065, 1110, '同步到独立站', 3, 'wc:sync', NULL, NULL, NULL, 5, 1, 1, NOW(), 0, 0);
```

- [ ] **Step 5: 编译 + Commit**

Run: `... -DskipTests compile` → BUILD SUCCESS
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/wcsync/client/WcProduct.java src/main/java/africa/zokomart/admin/module/wcsync/client/WooCommerceClient.java src/main/java/africa/zokomart/admin/module/wcsync/entity/WcSyncRecord.java src/main/java/africa/zokomart/admin/module/wcsync/mapper/WcSyncRecordMapper.java src/main/resources/db/migration/V13__wc_sync.sql
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(wcsync): WcProduct/client interface + sync record table (V13)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task WB3: WooCommerceClientImpl（HTTP + Jackson）

**Files:** Create `.../module/wcsync/client/impl/WooCommerceClientImpl.java`

- [ ] **Step 1: 实现**

```java
package africa.zokomart.admin.module.wcsync.client.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.config.WcSyncProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class WooCommerceClientImpl implements WooCommerceClient {

    private final WcSyncProperties props;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public boolean configured() {
        return StringUtils.hasText(props.getBaseUrl())
                && StringUtils.hasText(props.getConsumerKey())
                && StringUtils.hasText(props.getConsumerSecret());
    }

    private String base() {
        String b = props.getBaseUrl().trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    private String authHeader() {
        String raw = props.getConsumerKey() + ":" + props.getConsumerSecret();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode send(String method, String path, JsonNode body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", authHeader())
                    .header("Content-Type", "application/json");
            HttpRequest.BodyPublisher pub = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body), StandardCharsets.UTF_8);
            b.method(method, pub);
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new BusinessException(ResultCode.WC_API_ERROR,
                        "WC " + method + " " + path + " -> " + resp.statusCode());
            }
            return resp.body() == null || resp.body().isEmpty() ? om.createObjectNode() : om.readTree(resp.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.WC_API_ERROR, "WC 请求异常: " + e.getMessage());
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public long ensureCategory(String name) {
        JsonNode arr = send("GET", "/wp-json/wc/v3/products/categories?search=" + enc(name) + "&per_page=100", null);
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                if (name.equalsIgnoreCase(n.path("name").asText())) {
                    return n.path("id").asLong();
                }
            }
        }
        ObjectNode body = om.createObjectNode();
        body.put("name", name);
        return send("POST", "/wp-json/wc/v3/products/categories", body).path("id").asLong();
    }

    @Override
    public Long findProductIdBySku(String sku) {
        JsonNode arr = send("GET", "/wp-json/wc/v3/products?sku=" + enc(sku), null);
        if (arr.isArray() && arr.size() > 0) {
            return arr.get(0).path("id").asLong();
        }
        return null;
    }

    private ObjectNode toJson(WcProduct p) {
        ObjectNode body = om.createObjectNode();
        body.put("name", p.getName());
        body.put("type", "simple");
        body.put("sku", p.getSku());
        body.put("regular_price", p.getRegularPrice());
        body.put("manage_stock", true);
        body.put("stock_quantity", p.getStockQuantity());
        body.put("status", p.getStatus());
        ArrayNode cats = body.putArray("categories");
        cats.addObject().put("id", p.getCategoryId());
        if (StringUtils.hasText(p.getImageUrl())) {
            ArrayNode imgs = body.putArray("images");
            imgs.addObject().put("src", p.getImageUrl());
        }
        return body;
    }

    @Override
    public long createProduct(WcProduct product) {
        return send("POST", "/wp-json/wc/v3/products", toJson(product)).path("id").asLong();
    }

    @Override
    public void updateProduct(long wcProductId, WcProduct product) {
        send("PUT", "/wp-json/wc/v3/products/" + wcProductId, toJson(product));
    }
}
```

- [ ] **Step 2: 编译 + Commit**

Run: `... -DskipTests compile` → BUILD SUCCESS
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/wcsync/client/impl/WooCommerceClientImpl.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(wcsync): WooCommerce REST client (http+jackson, basic auth)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task WB4: VO/DTO + 同步服务

**Files:** Create WcSyncResultVO, WcSyncRowError, WcSyncRequest, WcSyncService(+impl)

- [ ] **Step 1: WcSyncRowError + WcSyncResultVO + WcSyncRequest**

`.../module/wcsync/vo/WcSyncRowError.java`
```java
package africa.zokomart.admin.module.wcsync.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WcSyncRowError {
    private Long supplierProductId;
    private String productCode;
    private String reason;
}
```
`.../module/wcsync/vo/WcSyncResultVO.java`
```java
package africa.zokomart.admin.module.wcsync.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WcSyncResultVO {
    private int total;
    private int created;
    private int updated;
    private int drafted;
    private int skipped;
    private int failed;
    private List<WcSyncRowError> errors = new ArrayList<>();
}
```
`.../module/wcsync/dto/WcSyncRequest.java`
```java
package africa.zokomart.admin.module.wcsync.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WcSyncRequest {
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    @NotEmpty(message = "请至少选择一个品牌")
    private List<Long> brandIds;
}
```

- [ ] **Step 2: WcSyncService 接口**

`.../module/wcsync/service/WcSyncService.java`
```java
package africa.zokomart.admin.module.wcsync.service;

import africa.zokomart.admin.module.wcsync.vo.WcSyncResultVO;

import java.util.List;

public interface WcSyncService {

    /** 把该供应商在选定品牌下的产品单向同步到 WooCommerce（按 SKU 幂等）。 */
    WcSyncResultVO syncSupplierBrands(Long supplierId, List<Long> brandIds);
}
```

- [ ] **Step 3: WcSyncServiceImpl**

`.../module/wcsync/service/impl/WcSyncServiceImpl.java`
```java
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
```

- [ ] **Step 4: 编译 + Commit**

Run: `... -DskipTests compile` → BUILD SUCCESS
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/wcsync/vo src/main/java/africa/zokomart/admin/module/wcsync/dto src/main/java/africa/zokomart/admin/module/wcsync/service
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(wcsync): sync service (map/upsert by SKU, record state)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task WB5: 控制器

**Files:** Create `.../module/wcsync/controller/WcSyncController.java`

- [ ] **Step 1: 控制器**

```java
package africa.zokomart.admin.module.wcsync.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.wcsync.dto.WcSyncRequest;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncResultVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "独立站同步")
public class WcSyncController {

    private final WcSyncService wcSyncService;

    @PostMapping("/api/wc-sync/supplier-brands")
    @SaCheckPermission("wc:sync")
    public Result<WcSyncResultVO> sync(@Valid @RequestBody WcSyncRequest req) {
        return Result.ok(wcSyncService.syncSupplierBrands(req.getSupplierId(), req.getBrandIds()));
    }
}
```

- [ ] **Step 2: 编译 + Commit**

Run: `... -DskipTests compile` → BUILD SUCCESS
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/wcsync/controller/WcSyncController.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(wcsync): sync endpoint POST /api/wc-sync/supplier-brands

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task WB6: 服务单测（mock WooCommerceClient）

**Files:** Test `src/test/java/africa/zokomart/admin/wcsync/WcSyncServiceTest.java`

> `@MockBean WooCommerceClient`，真实 `WcSyncService` + DB。自建供应商/品牌/授权/产品 via API；库存 via mapper.insert。用 ArgumentCaptor 断言推送载体的价格/库存/状态/分类，及 create→update 幂等。结束清理。

- [ ] **Step 1: 写测试**

```java
package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.inventory.entity.InventoryStock;
import africa.zokomart.admin.module.inventory.mapper.InventoryStockMapper;
import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class WcSyncServiceTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    WcSyncService wcSyncService;
    @Autowired
    InventoryStockMapper inventoryStockMapper;

    @MockBean
    WooCommerceClient wc;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}")).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    @Test
    void maps_price_stock_status_and_is_idempotent() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"WC_Sup_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"WC_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"))
                .andExpect(jsonPath("$.code").value(0));
        // p1: 未填零售价、批发价 100 -> 期望 170.00；库存 20
        long p1 = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"P1_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCA_" + ts + "\",\"wholesalePrice\":100,\"minPurchaseQty\":1,\"status\":1}", t);
        // p2: 已填零售价 200 -> 期望 200.00；无库存 -> 0
        long p2 = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"P2_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCB_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,\"minPurchaseQty\":1,\"status\":1}", t);
        InventoryStock st = new InventoryStock();
        st.setSupplierProductId(p1);
        st.setSupplierId(supplierId);
        st.setBrandId(brandId);
        st.setQuantity(20);
        inventoryStockMapper.insert(st);

        when(wc.configured()).thenReturn(true);
        when(wc.ensureCategory(any())).thenReturn(100L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(9001L);

        // 首次同步：两条都 create
        WcSyncResultVO r1 = wcSyncService.syncSupplierBrands(supplierId, List.of(brandId));
        org.junit.jupiter.api.Assertions.assertEquals(2, r1.getTotal());
        org.junit.jupiter.api.Assertions.assertEquals(2, r1.getCreated());
        org.junit.jupiter.api.Assertions.assertEquals(0, r1.getFailed());

        ArgumentCaptor<WcProduct> cap = ArgumentCaptor.forClass(WcProduct.class);
        verify(wc, times(2)).createProduct(cap.capture());
        WcProduct a = cap.getAllValues().stream().filter(x -> x.getSku().equals("WCA_" + ts)).findFirst().orElseThrow();
        WcProduct b = cap.getAllValues().stream().filter(x -> x.getSku().equals("WCB_" + ts)).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("170.00", a.getRegularPrice());
        org.junit.jupiter.api.Assertions.assertEquals(20, a.getStockQuantity());
        org.junit.jupiter.api.Assertions.assertEquals("publish", a.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(100L, a.getCategoryId());
        org.junit.jupiter.api.Assertions.assertEquals("200.00", b.getRegularPrice());
        org.junit.jupiter.api.Assertions.assertEquals(0, b.getStockQuantity());

        // 再次同步：已有记录 -> 走 update（幂等）
        reset(wc);
        when(wc.configured()).thenReturn(true);
        when(wc.ensureCategory(any())).thenReturn(100L);
        WcSyncResultVO r2 = wcSyncService.syncSupplierBrands(supplierId, List.of(brandId));
        org.junit.jupiter.api.Assertions.assertEquals(2, r2.getUpdated());
        org.junit.jupiter.api.Assertions.assertEquals(0, r2.getCreated());
        verify(wc, never()).createProduct(any());
        verify(wc, times(2)).updateProduct(anyLong(), any());

        // 清理
        inventoryStockMapper.deleteById(st.getId());
        for (long id : new long[]{p1, p2}) {
            mvc.perform(delete("/api/supplier-products/" + id).header("Authorization", t));
        }
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }

    @Test
    void rejects_when_not_configured() {
        when(wc.configured()).thenReturn(false);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> wcSyncService.syncSupplierBrands(1L, List.of(1L)));
    }
}
```

- [ ] **Step 2: 运行（首跑应用 V13）**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -Dtest=WcSyncServiceTest test`
Expected: 2 用例通过。注意：`wc_sync_record` 会残留这两条产品的记录；测试删了产品但未删 record（无妨，下次同步按 sku 兜底）。如需干净，可在清理里加 `recordMapper` 删除（可选）。

- [ ] **Step 3: 全量回归**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" test`
Expected: BUILD SUCCESS（既有 62 + 2 = 64）。

- [ ] **Step 4: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/test/java/africa/zokomart/admin/wcsync/WcSyncServiceTest.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "test(wcsync): sync mapping + idempotent upsert (mocked WC client)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 前端任务

### Task WF0: 建分支

- [ ] **Step 1:**
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -- src/types/components.d.ts 2>/dev/null; true
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout main
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -b feat/wc-sync
```

---

### Task WF1: 类型 + API

**Files:** Create `src/types/wcSync.d.ts`, `src/api/wcSync.ts`

- [ ] **Step 1: 类型**

`src/types/wcSync.d.ts`
```typescript
import type { Id } from './api';

export interface WcSyncRowError {
  supplierProductId: Id;
  productCode: string | null;
  reason: string;
}

export interface WcSyncResult {
  total: number;
  created: number;
  updated: number;
  drafted: number;
  skipped: number;
  failed: number;
  errors: WcSyncRowError[];
}
```

- [ ] **Step 2: API**

`src/api/wcSync.ts`
```typescript
import { http } from '@/utils/request';
import type { Id } from '@/types/api';
import type { WcSyncResult } from '@/types/wcSync';

export const apiWcSync = (payload: { supplierId: Id; brandIds: Id[] }) =>
  http.post<WcSyncResult>('/wc-sync/supplier-brands', payload);
```

- [ ] **Step 3: Commit**
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add src/types/wcSync.d.ts src/api/wcSync.ts
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "feat(wcsync): types + sync api

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task WF2: 同步对话框

**Files:** Create `src/views/product/supplier-product/SupplierProductWcSyncModal.vue`

- [ ] **Step 1: 组件**

```vue
<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { SelectOption } from '@/components/SchemaForm.vue';
import { apiWcSync } from '@/api/wcSync';
import { apiAuthorizedBrands } from '@/api/basedata/supplierBrand';
import type { WcSyncResult } from '@/types/wcSync';
import type { Id } from '@/types/api';

const props = defineProps<{
  open: boolean;
  supplierOptions: SelectOption[];
  defaultSupplierId?: Id | null;
}>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>();

const form = reactive<{ supplierId?: Id; brandIds: Id[] }>({ brandIds: [] });
const brandOptions = ref<SelectOption[]>([]);
const result = ref<WcSyncResult | null>(null);
const syncing = ref(false);

async function loadBrands(supplierId?: Id) {
  brandOptions.value = [];
  form.brandIds = [];
  if (supplierId == null) return;
  const list = await apiAuthorizedBrands(supplierId);
  brandOptions.value = list.map((b) => ({ label: b.brandName ?? String(b.brandId), value: b.brandId }));
  if (brandOptions.value.length === 0) {
    message.warning('该供应商暂无已授权品牌');
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      result.value = null;
      form.supplierId = (props.defaultSupplierId ?? undefined) as Id | undefined;
      loadBrands(form.supplierId);
    }
  },
);
watch(() => form.supplierId, (v) => loadBrands(v));

async function onSync() {
  if (form.supplierId == null) {
    message.warning('请选择供应商');
    return;
  }
  if (form.brandIds.length === 0) {
    message.warning('请至少选择一个品牌');
    return;
  }
  syncing.value = true;
  try {
    result.value = await apiWcSync({ supplierId: form.supplierId, brandIds: form.brandIds });
    message.success(
      `同步完成：新增 ${result.value.created}，更新 ${result.value.updated}，下架 ${result.value.drafted}，跳过 ${result.value.skipped}，失败 ${result.value.failed}`,
    );
  } finally {
    syncing.value = false;
  }
}

function onClose() {
  emit('update:open', false);
}

defineExpose({ form, brandOptions, result, onSync });
</script>

<template>
  <a-modal :open="open" title="同步到独立站 (WooCommerce)" :width="720" @cancel="onClose">
    <a-form layout="vertical">
      <a-form-item label="供应商" required>
        <a-select v-model:value="form.supplierId" :options="supplierOptions" placeholder="选择供应商"
          show-search option-filter-prop="label" style="width: 100%" />
      </a-form-item>
      <a-form-item label="品牌（可多选，仅已授权）" required>
        <a-select v-model:value="form.brandIds" :options="brandOptions" mode="multiple"
          placeholder="选择一个或多个品牌" style="width: 100%" />
      </a-form-item>
    </a-form>

    <div v-if="result" class="mt-2">
      <a-descriptions size="small" :column="3" bordered>
        <a-descriptions-item label="总数">{{ result.total }}</a-descriptions-item>
        <a-descriptions-item label="新增">{{ result.created }}</a-descriptions-item>
        <a-descriptions-item label="更新">{{ result.updated }}</a-descriptions-item>
        <a-descriptions-item label="下架">{{ result.drafted }}</a-descriptions-item>
        <a-descriptions-item label="跳过">{{ result.skipped }}</a-descriptions-item>
        <a-descriptions-item label="失败">{{ result.failed }}</a-descriptions-item>
      </a-descriptions>
      <a-table v-if="result.errors.length" class="mt-2" size="small" :pagination="false"
        :data-source="result.errors"
        :columns="[
          { title: '产品编码', dataIndex: 'productCode', width: 160 },
          { title: '原因', dataIndex: 'reason' },
        ]" row-key="supplierProductId" />
    </div>

    <template #footer>
      <a-space>
        <a-button @click="onClose">关闭</a-button>
        <a-button type="primary" :loading="syncing" data-test="do-wc-sync" @click="onSync">开始同步</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
```

- [ ] **Step 2: 类型检查 + Commit**

Run: `pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" exec vue-tsc --noEmit` → 无新增错误
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add src/views/product/supplier-product/SupplierProductWcSyncModal.vue
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "feat(wcsync): sync-to-WooCommerce modal

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task WF3: 接入页面

**Files:** Modify `src/views/product/supplier-product/index.vue`

- [ ] **Step 1: 脚本区 import + 状态**

在 import 区（`import SupplierProductScrapeModal ...` 之后）加：
```typescript
import SupplierProductWcSyncModal from './SupplierProductWcSyncModal.vue';
```
在 `function openScrape() { ... }` 之后加：
```typescript
const wcSyncOpen = ref(false);
function openWcSync() {
  wcSyncOpen.value = true;
}
```
把 `defineExpose({ ... openScrape });` 追加 `openWcSync`：
```typescript
defineExpose({ openCreate, openEdit, onSubmit, onDelete, onFilterChange, openImport, onImported, openScrape, openWcSync });
```

- [ ] **Step 2: 模板区按钮 + 挂载**

在「从URL获取」按钮所在 `<a-space>` 内追加（在「从URL获取」之后）：
```vue
          <a-button v-perm="'wc:sync'" data-test="supplier-product-wc-sync" @click="openWcSync">
            同步到独立站
          </a-button>
```
在 `<SupplierProductScrapeModal ... />` 之后加：
```vue
    <SupplierProductWcSyncModal
      v-model:open="wcSyncOpen"
      :supplier-options="supplierOptions"
      :default-supplier-id="filter.supplierId"
    />
```

- [ ] **Step 3: 构建 + Commit**

Run: `pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" build` → 通过
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -- src/types/components.d.ts 2>/dev/null; true
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add src/views/product/supplier-product/index.vue
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "feat(wcsync): wire sync button into supplier-product page

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task WF4: 组件测试 + 全量验证

**Files:** Test `tests/unit/supplier-product-wc-sync-modal.spec.ts`

- [ ] **Step 1: 测试**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import WcSyncModal from '@/views/product/supplier-product/SupplierProductWcSyncModal.vue';

const syncMock = vi.fn();
const brandsMock = vi.fn();

vi.mock('@/api/wcSync', () => ({ apiWcSync: (p: any) => syncMock(p) }));
vi.mock('@/api/basedata/supplierBrand', () => ({ apiAuthorizedBrands: (id: any) => brandsMock(id) }));
vi.mock('ant-design-vue', () => ({ message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }));

const stubs = {
  'a-modal': true, 'a-form': true, 'a-form-item': true, 'a-select': true, 'a-button': true,
  'a-space': true, 'a-descriptions': true, 'a-descriptions-item': true, 'a-table': true,
};

describe('SupplierProductWcSyncModal', () => {
  beforeEach(() => {
    syncMock.mockReset();
    brandsMock.mockReset();
    brandsMock.mockResolvedValue([{ brandId: '10', brandName: 'Morgan' }]);
  });

  it('loads authorized brands on open', async () => {
    const wrapper = mount(WcSyncModal, {
      props: { open: false, supplierOptions: [{ label: 'S1', value: '1' }], defaultSupplierId: '1' },
      global: { stubs },
    });
    await wrapper.setProps({ open: true });
    await new Promise((r) => setTimeout(r, 0));
    expect(brandsMock).toHaveBeenCalledWith('1');
    expect(wrapper.vm.brandOptions).toEqual([{ label: 'Morgan', value: '10' }]);
  });

  it('submits supplierId + brandIds and renders result', async () => {
    syncMock.mockResolvedValue({ total: 2, created: 2, updated: 0, drafted: 0, skipped: 0, failed: 0, errors: [] });
    const wrapper = mount(WcSyncModal, {
      props: { open: true, supplierOptions: [{ label: 'S1', value: '1' }], defaultSupplierId: '1' },
      global: { stubs },
    });
    await new Promise((r) => setTimeout(r, 0));
    wrapper.vm.form.supplierId = '1';
    wrapper.vm.form.brandIds = ['10'];
    await wrapper.vm.onSync();
    expect(syncMock).toHaveBeenCalledTimes(1);
    const payload = syncMock.mock.calls[0][0];
    expect(payload.supplierId).toBe('1');
    expect(payload.brandIds).toEqual(['10']);
    expect(wrapper.vm.result?.created).toBe(2);
  });
});
```

- [ ] **Step 2: 运行单测 + 全量 + 构建**

Run:
```
pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" exec vitest run tests/unit/supplier-product-wc-sync-modal.spec.ts
pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" test:unit
pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" build
```
Expected: 新测 2 通过；全量通过；build 成功。

- [ ] **Step 3: Commit**
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -- src/types/components.d.ts 2>/dev/null; true
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add tests/unit/supplier-product-wc-sync-modal.spec.ts
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "test(wcsync): sync modal component test

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 收尾

- [ ] **配置**：在 `backend/src/main/resources/application-local.yml` 填 `app.wc.base-url/consumer-key/consumer-secret`（WC 后台生成）。未配置时同步端点返回 `WC_NOT_CONFIGURED(40014)`，属预期。
- [ ] **联调**：后端重建+重启（见 [[backend-runtime-rebuild]]）→ 供应商产品页「同步到独立站」选供应商+品牌→同步→到 WooCommerce 后台确认商品/价格/库存/分类。注意图片 URL 须 WP 服务器公网可达、WC 站点 HTTPS。
- [ ] **完成分支**：superpowers:finishing-a-development-branch（两仓 `feat/wc-sync`）。

---

## Self-Review 结论（计划编写者自查）

- **Spec 覆盖**：单向推送(WB4)、手动端点(WB5)、库存→stock_quantity(WB4 build)、品牌→WC分类(WB4 ensureCategory)、零售价规则 retailPrice>0 否则 1.7×(WB4 build)、停用→draft(WB4)、配置/密钥(WB1)、记录表 V13(WB2)、前端按钮+多选品牌对话框(WF2/WF3)、测试(WB6/WF4) 均覆盖。
- **取舍**：逐条 upsert 代替 batch（已在头部说明，行为一致）；从未同步且停用的产品跳过（spec §4.3 step2 一致）。
- **占位符**：无 TODO/TBD；每步含完整代码与命令；菜单按钮 id 2065 已按现有 max(2064) 选定。
- **类型一致**：后端 `WcSyncResultVO`(total/created/updated/drafted/skipped/failed/errors) ↔ 前端 `WcSyncResult` 字段一致；`WcSyncRowError`(supplierProductId/productCode/reason) ↔ 前端一致；端点 `/api/wc-sync/supplier-brands` ↔ 前端 `/wc-sync/supplier-brands`；`WcProduct` 字段（name/sku/regularPrice/stockQuantity/status/categoryId/imageUrl）服务与客户端一致；`InventoryStock.getSupplierProductId/getQuantity`、`SupplierProduct.getRetailPrice/getWholesalePrice/getStatus/getBrandId/getProductCode/getImageUrl/getName` 均为既有字段。
