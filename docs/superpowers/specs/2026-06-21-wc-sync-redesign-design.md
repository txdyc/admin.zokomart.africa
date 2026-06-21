# WooCommerce 全量同步重设计（异步任务化 + 图片幂等）

- 日期：2026-06-21
- 范围：后端 `admin.zokomart.africa`（核心改造 + 迁移 V14） + 前端 `front.admin.zokomart.africa`（同步弹框轮询化）
- 入口：平台目录 → 供应商产品 → 「同步到独立站」（权限 `wc:sync`）

## 1. 背景与问题

后台有「同步到独立站」特性：把选定供应商+品牌的供应商产品**单向推送**到 WooCommerce 独立站（走 WC REST API，不改 WP 代码）。Morgan 品牌下 142 个产品在使用中暴露三个生产问题：

1. **`timeout of 15000ms exceeded`**：前端 axios 全局超时写死 15s（`frontend/src/utils/request.ts`），后端 `POST /api/wc-sync/supplier-brands` 在**单个 HTTP 请求线程内串行跑完全部产品**才返回（`WcSyncServiceImpl.syncSupplierBrands`）。每个产品要发多次 WC REST 调用，且 `images` 用 `src` 传时 WC 服务器要现场下载图片，单产品常达数百 ms~数秒。142 个 ≫ 15s，浏览器必然在 15 秒掐断，后端却仍在继续跑。
2. **142 → 只到约 105**：前端掐断后用户重试，后端第一趟仍在跑并写 WC + `wc_sync_record`，重试又起**第二趟并发全量循环**，两趟在同一批 SKU 上抢 create（`findProductIdBySku` 存在 TOCTOU 窗口），永远跑不到"干净的 142 全完成"。`failed` 产品不自动重试且因请求掐断用户看不到错误表；"停用且从未同步→跳过"分支又吃掉一部分。叠加 ≈ 37 缺口。
3. **Media Library 图片重复**：`toJson` 在 create 和 update **都**无条件塞 `images:[{src: imageUrl}]`（`WooCommerceClientImpl.toJson`）。WC 收到 `src`（而非已有图片 `id`）时**每次都重新下载并新建一个 media 附件**。每次重同步 / 每次超时重试都再 sideload 一遍 → 重复。

三个问题同根：**同步是阻塞式一锤子、未任务化、图片处理无幂等、无并发保护**。本设计一并解决。

## 2. 已确认的决策

| 决策点 | 选定 |
| --- | --- |
| 停用产品 | **全部推送**，停用的以 `draft` 状态落地（去掉"停用且未同步→跳过"分支），全量 142 都在 WC 建出来 |
| 执行架构 | **方案 A**：后端异步任务 + 前端轮询进度，立即返回 `jobId`，根治 15s 超时 |
| 任务持久化 | 持久化到 **`wc_sync_job`** 表，可查历史、重启不丢 |
| 存量重复图 | **只保证今后不再重复**，历史堆积的重复 media 先不动（不做破坏性删除） |
| 单飞锁 | **进程内 `AtomicBoolean` 全局单飞**（后端为单机单 jar 运行、无应用级 Redis 接线，故用进程内锁最简最稳；行为=任意时刻仅一个同步任务） |
| 线程池 | 单任务串行跑（对 WC 友好、避免限流） |
| 失败明细 | 存 `wc_sync_job.failed_items`(JSON) 列，不另建子表（量有界） |

## 3. 数据模型

### 3.1 新增表 `wc_sync_job`（迁移 V14）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK | 雪花 id |
| `supplier_id` | BIGINT | 供应商 |
| `brand_ids` | VARCHAR(255) | 选中的品牌 id，JSON 数组字符串，如 `[1,2,3]` |
| `operator` | VARCHAR(64) | 触发人（Sa-Token loginId） |
| `status` | VARCHAR(16) | RUNNING / SUCCESS / PARTIAL / FAILED / INTERRUPTED |
| `total` | INT | 本次任务产品总数 |
| `processed` | INT | 已处理数（驱动进度条） |
| `created_count` | INT | 新建数 |
| `updated_count` | INT | 更新数 |
| `drafted_count` | INT | 推为草稿数（停用产品） |
| `failed_count` | INT | 失败数 |
| `failed_items` | TEXT | JSON：`[{"productCode":"...","error":"..."}]`，封顶 200 条 |
| `start_time` | DATETIME | |
| `end_time` | DATETIME | 终态时写入 |
| 审计字段 | | `create_time/update_time/...` 走 BaseEntity 自动填充；逻辑删除 `deleted` |

终态判定：`failed_count == 0` → SUCCESS；`0 < failed_count < total` → PARTIAL；`failed_count == total`（或任务级未捕获异常）→ FAILED。

### 3.2 扩展 `wc_sync_record`（迁移 V14，图片幂等核心）

```sql
ALTER TABLE wc_sync_record
  ADD COLUMN wc_image_id      BIGINT       DEFAULT NULL COMMENT 'WC 主图 media 附件 id',
  ADD COLUMN synced_image_url VARCHAR(512) DEFAULT NULL COMMENT '上次 sideload 的源图 URL';
```

`lastStatus` 取值扩充：去掉对"停用→跳过"的依赖，停用走 `DRAFTED`。

### 3.3 迁移文件

`V14__wc_sync_job_and_image_dedup.sql`：建 `wc_sync_job` 表 + 给 `wc_sync_record` 加两列。无需新增菜单/权限（沿用 `wc:sync` / 菜单 2065）。

## 4. 后端设计

包：`africa.zokomart.admin.module.wcsync`

### 4.1 执行流

**`POST /api/wc-sync/supplier-brands`（语义变更：立即返回 jobId）**
1. 校验 WC 已配置、供应商存在、品牌非空。
2. 抢进程内单飞锁 `WcSyncLock.tryAcquire()`（`AtomicBoolean.compareAndSet(false,true)`）。被占 → 抛业务码 `WC_SYNC_RUNNING`（"已有同步任务进行中"）。
3. 统计 total，建 `wc_sync_job` 行（status=RUNNING, start_time=now）。
4. 提交给专用线程池 `@Async` 执行 `runSyncAsync(jobId, supplierId, brandIds)`（内部调同步的 `runSync`）。
5. **立即返回 `{ jobId }`**。

**`runSync`（@Async，串行循环）**
- 查出供应商 + brandIds 下全部产品。
- 逐个产品 `upsertOne(p, job)`：
  - `enabled = p.status == 1`；WC `status = enabled ? "publish" : "draft"`。
  - 解析 `wcId`（先查 `wc_sync_record`，无则 `findProductIdBySku`）。
  - 解析 WC 分类（父→子）、品牌（原生 brands），逻辑同现状（带缓存）。
  - **图片决策（见 4.3）**决定 `images` 字段如何传。
  - create / update；从返回中抓 `images[0].id` 回写 `wc_image_id` + `synced_image_url`。
  - 计数：CREATED / UPDATED / DRAFTED；失败进 catch → `failed_count++` + `failed_items` 追加 `{productCode, error}` + 记录 FAILED。
  - 每个产品后 `processed++` 并持久化 job 进度（保证轮询可见实时进度）。
- 结束：按 4.1 规则置终态 + end_time；`finally` 释放锁。
- **去掉**"停用且未同步→跳过"分支：不再有 SKIPPED 计数。

### 4.2 单飞锁

- `WcSyncLock` 组件包一个 `AtomicBoolean`，`tryAcquire()`=`compareAndSet(false,true)`，`release()` 置回 false。
- `startSync` 抢锁，`runSync` 的 `finally` 释放（即便 startSync 抢锁后 runSync 由 startSync 在 finally 释放——锁由"任务生命周期"持有：startSync 抢、异步任务结束时释放）。
- 与"线程池单任务"叠加，确保任意时刻只有一个同步在跑，根治问题 2 的并发重试。
- 单机单 jar 运行，进程内锁即足够；无需 Redis。重启后 JVM 全新、锁自然为 false。

### 4.3 图片幂等（问题 3 根治）

`WooCommerceClient` 改造：`createProduct/updateProduct` 解析返回的 `images[0].id` 一并回传 —— 新增轻量返回体 `WcUpsertResult{ long productId; Long imageId; }`。`toJson` 的 `images` 改为由 service 显式控制（传 src / 传 id / 不传）。

service 决策矩阵（以 `wc_sync_record` 状态为准）：

| 记录状态 | imageUrl | 动作 |
| --- | --- | --- |
| `imageUrl` 为空 | — | 不传 `images` |
| 有 `wc_image_id` 且 `synced_image_url == imageUrl` | 未变 | **不传 `images`**（WC 不重下，杜绝重复） |
| 有 `wc_image_id` 但 `synced_image_url != imageUrl` | 变了 | 传 `images:[{src:newUrl}]`，回写新 id + url |
| 无 `wc_image_id`，产品本次新建 | 有 | 传 `images:[{src:imageUrl}]`，回写 id + url |
| 无 `wc_image_id`，产品已存在（历史脏记录） | 有 | 先 `GET` 该产品读现有主图 id **收编**（回写 id + url），**不重复上传** |

辅助方法：`WooCommerceClient.findProductMainImageId(wcProductId)` 供"收编"使用。

> 存量已重复的 media 不在本次清理范围（决策：只保证今后不再产生）。

### 4.4 健壮性

- **客户端重试**：`WooCommerceClientImpl.send` 对网络超时 / IOException 做 1 次重试（业务级 4xx/5xx 不重试）。connectTimeout 保持 10s，单调用读超时保持 20s。
- **重启恢复**：`ApplicationRunner` 启动时把残留 `RUNNING`（单机重启即视为已死）批量标为 `INTERRUPTED`。（进程内锁随 JVM 重启自动复位，无需额外清理。）
- 不记录 WC `consumer-secret` / token 到日志。

### 4.5 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/wc-sync/supplier-brands` | 启动任务，返回 `{ jobId }`（语义变更） |
| GET | `/api/wc-sync/jobs/{id}` | 任务进度+计数+失败明细（轮询用） |
| GET | `/api/wc-sync/jobs?supplierId=` | 历史任务列表（分页） |

全部 `@SaCheckPermission("wc:sync")`。返回统一 `Result<T>`，异常走全局处理。

### 4.6 新增/改动文件清单（后端）

- 新增：`entity/WcSyncJob`、`mapper/WcSyncJobMapper`、`service/WcSyncJobService(+impl)`、`vo/WcSyncJobVO`、`client/WcUpsertResult`、`service/WcSyncLock`(进程内单飞锁)、`config/WcSyncAsyncConfig`(线程池+@EnableAsync)、`config/WcSyncStartupRecovery`(ApplicationRunner)、`db/migration/V14__...sql`。
- 改动：`WcSyncServiceImpl`（任务化 + 锁 + 图片决策 + 去 skip）、`WooCommerceClientImpl`（返回 imageId、条件 images、findProductMainImageId、重试）、`WooCommerceClient` 接口、`WcProduct`（imageUrl 字段保留，images 控制上移）、`WcSyncRecord`(+2 字段)、`WcSyncController`(+2 端点，POST 改返回 jobId)、`ResultCode`(+`WC_SYNC_RUNNING`)。

## 5. 前端设计

仓库 `front.admin.zokomart.africa`，文件 `src/views/product/supplier-product/SupplierProductWcSyncModal.vue` + `src/api/wcSync.ts`。

- `apiWcSync` → `apiStartWcSync(payload)` 返回 `{ jobId }`；新增 `apiGetWcSyncJob(jobId)`。
- 弹框「开始同步」流程：启动 → 拿 jobId → 每 ~1.5s 轮询 → 渲染**进度条（processed/total）+ 实时计数 + 失败表**；终态（SUCCESS/PARTIAL/FAILED/INTERRUPTED）停轮询并提示。
- 运行中允许关闭弹框（任务持久化，重开可继续看进度）；"重试失败"= 重新点同步（天然幂等，已同步且图未变的会跳过重传图）。
- 不改 axios 全局 15s 超时——本链路每次请求都很小，不再触发。
- 类型：`src/types/wcSync.ts` 增加 `WcSyncJob`（status/total/processed/各计数/failedItems/时间）。

## 6. 测试

**后端（JUnit + Mock `WooCommerceClient`）**
- 图片决策矩阵三态：源未变→不传 images；源变→传新 src 并回写 id；新建→传 src 回写 id；历史脏记录→收编现有 id 不重传。
- 停用产品 → `draft` + drafted_count；不再产生 SKIPPED。
- 任务计数与终态判定（全成功=SUCCESS、部分失败=PARTIAL、全失败=FAILED）。
- 单飞锁：`WcSyncLock` 连续 `tryAcquire` 两次第二次返回 false；锁被持有时 `startSync` 抛 `WC_SYNC_RUNNING`。
- 重启恢复：启动时 RUNNING → INTERRUPTED。
- `WooCommerceClientImpl.toJson`：omit images 时不出现 `images` 字段。

**前端（Vitest，仿现有 `tests/unit/supplier-product-wc-sync-modal.spec.ts`）**
- start 返回 jobId；轮询渲染进度条+计数；终态渲染最终结果与失败表；终态停止轮询。

## 7. 错误处理

- 单产品异常 try/catch 不中断全局 → failed_count + failed_items + 记录 FAILED。
- 任务级未捕获异常 → status=FAILED + end_time + `finally` 释放锁。
- 锁竞争 → 业务码 `WC_SYNC_RUNNING`，前端提示"已有同步任务进行中"。
- WC 配置缺失 → 沿用 `WC_NOT_CONFIGURED`。

## 8. 验收标准

1. 对 Morgan（142 产品）点一次同步：HTTP 立即返回、无 15s 超时；任务跑完后 WC 中该供应商+品牌产品数 = 142（停用的为 draft）。
2. 连点 / 重复同步：WC Media Library **不再新增**重复图（同源图复用既有附件）。
3. 任意时刻只有一个同步任务在跑；并发启动被拒。
4. 前端全程可见进度条与实时计数，失败项可见明细。
5. 后端重启后历史任务可查，残留 RUNNING 被标为 INTERRUPTED。

## 9. 部署注意

- 联调前在 `backend/src/main/resources/application-local.yml` 配 `app.wc.base-url/consumer-key/consumer-secret`（密钥红线：禁入 tracked 文件）。
- 后端以打包 jar 运行：改完代码 `mvn clean package -DskipTests` 后重启 jar，确认跑新字节码（否则新端点 500）。
- WC 原生 Brands 需 WooCommerce 9.6+；图片源需 WP 公网可达、站点 HTTPS。
- 两仓各自独立提交：后端改后端仓、前端改前端仓，不跨仓混提交。
