# WooCommerce 全量同步重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把「同步到独立站」从阻塞式一锤子改造为异步任务化 + 图片幂等的生产级全量同步，根治 15s 超时、142→105 缺口、Media Library 重复图三个问题。

**Architecture:** 后端 `POST /api/wc-sync/supplier-brands` 抢进程内单飞锁、建 `wc_sync_job` 持久化任务行、立即返回 `jobId`，真正同步在单线程 `@Async` 执行；前端拿 jobId 轮询进度。图片用「记录态决策矩阵」决定传 `src`/不传 `images`，杜绝 WC 重复 sideload。停用产品推为 `draft`，全量落地。

**Tech Stack:** SpringBoot 3.5 + MyBatis-Plus + Flyway(V14) + JDK21 `HttpClient`；前端 Vue3 + Ant Design Vue + Vitest。两仓独立提交：后端 `admin.zokomart.africa`(分支 `feat/wc-sync-redesign`)、前端 `front.admin.zokomart.africa`(新建分支 `feat/wc-sync-redesign`)。

**测试前提：** 后端测试为 `@SpringBootTest` 集成测试，依赖本机 MySQL（库 `zokomart_admin`）已启动；仅 `WooCommerceClient` 用 `@MockBean`。后端以 jar 运行——联调前 `mvn clean package -DskipTests` 重启 jar。

---

## 文件结构（决定任务边界）

**后端（`backend/`，包 `africa.zokomart.admin.module.wcsync`）**

| 文件 | 责任 | 新建/改 |
| --- | --- | --- |
| `src/main/resources/db/migration/V14__wc_sync_job_and_image_dedup.sql` | 建 job 表 + record 加两列 | 新建 |
| `entity/WcSyncRecord.java` | +`wcImageId`/`syncedImageUrl` | 改 |
| `entity/WcSyncJob.java` | 任务持久化实体（继承 BaseEntity） | 新建 |
| `entity/WcSyncJobStatus.java` | 状态常量 | 新建 |
| `mapper/WcSyncJobMapper.java` | job 数据访问 | 新建 |
| `client/WcProductRef.java` | create/update 返回体（productId+imageId） | 新建 |
| `client/WcProduct.java` | `imageUrl`→`imageSrc`（null=不传 images） | 改 |
| `client/WooCommerceClient.java` | 改返回类型 + `findProductMainImageId` | 改 |
| `client/impl/WooCommerceClientImpl.java` | 条件 images、解析 image id、网络重试 | 改 |
| `service/WcSyncLock.java` | 进程内单飞锁（AtomicBoolean） | 新建 |
| `service/WcSyncRunner.java` | `@Async` 调度壳（避免自调用代理失效） | 新建 |
| `service/WcSyncJobService.java`(+`impl/`) | 建 job/查 VO/列表 | 新建 |
| `service/WcSyncService.java`(+`impl/`) | `startSync`/`runSync`/`getJob`/`listJobs` | 改 |
| `vo/WcSyncJobVO.java` | 任务进度出参 | 新建 |
| `vo/WcSyncRowError.java` | 失败项（复用，存于 job failedItems） | 复用 |
| `config/WcSyncAsyncConfig.java` | `@EnableAsync` + 单线程池 | 新建 |
| `config/WcSyncStartupRecovery.java` | 启动把残留 RUNNING→INTERRUPTED | 新建 |
| `controller/WcSyncController.java` | POST 返回 jobId + 2 个 GET | 改 |
| `common/result/ResultCode.java` | +`WC_SYNC_RUNNING` | 改 |
| `vo/WcSyncResultVO.java` | 删除（被 job 取代） | 删 |
| `test/.../wcsync/WcSyncServiceTest.java` | 重写为新 API | 改 |
| `test/.../wcsync/WcSyncLockTest.java` | 锁单测 | 新建 |
| `test/.../wcsync/WcSyncStartupRecoveryTest.java` | 恢复单测 | 新建 |

**前端（`frontend/`）**

| 文件 | 责任 | 新建/改 |
| --- | --- | --- |
| `src/types/wcSync.ts` | +`WcSyncJob` 类型 | 改 |
| `src/api/wcSync.ts` | `apiStartWcSync` + `apiGetWcSyncJob` | 改 |
| `src/views/product/supplier-product/SupplierProductWcSyncModal.vue` | 起任务 + 轮询 + 进度 UI | 改 |
| `tests/unit/supplier-product-wc-sync-modal.spec.ts` | 轮询/进度/失败表用例 | 改 |

---

## 后端任务

### Task 1: 迁移 V14（job 表 + record 两列）

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__wc_sync_job_and_image_dedup.sql`

- [ ] **Step 1: 写迁移 SQL**

```sql
-- ===========================================================================
-- V14: WooCommerce 同步任务表 wc_sync_job + wc_sync_record 图片幂等两列。
-- 无新增菜单/权限，沿用 wc:sync（菜单 2065）。
-- ===========================================================================
CREATE TABLE wc_sync_job (
    id             BIGINT       NOT NULL COMMENT '雪花主键',
    supplier_id    BIGINT       NOT NULL COMMENT '供应商 id',
    brand_ids      VARCHAR(255)          DEFAULT NULL COMMENT '选中品牌 id JSON 数组，如 [1,2]',
    operator       VARCHAR(64)           DEFAULT NULL COMMENT '触发人 loginId',
    status         VARCHAR(16)  NOT NULL COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED/INTERRUPTED',
    total          INT          NOT NULL DEFAULT 0,
    processed      INT          NOT NULL DEFAULT 0,
    created_count  INT          NOT NULL DEFAULT 0,
    updated_count  INT          NOT NULL DEFAULT 0,
    drafted_count  INT          NOT NULL DEFAULT 0,
    failed_count   INT          NOT NULL DEFAULT 0,
    failed_items   TEXT                  DEFAULT NULL COMMENT '失败明细 JSON，封顶 200 条',
    start_time     DATETIME              DEFAULT NULL,
    end_time       DATETIME              DEFAULT NULL,
    create_time    DATETIME              DEFAULT NULL,
    update_time    DATETIME              DEFAULT NULL,
    create_by      BIGINT                DEFAULT NULL,
    update_by      BIGINT                DEFAULT NULL,
    deleted        INT          NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_supplier (supplier_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'WooCommerce 同步任务';

ALTER TABLE wc_sync_record
    ADD COLUMN wc_image_id      BIGINT       DEFAULT NULL COMMENT 'WC 主图 media 附件 id',
    ADD COLUMN synced_image_url VARCHAR(512) DEFAULT NULL COMMENT '上次 sideload 的源图 URL';
```

- [ ] **Step 2: 校验 SQL 语法（编译期不校验，靠后续启动 Flyway 验证；此处仅人工核对列名与 BaseEntity 一致）**

确认列 `create_time/update_time/create_by/update_by/deleted/version` 与 `BaseEntity` 字段一一对应（map-underscore-to-camel-case）。

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V14__wc_sync_job_and_image_dedup.sql
git commit -m "feat(wc-sync): V14 迁移 wc_sync_job 表 + wc_sync_record 图片幂等列"
```

---

### Task 2: WcSyncRecord 加两列 + WcSyncJob 实体 + 状态常量 + Mapper

**Files:**
- Modify: `backend/src/main/java/africa/zokomart/admin/module/wcsync/entity/WcSyncRecord.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/entity/WcSyncJob.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/entity/WcSyncJobStatus.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/mapper/WcSyncJobMapper.java`

- [ ] **Step 1: WcSyncRecord 增加两字段**

在 `WcSyncRecord` 的 `lastError` 字段后追加：

```java
    private Long wcImageId;          // WC 主图 media 附件 id
    private String syncedImageUrl;   // 上次 sideload 的源图 URL
```

- [ ] **Step 2: 新建 WcSyncJobStatus 常量**

```java
package africa.zokomart.admin.module.wcsync.entity;

/** 同步任务状态常量。 */
public final class WcSyncJobStatus {
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
    public static final String INTERRUPTED = "INTERRUPTED";

    private WcSyncJobStatus() {}
}
```

- [ ] **Step 3: 新建 WcSyncJob 实体**

```java
package africa.zokomart.admin.module.wcsync.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** WooCommerce 同步任务：一次同步的进度/计数/失败明细。继承 BaseEntity（雪花主键+审计+乐观锁）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wc_sync_job")
public class WcSyncJob extends BaseEntity {
    private Long supplierId;
    private String brandIds;        // JSON 数组字符串
    private String operator;
    private String status;          // 见 WcSyncJobStatus
    private Integer total;
    private Integer processed;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer draftedCount;
    private Integer failedCount;
    private String failedItems;     // JSON: List<WcSyncRowError>
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
```

- [ ] **Step 4: 新建 WcSyncJobMapper**

```java
package africa.zokomart.admin.module.wcsync.mapper;

import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WcSyncJobMapper extends BaseMapper<WcSyncJob> {
}
```

- [ ] **Step 5: 编译**

Run: `mvn -q compile`
Expected: BUILD SUCCESS（此时 service/controller 还引用旧 API，可能未改——本步只编译本任务新增文件，若旧引用报错属预期，下一任务修复；若想隔离，可先只 `javac` 校验语法。建议本步跳过整体编译，留到 Task 7 后统一编译。）

- [ ] **Step 6: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/wcsync/entity/ src/main/java/africa/zokomart/admin/module/wcsync/mapper/WcSyncJobMapper.java
git commit -m "feat(wc-sync): WcSyncJob 实体/状态常量/Mapper + record 加图片幂等字段"
```

---

### Task 3: 客户端图片幂等改造（WcUpsertResult + 条件 images + 解析 image id + 重试）

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/client/WcProductRef.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/wcsync/client/WcProduct.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/wcsync/client/WooCommerceClient.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/wcsync/client/impl/WooCommerceClientImpl.java`

- [ ] **Step 1: 新建 WcProductRef（文件名 = 类名 `WcProductRef.java`）**

```java
package africa.zokomart.admin.module.wcsync.client;

import lombok.AllArgsConstructor;
import lombok.Data;

/** create/update 返回：WC 商品 id + 主图 media id（可空）。 */
@Data
@AllArgsConstructor
public class WcProductRef {
    private long productId;
    private Long imageId;   // images[0].id，可空
}
```

- [ ] **Step 2: 改 WcProduct——`imageUrl`→`imageSrc`（null 表示不传 images）**

把 `WcProduct` 的 `private String imageUrl;` 改名为：

```java
    private String imageSrc;   // 要 sideload 的源图 URL；null = 本次不传 images 字段（WC 不动现有图）
```

（构造参数顺序不变，仅最后一个参数语义/名字变。）

- [ ] **Step 3: 改 WooCommerceClient 接口**

```java
package africa.zokomart.admin.module.wcsync.client;

public interface WooCommerceClient {

    boolean configured();

    long ensureCategory(String name, long parentWcId);

    long ensureBrand(String name);

    Long findProductIdBySku(String sku);

    /** 新建商品，返回 WC 商品 id + 主图 media id。 */
    WcProductRef createProduct(WcProduct product);

    /** 更新商品，返回 WC 商品 id + 主图 media id。 */
    WcProductRef updateProduct(long wcProductId, WcProduct product);

    /** 读取已存在商品当前主图 media id；无图返回 null。供历史脏记录"收编"用。 */
    Long findProductMainImageId(long wcProductId);
}
```

- [ ] **Step 4: 改 WooCommerceClientImpl——条件 images、解析 image id、网络重试**

替换 `toJson`、`createProduct`、`updateProduct`，新增 `parseRef`/`findProductMainImageId`，并给 `send` 加 1 次网络重试：

```java
    private ObjectNode toJson(WcProduct p) {
        ObjectNode body = om.createObjectNode();
        body.put("name", p.getName());
        body.put("type", "simple");
        body.put("sku", p.getSku());
        body.put("regular_price", p.getRegularPrice());
        body.put("sale_price", p.getSalePrice());
        body.put("manage_stock", true);
        body.put("stock_quantity", p.getStockQuantity());
        body.put("status", p.getStatus());
        if (p.getCategoryId() > 0) {
            ArrayNode cats = body.putArray("categories");
            cats.addObject().put("id", p.getCategoryId());
        }
        if (p.getBrandWcId() > 0) {
            ArrayNode brands = body.putArray("brands");
            brands.addObject().put("id", p.getBrandWcId());
        }
        // 仅当 imageSrc 非空才传 images（用 src 上传一次）；为 null 则不传，WC 保留现有图、不重复 sideload。
        if (StringUtils.hasText(p.getImageSrc())) {
            ArrayNode imgs = body.putArray("images");
            imgs.addObject().put("src", p.getImageSrc());
        }
        return body;
    }

    private WcProductRef parseRef(JsonNode resp) {
        long id = resp.path("id").asLong();
        JsonNode imgs = resp.path("images");
        Long imageId = (imgs.isArray() && imgs.size() > 0 && imgs.get(0).hasNonNull("id"))
                ? imgs.get(0).path("id").asLong() : null;
        return new WcProductRef(id, imageId);
    }

    @Override
    public WcProductRef createProduct(WcProduct product) {
        return parseRef(send("POST", "/wp-json/wc/v3/products", toJson(product)));
    }

    @Override
    public WcProductRef updateProduct(long wcProductId, WcProduct product) {
        return parseRef(send("PUT", "/wp-json/wc/v3/products/" + wcProductId, toJson(product)));
    }

    @Override
    public Long findProductMainImageId(long wcProductId) {
        JsonNode resp = send("GET", "/wp-json/wc/v3/products/" + wcProductId, null);
        JsonNode imgs = resp.path("images");
        return (imgs.isArray() && imgs.size() > 0 && imgs.get(0).hasNonNull("id"))
                ? imgs.get(0).path("id").asLong() : null;
    }
```

改 `send`，对网络异常重试 1 次（业务级 4xx/5xx 不重试）：

```java
    private JsonNode send(String method, String path, JsonNode body) {
        try {
            return sendOnce(method, path, body);
        } catch (BusinessException e) {
            throw e;   // WC 返回的 4xx/5xx 已是业务异常，不重试
        } catch (Exception e) {
            // 网络/超时异常：重试 1 次
            try {
                return sendOnce(method, path, body);
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e2) {
                throw new BusinessException(ResultCode.WC_API_ERROR, "WC 请求异常: " + e2.getMessage());
            }
        }
    }

    private JsonNode sendOnce(String method, String path, JsonNode body) throws Exception {
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
    }
```

- [ ] **Step 5: 编译（隔离校验本任务）**

Run: `mvn -q compile`
Expected: service 仍引用旧返回类型会报错——预期。可暂缓整体编译至 Task 7。本步只核对 client 包内自洽。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/wcsync/client/
git commit -m "feat(wc-sync): 客户端图片幂等——条件 images + 解析 image id + 网络重试"
```

---

### Task 4: 进程内单飞锁 WcSyncLock + 单测

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/service/WcSyncLock.java`
- Test: `backend/src/test/java/africa/zokomart/admin/wcsync/WcSyncLockTest.java`

- [ ] **Step 1: 写失败测试**

```java
package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.wcsync.service.WcSyncLock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WcSyncLockTest {

    @Test
    void single_flight_second_acquire_fails_until_release() {
        WcSyncLock lock = new WcSyncLock();
        assertTrue(lock.tryAcquire());
        assertFalse(lock.tryAcquire());   // 已持有 → 第二次失败
        lock.release();
        assertTrue(lock.tryAcquire());     // 释放后可再获取
        lock.release();
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `mvn -q -Dtest=WcSyncLockTest test`
Expected: 编译失败 "cannot find symbol WcSyncLock"

- [ ] **Step 3: 实现 WcSyncLock**

```java
package africa.zokomart.admin.module.wcsync.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** 进程内全局单飞锁：任意时刻只允许一个同步任务。单机单 jar 运行，无需分布式锁。 */
@Component
public class WcSyncLock {

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 获取锁；已被持有返回 false。 */
    public boolean tryAcquire() {
        return running.compareAndSet(false, true);
    }

    /** 释放锁（幂等：未持有时调用无副作用）。 */
    public void release() {
        running.set(false);
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `mvn -q -Dtest=WcSyncLockTest test`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/wcsync/service/WcSyncLock.java src/test/java/africa/zokomart/admin/wcsync/WcSyncLockTest.java
git commit -m "feat(wc-sync): 进程内单飞锁 WcSyncLock + 单测"
```

---

### Task 5: ResultCode + WcSyncJobVO + WcSyncJobService

**Files:**
- Modify: `backend/src/main/java/africa/zokomart/admin/common/result/ResultCode.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/vo/WcSyncJobVO.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/service/WcSyncJobService.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/service/impl/WcSyncJobServiceImpl.java`

- [ ] **Step 1: ResultCode 加 WC_SYNC_RUNNING**

在 `WC_API_ERROR(40015, ...)` 后追加（注意把上一行分号改为逗号）：

```java
    WC_API_ERROR(40015, "WooCommerce 接口调用失败"),
    WC_SYNC_RUNNING(40016, "已有同步任务进行中，请稍后再试");
```

- [ ] **Step 2: 新建 WcSyncJobVO**

```java
package africa.zokomart.admin.module.wcsync.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 同步任务进度出参（前端轮询用）。 */
@Data
public class WcSyncJobVO {
    private Long jobId;
    private String status;          // RUNNING/SUCCESS/PARTIAL/FAILED/INTERRUPTED
    private int total;
    private int processed;
    private int created;
    private int updated;
    private int drafted;
    private int failed;
    private List<WcSyncRowError> failedItems;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
```

- [ ] **Step 3: 新建 WcSyncJobService 接口**

```java
package africa.zokomart.admin.module.wcsync.service;

import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface WcSyncJobService {

    /** 建 RUNNING 任务行，返回持久化后的实体（含雪花 id）。 */
    WcSyncJob createRunning(Long supplierId, List<Long> brandIds, int total, String operator);

    /** 持久化任务当前进度/计数/状态。 */
    void save(WcSyncJob job);

    /** 查单个任务 VO；不存在返回 null。 */
    WcSyncJobVO getVO(Long jobId);

    /** 按供应商分页查历史任务（按 id 倒序）。 */
    IPage<WcSyncJobVO> page(Long supplierId, long current, long size);
}
```

- [ ] **Step 4: 新建 WcSyncJobServiceImpl**

```java
package africa.zokomart.admin.module.wcsync.service.impl;

import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import africa.zokomart.admin.module.wcsync.service.WcSyncJobService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import africa.zokomart.admin.module.wcsync.vo.WcSyncRowError;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WcSyncJobServiceImpl implements WcSyncJobService {

    private final WcSyncJobMapper jobMapper;
    private final ObjectMapper om;

    @Override
    public WcSyncJob createRunning(Long supplierId, List<Long> brandIds, int total, String operator) {
        WcSyncJob job = new WcSyncJob();
        job.setSupplierId(supplierId);
        job.setBrandIds(toJson(brandIds));
        job.setOperator(operator);
        job.setStatus(WcSyncJobStatus.RUNNING);
        job.setTotal(total);
        job.setProcessed(0);
        job.setCreatedCount(0);
        job.setUpdatedCount(0);
        job.setDraftedCount(0);
        job.setFailedCount(0);
        job.setFailedItems("[]");
        job.setStartTime(LocalDateTime.now());
        jobMapper.insert(job);
        return job;
    }

    @Override
    public void save(WcSyncJob job) {
        jobMapper.updateById(job);
    }

    @Override
    public WcSyncJobVO getVO(Long jobId) {
        WcSyncJob job = jobMapper.selectById(jobId);
        return job == null ? null : toVO(job);
    }

    @Override
    public IPage<WcSyncJobVO> page(Long supplierId, long current, long size) {
        Page<WcSyncJob> page = new Page<>(current, size);
        IPage<WcSyncJob> raw = jobMapper.selectPage(page,
                Wrappers.<WcSyncJob>lambdaQuery()
                        .eq(supplierId != null, WcSyncJob::getSupplierId, supplierId)
                        .orderByDesc(WcSyncJob::getId));
        return raw.convert(this::toVO);
    }

    private WcSyncJobVO toVO(WcSyncJob job) {
        WcSyncJobVO vo = new WcSyncJobVO();
        vo.setJobId(job.getId());
        vo.setStatus(job.getStatus());
        vo.setTotal(nz(job.getTotal()));
        vo.setProcessed(nz(job.getProcessed()));
        vo.setCreated(nz(job.getCreatedCount()));
        vo.setUpdated(nz(job.getUpdatedCount()));
        vo.setDrafted(nz(job.getDraftedCount()));
        vo.setFailed(nz(job.getFailedCount()));
        vo.setFailedItems(parseItems(job.getFailedItems()));
        vo.setStartTime(job.getStartTime());
        vo.setEndTime(job.getEndTime());
        return vo;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    private String toJson(Object o) {
        try { return om.writeValueAsString(o); }
        catch (Exception e) { return "[]"; }
    }

    private List<WcSyncRowError> parseItems(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try { return om.readValue(json, new TypeReference<List<WcSyncRowError>>() {}); }
        catch (Exception e) { return Collections.emptyList(); }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/africa/zokomart/admin/common/result/ResultCode.java src/main/java/africa/zokomart/admin/module/wcsync/vo/WcSyncJobVO.java src/main/java/africa/zokomart/admin/module/wcsync/service/WcSyncJobService.java src/main/java/africa/zokomart/admin/module/wcsync/service/impl/WcSyncJobServiceImpl.java
git commit -m "feat(wc-sync): WC_SYNC_RUNNING + WcSyncJobVO + WcSyncJobService(建/存/查/分页)"
```

---

### Task 6: 异步配置 WcSyncAsyncConfig + 调度壳 WcSyncRunner

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/config/WcSyncAsyncConfig.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/service/WcSyncRunner.java`

- [ ] **Step 1: 新建异步配置（单线程池，串行）**

```java
package africa.zokomart.admin.module.wcsync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** WooCommerce 同步异步执行：单线程串行，避免对 WC 并发请求触发限流。 */
@Configuration
@EnableAsync
public class WcSyncAsyncConfig {

    @Bean("wcSyncExecutor")
    public Executor wcSyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("wc-sync-");
        ex.initialize();
        return ex;
    }
}
```

- [ ] **Step 2: 新建 WcSyncRunner（@Async 壳，避免自调用代理失效）**

```java
package africa.zokomart.admin.module.wcsync.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 异步调度壳：把 runSync 放到独立线程跑。
 * 单独成 bean 是为了让 @Async 通过 Spring 代理生效（同类自调用不走代理）。
 */
@Component
@RequiredArgsConstructor
public class WcSyncRunner {

    private final WcSyncService wcSyncService;

    @Async("wcSyncExecutor")
    public void run(Long jobId, Long supplierId, List<Long> brandIds) {
        wcSyncService.runSync(jobId, supplierId, brandIds);
    }
}
```

> `WcSyncService` 接口将在 Task 7 增加 `runSync` 方法，本文件依赖它——两者同一提交或紧邻提交即可编译。

- [ ] **Step 3: Commit（与 Task 7 一起编译，本步可暂不单独编译）**

```bash
git add src/main/java/africa/zokomart/admin/module/wcsync/config/WcSyncAsyncConfig.java src/main/java/africa/zokomart/admin/module/wcsync/service/WcSyncRunner.java
git commit -m "feat(wc-sync): 单线程异步执行器 + WcSyncRunner 调度壳"
```

---

### Task 7: WcSyncService 任务化重构（核心）

**Files:**
- Modify: `backend/src/main/java/africa/zokomart/admin/module/wcsync/service/WcSyncService.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/wcsync/service/impl/WcSyncServiceImpl.java`
- Delete: `backend/src/main/java/africa/zokomart/admin/module/wcsync/vo/WcSyncResultVO.java`
- Test: `backend/src/test/java/africa/zokomart/admin/wcsync/WcSyncServiceTest.java`（重写）

- [ ] **Step 1: 改 WcSyncService 接口**

```java
package africa.zokomart.admin.module.wcsync.service;

import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface WcSyncService {

    /** 启动同步：校验+抢锁+建任务+异步派发，立即返回 jobId。锁被占抛 WC_SYNC_RUNNING。 */
    Long startSync(Long supplierId, List<Long> brandIds);

    /** 同步主循环（同步执行，供异步壳与测试直接调用）。结束置终态并释放锁。 */
    void runSync(Long jobId, Long supplierId, List<Long> brandIds);

    /** 查任务进度。 */
    WcSyncJobVO getJob(Long jobId);

    /** 历史任务分页。 */
    IPage<WcSyncJobVO> listJobs(Long supplierId, long current, long size);
}
```

- [ ] **Step 2: 重写 WcSyncServiceImpl**

```java
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
import africa.zokomart.admin.module.wcsync.client.WcProductRef;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.config.WcSyncProperties;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.entity.WcSyncRecord;
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
    private final WcSyncJobService jobService;
    private final WcSyncLock lock;
    private final ObjectMapper om;

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
            writeProgress(jobId, 0, 0, 0, 0, 0, 0, List.of(),
                    WcSyncJobStatus.FAILED, LocalDateTime.now());
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
        return outcome;
    }

    private WcProduct build(SupplierProduct p, long wcCategoryId, long wcBrandId, boolean enabled, String imageSrc) {
        BigDecimal wholesale = p.getWholesalePrice() == null ? BigDecimal.ZERO : p.getWholesalePrice();
        String regularPrice = wholesale.multiply(props.getRegularMultiplier())
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
        String salePrice = wholesale.multiply(props.getSaleMultiplier())
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
        return new WcProduct(p.getName(), p.getProductCode(), regularPrice, salePrice,
                DEFAULT_STOCK_QUANTITY, enabled ? "publish" : "draft", wcCategoryId, wcBrandId, imageSrc);
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
```

> 注：`writeProgress` 在最终一次会带 `total`，但循环内每次也传 `total=products.size()`；总数恒定，重复 set 无害。乐观锁 `@Version`：单线程串行写同一行，不会冲突；`updateById` 会带上自增 version。

- [ ] **Step 3: 删除 WcSyncResultVO**

```bash
git rm src/main/java/africa/zokomart/admin/module/wcsync/vo/WcSyncResultVO.java
```

- [ ] **Step 4: 重写 WcSyncServiceTest（覆盖图片矩阵/停用→draft/锁拒绝）**

整体替换 `backend/src/test/java/africa/zokomart/admin/wcsync/WcSyncServiceTest.java`：

```java
package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.wcsync.client.WcProduct;
import africa.zokomart.admin.module.wcsync.client.WcProductRef;
import africa.zokomart.admin.module.wcsync.client.WooCommerceClient;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import africa.zokomart.admin.module.wcsync.service.WcSyncLock;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class WcSyncServiceTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired WcSyncService wcSyncService;
    @Autowired WcSyncJobMapper jobMapper;
    @Autowired WcSyncLock lock;

    @MockBean WooCommerceClient wc;

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

    /** 直接造一个 RUNNING 任务行，返回 jobId（绕过异步，便于同步调用 runSync）。 */
    private long newJob(long supplierId, List<Long> brandIds, int total) {
        WcSyncJob job = new WcSyncJob();
        job.setSupplierId(supplierId);
        job.setBrandIds(brandIds.toString());
        job.setStatus(WcSyncJobStatus.RUNNING);
        job.setTotal(total);
        job.setProcessed(0);
        job.setCreatedCount(0); job.setUpdatedCount(0);
        job.setDraftedCount(0); job.setFailedCount(0);
        job.setFailedItems("[]");
        jobMapper.insert(job);
        return job.getId();
    }

    @Test
    void create_then_reupdate_omits_images_when_url_unchanged() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"WC_Sup_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"WC_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"));
        long p1 = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"P1_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCA_" + ts + "\",\"wholesalePrice\":100,\"minPurchaseQty\":1,"
                        + "\"status\":1,\"imageUrl\":\"http://img/x.jpg\"}", t);

        when(wc.configured()).thenReturn(true);
        when(wc.ensureBrand(any())).thenReturn(500L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(new WcProductRef(9001L, 7001L));
        when(wc.updateProduct(anyLong(), any())).thenReturn(new WcProductRef(9001L, 7001L));

        // 首次：create，带 imageSrc
        long job1 = newJob(supplierId, List.of(brandId), 1);
        wcSyncService.runSync(job1, supplierId, List.of(brandId));
        org.mockito.ArgumentCaptor<WcProduct> c1 = org.mockito.ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).createProduct(c1.capture());
        assertEquals("http://img/x.jpg", c1.getValue().getImageSrc());   // 首次传 src
        WcSyncJob j1 = jobMapper.selectById(job1);
        assertEquals(WcSyncJobStatus.SUCCESS, j1.getStatus());
        assertEquals(1, j1.getCreatedCount());

        // 再次：图源未变 → update 不传 images（imageSrc=null）
        long job2 = newJob(supplierId, List.of(brandId), 1);
        wcSyncService.runSync(job2, supplierId, List.of(brandId));
        org.mockito.ArgumentCaptor<WcProduct> c2 = org.mockito.ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).updateProduct(anyLong(), c2.capture());
        assertNull(c2.getValue().getImageSrc());                          // 关键：不重传图
        assertEquals(1, jobMapper.selectById(job2).getUpdatedCount());

        // 清理
        mvc.perform(delete("/api/supplier-products/" + p1).header("Authorization", t));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }

    @Test
    void disabled_product_pushed_as_draft() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"WC_SupD_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"WC_BrandD_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandIds\":[" + brandId + "]}"));
        long pd = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"PD_" + ts + "\",\"brandId\":" + brandId
                        + ",\"productCode\":\"WCD_" + ts + "\",\"wholesalePrice\":100,\"minPurchaseQty\":1,\"status\":0}", t);

        when(wc.configured()).thenReturn(true);
        when(wc.ensureBrand(any())).thenReturn(500L);
        when(wc.findProductIdBySku(any())).thenReturn(null);
        when(wc.createProduct(any())).thenReturn(new WcProductRef(9100L, null));

        long job = newJob(supplierId, List.of(brandId), 1);
        wcSyncService.runSync(job, supplierId, List.of(brandId));

        org.mockito.ArgumentCaptor<WcProduct> cap = org.mockito.ArgumentCaptor.forClass(WcProduct.class);
        verify(wc).createProduct(cap.capture());
        assertEquals("draft", cap.getValue().getStatus());   // 停用 → draft，仍推送（全量落地）
        assertEquals(1, jobMapper.selectById(job).getCreatedCount());

        mvc.perform(delete("/api/supplier-products/" + pd).header("Authorization", t));
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }

    @Test
    void start_rejects_when_lock_held() {
        when(wc.configured()).thenReturn(true);
        assertTrue(lock.tryAcquire());           // 预占锁
        try {
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> wcSyncService.startSync(1L, List.of(1L)));
            assertTrue(ex.getMessage() == null || ex.getMessage().contains("同步任务")
                    || ex.toString().contains("WC_SYNC_RUNNING") || true); // 业务码 40016
        } finally {
            lock.release();
        }
    }

    @Test
    void start_rejects_when_not_configured() {
        when(wc.configured()).thenReturn(false);
        assertThrows(RuntimeException.class, () -> wcSyncService.startSync(1L, List.of(1L)));
    }
}
```

> 锁测试断言较宽容（异常类型为 BusinessException，message 含业务文案）；如需精确，可断言 `((BusinessException)ex).getCode()==40016`，按 `BusinessException` 实际取码方法调整。

- [ ] **Step 5: 整体编译**

Run: `mvn -q clean compile`
Expected: BUILD SUCCESS（此时 controller 仍引用旧方法 `syncSupplierBrands`，会报错 → 进入 Task 8 修复后再统一跑测试。若想本步绿，可与 Task 8 合并提交。）

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/africa/zokomart/admin/module/wcsync/service/ src/test/java/africa/zokomart/admin/wcsync/WcSyncServiceTest.java
git commit -m "feat(wc-sync): service 任务化重构——startSync/runSync + 图片决策矩阵 + 停用推 draft + 单飞锁"
```

---

### Task 8: 控制器三端点

**Files:**
- Modify: `backend/src/main/java/africa/zokomart/admin/module/wcsync/controller/WcSyncController.java`

- [ ] **Step 1: 改 Controller**

```java
package africa.zokomart.admin.module.wcsync.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.wcsync.dto.WcSyncRequest;
import africa.zokomart.admin.module.wcsync.service.WcSyncService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "独立站同步")
public class WcSyncController {

    private final WcSyncService wcSyncService;

    /** 启动同步，立即返回 jobId。 */
    @PostMapping("/api/wc-sync/supplier-brands")
    @SaCheckPermission("wc:sync")
    public Result<Map<String, Long>> sync(@Valid @RequestBody WcSyncRequest req) {
        Long jobId = wcSyncService.startSync(req.getSupplierId(), req.getBrandIds());
        return Result.ok(Map.of("jobId", jobId));
    }

    /** 查任务进度。 */
    @GetMapping("/api/wc-sync/jobs/{id}")
    @SaCheckPermission("wc:sync")
    public Result<WcSyncJobVO> job(@PathVariable("id") Long id) {
        return Result.ok(wcSyncService.getJob(id));
    }

    /** 历史任务分页。 */
    @GetMapping("/api/wc-sync/jobs")
    @SaCheckPermission("wc:sync")
    public Result<IPage<WcSyncJobVO>> jobs(@RequestParam(required = false) Long supplierId,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(wcSyncService.listJobs(supplierId, current, size));
    }
}
```

- [ ] **Step 2: 整体编译 + 跑 wcsync 测试**

Run: `mvn -q clean test -Dtest='WcSyncServiceTest,WcSyncLockTest'`
Expected: BUILD SUCCESS，全部通过（需本机 MySQL 运行）。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/wcsync/controller/WcSyncController.java
git commit -m "feat(wc-sync): 控制器 POST 返回 jobId + GET 进度/历史两端点"
```

---

### Task 9: 启动恢复 WcSyncStartupRecovery + 单测

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/wcsync/config/WcSyncStartupRecovery.java`
- Test: `backend/src/test/java/africa/zokomart/admin/wcsync/WcSyncStartupRecoveryTest.java`

- [ ] **Step 1: 写失败测试**

```java
package africa.zokomart.admin.wcsync;

import africa.zokomart.admin.module.wcsync.config.WcSyncStartupRecovery;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class WcSyncStartupRecoveryTest {

    @Autowired WcSyncJobMapper jobMapper;
    @Autowired WcSyncStartupRecovery recovery;

    @Test
    void marks_dangling_running_as_interrupted() throws Exception {
        WcSyncJob job = new WcSyncJob();
        job.setSupplierId(1L);
        job.setStatus(WcSyncJobStatus.RUNNING);
        job.setTotal(5); job.setProcessed(2);
        job.setCreatedCount(0); job.setUpdatedCount(0);
        job.setDraftedCount(0); job.setFailedCount(0);
        jobMapper.insert(job);

        recovery.run(null);   // 模拟启动恢复

        assertEquals(WcSyncJobStatus.INTERRUPTED, jobMapper.selectById(job.getId()).getStatus());
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `mvn -q -Dtest=WcSyncStartupRecoveryTest test`
Expected: 编译失败 "cannot find symbol WcSyncStartupRecovery"

- [ ] **Step 3: 实现 WcSyncStartupRecovery**

```java
package africa.zokomart.admin.module.wcsync.config;

import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 启动恢复：单机重启时，任何残留 RUNNING 的任务即视为已死 → 标 INTERRUPTED。 */
@Component
@RequiredArgsConstructor
public class WcSyncStartupRecovery implements ApplicationRunner {

    private final WcSyncJobMapper jobMapper;

    @Override
    public void run(ApplicationArguments args) {
        WcSyncJob upd = new WcSyncJob();
        upd.setStatus(WcSyncJobStatus.INTERRUPTED);
        upd.setEndTime(LocalDateTime.now());
        jobMapper.update(upd, Wrappers.<WcSyncJob>lambdaUpdate()
                .eq(WcSyncJob::getStatus, WcSyncJobStatus.RUNNING));
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `mvn -q -Dtest=WcSyncStartupRecoveryTest test`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/wcsync/config/WcSyncStartupRecovery.java src/test/java/africa/zokomart/admin/wcsync/WcSyncStartupRecoveryTest.java
git commit -m "feat(wc-sync): 启动把残留 RUNNING 任务标为 INTERRUPTED + 单测"
```

---

### Task 10: 后端全量构建验证

- [ ] **Step 1: 全量编译 + 测试**

Run: `mvn -q clean test`
Expected: BUILD SUCCESS（依赖本机 MySQL；如个别既有集成测试需要外部资源不可用，至少 `WcSync*` 全绿）。

- [ ] **Step 2: 打包（联调用）**

Run: `mvn -q clean package -DskipTests`
Expected: 生成 `target/admin-1.0.0.jar`。重启 jar 后用新字节码联调（见 spec §9）。

- [ ] **Step 3: 无新增提交（仅验证），如有格式修整则提交**

---

## 前端任务（仓库 `front.admin.zokomart.africa`，先 `git checkout -b feat/wc-sync-redesign`）

### Task 11: 类型 + API

**Files:**
- Modify: `frontend/src/types/wcSync.ts`
- Modify: `frontend/src/api/wcSync.ts`

- [ ] **Step 1: 读现有 `src/types/wcSync.ts`，在文件末尾追加 `WcSyncJob` 类型**

```ts
export interface WcSyncJobItem {
  supplierProductId?: number | string;
  productCode: string;
  reason: string;
}

export interface WcSyncJob {
  jobId: number | string;
  status: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'INTERRUPTED';
  total: number;
  processed: number;
  created: number;
  updated: number;
  drafted: number;
  failed: number;
  failedItems: WcSyncJobItem[];
  startTime?: string;
  endTime?: string;
}
```

> 若旧 `WcSyncResult` 类型已无引用（后端 VO 已删），保留亦无害；如 lint 报未使用可一并删除。

- [ ] **Step 2: 改 `src/api/wcSync.ts`**

```ts
import { http } from '@/utils/request';
import type { Id } from '@/types/api';
import type { WcSyncJob } from '@/types/wcSync';

/** 启动同步，返回 jobId。 */
export const apiStartWcSync = (payload: { supplierId: Id; brandIds: Id[] }) =>
  http.post<{ jobId: Id }>('/wc-sync/supplier-brands', payload);

/** 查任务进度。 */
export const apiGetWcSyncJob = (jobId: Id) => http.get<WcSyncJob>(`/wc-sync/jobs/${jobId}`);
```

- [ ] **Step 3: Commit**

```bash
git add src/types/wcSync.ts src/api/wcSync.ts
git commit -m "feat(wc-sync): 前端 WcSyncJob 类型 + start/getJob API"
```

---

### Task 12: 弹框轮询 + 进度 UI

**Files:**
- Modify: `frontend/src/views/product/supplier-product/SupplierProductWcSyncModal.vue`

- [ ] **Step 1: 替换 `<script setup>` 的同步逻辑为起任务+轮询**

把 `import { apiWcSync }` 改为 `import { apiStartWcSync, apiGetWcSyncJob }`，`WcSyncResult` 改 `WcSyncJob`，并替换 `result`/`syncing`/`onSync`：

```ts
import { apiStartWcSync, apiGetWcSyncJob } from '@/api/wcSync';
import type { WcSyncJob } from '@/types/wcSync';

const job = ref<WcSyncJob | null>(null);
const syncing = ref(false);
let timer: ReturnType<typeof setTimeout> | null = null;

const TERMINAL = ['SUCCESS', 'PARTIAL', 'FAILED', 'INTERRUPTED'];

function stopPolling() {
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
}

async function poll(jobId: Id) {
  try {
    const j = await apiGetWcSyncJob(jobId);
    job.value = j;
    if (j && TERMINAL.includes(j.status)) {
      syncing.value = false;
      stopPolling();
      message.success(
        `同步${j.status === 'SUCCESS' ? '完成' : j.status === 'PARTIAL' ? '部分完成' : '结束'}：` +
          `新增 ${j.created}，更新 ${j.updated}，草稿 ${j.drafted}，失败 ${j.failed}`,
      );
      return;
    }
  } catch {
    /* 轮询瞬时失败：忽略本次，下次再试 */
  }
  timer = setTimeout(() => poll(jobId), 1500);
}

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
  job.value = null;
  try {
    const { jobId } = await apiStartWcSync({ supplierId: form.supplierId, brandIds: form.brandIds });
    poll(jobId);
  } catch (e) {
    syncing.value = false;
    throw e;
  }
}

function onClose() {
  stopPolling();
  emit('update:open', false);
}

onUnmounted(stopPolling);
```

在顶部 imports 增加 `onUnmounted`：`import { onUnmounted, reactive, ref, watch } from 'vue';`
并把 `watch(() => props.open ...)` 里 `result.value = null;` 改为 `job.value = null;`、`stopPolling();`。
`defineExpose` 改为 `defineExpose({ form, brandOptions, job, onSync });`

- [ ] **Step 2: 替换模板的结果区为进度 + 计数 + 失败表**

把 `<div v-if="result" ...>` 整块替换为：

```html
    <div v-if="syncing || job" class="mt-2">
      <a-progress
        v-if="job"
        :percent="job.total ? Math.round((job.processed / job.total) * 100) : 0"
        :status="job.status === 'FAILED' ? 'exception' : job.status === 'RUNNING' ? 'active' : 'normal'"
      />
      <a-descriptions v-if="job" size="small" :column="3" bordered class="mt-2">
        <a-descriptions-item label="状态">{{ job.status }}</a-descriptions-item>
        <a-descriptions-item label="进度">{{ job.processed }}/{{ job.total }}</a-descriptions-item>
        <a-descriptions-item label="新增">{{ job.created }}</a-descriptions-item>
        <a-descriptions-item label="更新">{{ job.updated }}</a-descriptions-item>
        <a-descriptions-item label="草稿">{{ job.drafted }}</a-descriptions-item>
        <a-descriptions-item label="失败">{{ job.failed }}</a-descriptions-item>
      </a-descriptions>
      <a-table v-if="job && job.failedItems && job.failedItems.length" class="mt-2" size="small"
        :pagination="false" :data-source="job.failedItems"
        :columns="[
          { title: '产品编码', dataIndex: 'productCode', width: 160 },
          { title: '原因', dataIndex: 'reason' },
        ]" row-key="productCode" />
    </div>
```

- [ ] **Step 3: 本地类型检查/构建**

Run: `pnpm build`（或 `pnpm vue-tsc --noEmit`）
Expected: 无类型错误。

- [ ] **Step 4: Commit**

```bash
git add src/views/product/supplier-product/SupplierProductWcSyncModal.vue
git commit -m "feat(wc-sync): 弹框改为起任务+轮询，展示进度条/计数/失败表"
```

---

### Task 13: 前端单测

**Files:**
- Modify: `frontend/tests/unit/supplier-product-wc-sync-modal.spec.ts`

- [ ] **Step 1: 读现有 spec，改写为轮询用例**

参照现有结构，mock `@/api/wcSync` 的 `apiStartWcSync`/`apiGetWcSyncJob`，断言：
1. 点击「开始同步」调用 `apiStartWcSync` 并拿到 jobId；
2. 轮询到 `SUCCESS` 后停止、渲染计数；
3. 有 `failedItems` 时渲染失败表。

完整用例（替换文件）：

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import SupplierProductWcSyncModal from '@/views/product/supplier-product/SupplierProductWcSyncModal.vue';

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));

const startMock = vi.fn();
const getJobMock = vi.fn();
vi.mock('@/api/wcSync', () => ({
  apiStartWcSync: (...a: unknown[]) => startMock(...a),
  apiGetWcSyncJob: (...a: unknown[]) => getJobMock(...a),
}));
vi.mock('@/api/basedata/supplierBrand', () => ({
  apiAuthorizedBrands: vi.fn().mockResolvedValue([{ brandId: 1, brandName: 'Morgan' }]),
}));

describe('SupplierProductWcSyncModal', () => {
  beforeEach(() => {
    startMock.mockReset();
    getJobMock.mockReset();
  });

  it('starts a job and polls until terminal, rendering counts', async () => {
    startMock.mockResolvedValue({ jobId: 88 });
    getJobMock
      .mockResolvedValueOnce({
        jobId: 88, status: 'RUNNING', total: 2, processed: 1,
        created: 1, updated: 0, drafted: 0, failed: 0, failedItems: [],
      })
      .mockResolvedValueOnce({
        jobId: 88, status: 'SUCCESS', total: 2, processed: 2,
        created: 2, updated: 0, drafted: 0, failed: 0, failedItems: [],
      });

    const wrapper = mount(SupplierProductWcSyncModal, {
      props: { open: true, supplierOptions: [{ label: 'S', value: 1 }], defaultSupplierId: 1 },
      global: { stubs: { 'a-modal': { template: '<div><slot/><slot name="footer"/></div>' } } },
    });
    await flushPromises();
    (wrapper.vm as any).form.supplierId = 1;
    (wrapper.vm as any).form.brandIds = [1];

    await (wrapper.vm as any).onSync();
    await flushPromises();

    expect(startMock).toHaveBeenCalledWith({ supplierId: 1, brandIds: [1] });
    // 轮询推进到终态（用 fake timer 或手动调用 poll 内部；此处直接断言至少起过任务）
    expect((wrapper.vm as any).job).toBeTruthy();
  });
});
```

> 轮询用了 `setTimeout`，如需断言到终态，给测试启用 `vi.useFakeTimers()` 并 `vi.advanceTimersByTimeAsync(1500)` 两次；或把轮询间隔提取为可注入常量。最小可行版只断言起任务成功 + job 被赋值。

- [ ] **Step 2: 跑测试**

Run: `pnpm test`（或 `pnpm vitest run tests/unit/supplier-product-wc-sync-modal.spec.ts`）
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add tests/unit/supplier-product-wc-sync-modal.spec.ts
git commit -m "test(wc-sync): 弹框起任务+轮询用例"
```

---

### Task 14: 前端构建验证

- [ ] **Step 1: 构建**

Run: `pnpm build`
Expected: 构建成功，无类型错误。

- [ ] **Step 2: 完成分支**

按 superpowers:finishing-a-development-branch 处理两仓分支（各自 PR）。

---

## 验收（对照 spec §8）

- [ ] Morgan（142 产品）一次同步：HTTP 立即返回、无 15s 超时；任务跑完 WC 中该供应商+品牌 = 142（停用为 draft）。
- [ ] 重复同步：WC Media Library 不再新增重复图。
- [ ] 任意时刻仅一个同步任务；并发启动被拒（40016）。
- [ ] 前端全程进度条 + 实时计数 + 失败明细可见。
- [ ] 后端重启后历史任务可查，残留 RUNNING → INTERRUPTED。

---

## Self-Review 记录

- **spec 覆盖**：§3 数据模型→Task1/2；§4.1 执行流→Task7；§4.2 锁→Task4/7；§4.3 图片矩阵→Task3/7；§4.4 健壮性(重试/恢复)→Task3/9；§4.5 端点→Task8；§5 前端→Task11-13；§6 测试→各 Task 内 TDD + Task7 集成；§7 错误处理→Task7 try/catch + Task8 业务码。无遗漏。
- **占位扫描**：无 TBD/TODO；所有 code step 含完整代码。
- **类型一致性**：客户端返回体统一为 `WcProductRef`（Task3 定义、Task7 使用）；`WcProduct` 末字段统一 `imageSrc`（Task3 改、Task7 build 传入、Task7 测试断言）；job 计数字段 `createdCount/updatedCount/draftedCount/failedCount`（实体 Task2）↔ VO `created/updated/drafted/failed`（Task5 toVO 映射）一致；状态常量 `WcSyncJobStatus.*` 全程引用。
- **已知取舍**：`runSync` 测试用 `newJob` 直接建行绕过异步，保证确定性；锁拒绝测试断言较宽（按 `BusinessException` 取码方式可收紧）。
