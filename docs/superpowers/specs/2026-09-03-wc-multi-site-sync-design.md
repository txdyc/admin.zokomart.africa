# 多站点 WooCommerce 同步设计（新增 kianosmart.africa）

- 日期：2026-09-03
- 范围：后端 `admin.zokomart.africa`（配置 / 数据模型 V22 / 客户端工厂 / 同步逻辑 / API） + 前端 `front.admin.zokomart.africa`（站点多选 + 多任务进度）
- 入口：平台目录 → 供应商产品 → 「同步到独立站」（权限 `wc:sync`，沿用）

## 1. 背景与目标

现有「同步到独立站」把供应商产品单向推送到**唯一一个** WooCommerce 站点。现在要新增第二个站点
**https://kianosmart.africa**，把**全部产品**也同步过去。

已验证：kianosmart.africa 是 WordPress + WooCommerce，HTTPS，`/wp-json/wc/v3/products`、
`products/categories`、`products/brands` 均返回 **401**（端点存在、需鉴权），即 WooCommerce 版本
支持**原生 Brands**（9.6+）。因此**现有客户端逻辑无需改造即可对接**，只需给它独立的密钥。

真正的问题不是 WC 兼容性，而是**"站点"这个维度在系统里根本不存在**。三处硬编码了单站点假设：

| 位置 | 现状 | 为什么必须改 |
| --- | --- | --- |
| `app.wc.*` | 扁平 base-url/consumer-key/consumer-secret | 无法表达第二套凭证 |
| `wc_sync_record` | 主键 `supplier_product_id`，单个 `wc_product_id`/`wc_image_id`/`synced_image_url` | WC 商品 id 与 media id **是站点私有的** |
| `ad_product_image.wc_media_id` | 单列 | 同上，广告图 media id 也是站点私有的 |

**若不加站点维度直接同步第二站，会发生数据损坏**：
- 产品映射：kianosmart 的 `wc_product_id` 会覆盖 zokomart 的映射；下次同步 zokomart 时按错误 id 更新商品。
- 主图：`synced_image_url`/`wc_image_id` 被另一站覆盖 → 系统认为"图变了" → **重新 sideload**，
  正是刚修复掉的 Media Library 重复图问题复发。
- 广告图：zokomart 存下 media id 500 后，推 kianosmart 时会发 `WcImage(500)`——500 在
  kianosmart 上是**另一张或不存在的**附件 → 错图或报错。

**目标**：把"站点"提升为一等维度，使同一套代码对 N 个站点都正确；每站拥有独立的商品 id、
主图 media id、广告图 media id 与同步任务，互不干扰。

## 2. 已确认的决策

| 决策点 | 选定 |
| --- | --- |
| 方案 | **A：站点作为一等维度**（否决"硬编码第二站"与"复制模块"） |
| 目标站点选择 | 弹框内**多选**，默认全选；可单独重跑某一站 |
| 价格规则 | **每站可覆盖** `regular/sale` 倍率，不填则继承全局（1.75 / 1.5） |
| 广告图 | **两站都带**广告图 → 广告图 media id 必须按站点存 |
| 站点来源 | **配置**（非数据库表）：密钥不入库的红线；加站点是部署期动作 |
| 旧扁平配置 | **不做向后兼容 shim**；`sites` 为空时沿用 `WC_NOT_CONFIGURED`（可见且无损的失败） |

## 3. 配置模型

```yaml
app:
  wc:
    regular-multiplier: 1.75      # 全局默认
    sale-multiplier: 1.5
    public-file-base-url: ${WC_PUBLIC_FILE_BASE_URL:}   # 仍全局：是"本机文件的公网可达地址"，与站点无关
    sites:
      - code: zokomart
        name: ZokoMart
        base-url: ${WC_BASE_URL:}
        consumer-key: ${WC_CONSUMER_KEY:}
        consumer-secret: ${WC_CONSUMER_SECRET:}
      - code: kianosmart
        name: KianoSmart
        base-url: ${WC_KIANO_BASE_URL:}
        consumer-key: ${WC_KIANO_CONSUMER_KEY:}
        consumer-secret: ${WC_KIANO_CONSUMER_SECRET:}
        # regular-multiplier / sale-multiplier 可选覆盖
```

- `WcSyncProperties`：保留全局 `regularMultiplier`/`saleMultiplier`/`publicFileBaseUrl`，新增 `List<WcSite> sites`。
- `WcSite`：`code`/`name`/`baseUrl`/`consumerKey`/`consumerSecret` + 可空的 `regularMultiplier`/`saleMultiplier`。
- **倍率回退只实现一处**：`effectiveRegularMultiplier(site)` / `effectiveSaleMultiplier(site)`
  （站点值非空取站点值，否则取全局），避免回退规则在多处漂移。
- **`code` 是写入数据库的稳定标识，一经使用不可改名**（改名会让既有映射变孤儿）。
- **红线：真实 key/secret 只放 `application-local.yml`（已 gitignore）**；`application.yml` 只留 `${...:}` 占位。

## 4. 数据模型与迁移 V22

> 现有迁移已到 V21，本次为 **V22**。

```sql
-- 1) 产品映射：每站一行
ALTER TABLE wc_sync_record
    ADD COLUMN site_code VARCHAR(32) NOT NULL DEFAULT 'zokomart' COMMENT '目标站点 code',
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (supplier_product_id, site_code);
ALTER TABLE wc_sync_record ALTER COLUMN site_code DROP DEFAULT;

-- 2) 同步任务：一次运行 × 一个站点 = 一个 job
ALTER TABLE wc_sync_job
    ADD COLUMN site_code VARCHAR(32) NOT NULL DEFAULT 'zokomart' COMMENT '目标站点 code';
ALTER TABLE wc_sync_job ALTER COLUMN site_code DROP DEFAULT;

-- 3) 广告图 media id：每站一行
CREATE TABLE ad_image_site_media (
    ad_image_id BIGINT      NOT NULL COMMENT 'ad_product_image.id',
    site_code   VARCHAR(32) NOT NULL COMMENT '目标站点 code',
    wc_media_id BIGINT      NOT NULL COMMENT '该站点上的 media 附件 id',
    create_time DATETIME             DEFAULT NULL,
    update_time DATETIME             DEFAULT NULL,
    PRIMARY KEY (ad_image_id, site_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '广告图在各站点的 media id 映射';

INSERT INTO ad_image_site_media (ad_image_id, site_code, wc_media_id, create_time)
SELECT id, 'zokomart', wc_media_id, NOW() FROM ad_product_image WHERE wc_media_id IS NOT NULL;

ALTER TABLE ad_product_image DROP COLUMN wc_media_id;
```

**回填到 `'zokomart'` 是整个迁移的关键**：既有映射原样保留、继续可用；kianosmart 尚无任何行，
因此首次同步会把全部产品在该站 **CREATE** 出来并各自上传图片——这是正确行为，不是重复。

**关于 `ad_product_image.wc_media_id` 的删除**：已核实其**仅有的两处读写**在
`WcSyncServiceImpl.upsertOne`（读：复用 media id 不重传；写：回写 media id），本次都会重写，
故删除安全、且避免留下"两个真相来源"。

**关于复合主键**：`WcSyncRecord` 采用 `(supplier_product_id, site_code)` 复合主键而非新增代理 id，
让 schema 如实表达身份。代价是 MyBatis-Plus 的 `selectById`/`updateById` 不再适用，改用条件
构造器；受影响的只有 2 处调用点（`upsertOne` 的查找、`saveRecord` 的落库）。

## 5. 客户端接线

- **`WooCommerceClient` 接口保持不变**（方法签名不动）。
- `WooCommerceClientImpl` 不再是绑定单一配置的单例，改为构造时接收一个 `WcSite`；
  共享 `HttpClient`/`ObjectMapper`。
- 新增 `WooCommerceClientFactory`（`@Component`）：
  - `WooCommerceClient forSite(String code)`（按 code 缓存实例）
  - `List<WcSite> sites()`
  - `boolean configured(String code)`
- `WcSyncServiceImpl` 注入工厂，按 job 的 `siteCode` 解析客户端。
- **分类/品牌缓存**：`categoryCache`/`brandCache` 本就是"每次运行的局部 Map"，而每个 job 只针对
  一个站点，因此天然是每站独立的——**不可**改成跨站共享缓存（各 WordPress 的分类/品牌 id 不同，
  共享会造成静默错配）。

## 6. 同步逻辑改动

`upsertOne` 内仅两处行为变化，其余（图片决策矩阵、停用→draft、描述标记区块）逻辑不变，
只是其状态天然变成按站点：

1. **价格**：`build()` 用**该站点的**有效倍率，而非全局倍率。
2. **广告图 media id**：改为从"当前站点的 `Map<adImageId, wcMediaId>`"取值（进入产品循环前按站点
   批量载入）；回写时 upsert 进 `ad_image_site_media`。

描述里的广告图 URL 取自该站点响应的 `ref.getImages()`，本就是站点私有的，无需额外改动。

## 7. API 与任务编排

| 方法 | 路径 | 变化 |
| --- | --- | --- |
| POST | `/api/wc-sync/supplier-brands` | 入参增加 `siteCodes: string[]`（省略=全部已配置站点）；**返回改为 `{ jobIds: [...] }`** |
| GET | `/api/wc-sync/sites` | **新增**，返回 `[{code, name, configured}]` 供弹框使用 |
| GET | `/api/wc-sync/jobs/{id}` | 不变；`WcSyncJobVO` 增加 `siteCode`/`siteName` |
| GET | `/api/wc-sync/jobs` | 不变（可按站点过滤为后续增强，本次不做） |

全部沿用 `@SaCheckPermission("wc:sync")`，无新增菜单/权限。

- **入参校验**：`siteCodes` 中出现**未知 code** 或**未配置（缺 base-url/key/secret）的站点**时，
  整个请求以 `WC_NOT_CONFIGURED` 拒绝并指明是哪个 code——不做"跳过坏站点、只同步好站点"的静默降级。
  `sites` 整体为空同样返回 `WC_NOT_CONFIGURED`。
- **单飞锁按站点**：`WcSyncLock` 由单个 `AtomicBoolean` 改为按 code 的并发集合，
  `tryAcquire(code)`/`release(code)`。
- **冲突语义**：若所选站点中**任一**正在同步，则**整个请求被拒**并抛 `WC_SYNC_RUNNING`（消息指明是哪个站点）。
  最简单、可预期，与现行语义一致。
- 执行器仍为**单线程**：两个站点的 job 顺序执行，对 WC 更友好（避免并发触发限流）。
- 启动恢复 `WcSyncStartupRecovery` 不变（残留 RUNNING → INTERRUPTED，天然覆盖所有站点的 job）。

## 8. 前端

- 新增 `apiWcSyncSites()`；类型新增 `WcSyncSite`，`WcSyncJob` 增加 `siteCode`/`siteName`。
- 弹框新增「目标站点」多选，**默认全选**已配置站点；未配置的站点置灰不可选。
- `apiStartWcSync` 返回 `jobIds[]`；弹框对每个 job 轮询，**按站点各渲染一块进度**
  （站点名 + 进度条 + 计数 + 失败表）。终态判定与停轮询逻辑按 job 各自独立。

## 9. 测试

- **站点隔离**：先同步 A 再同步 B → `wc_sync_record` 出现两行；B 的同步不改动 A 行的
  `wc_product_id`/`wc_image_id`。
- **每站图片幂等**：B 同步之后再次同步 A，A 仍**不传 images**（证明未因跨站污染而重传）。
- **广告图 media id 隔离**（关键回归）：B 的请求**不得**携带 A 的 media id；B 首次应以 `src` 上传
  并把新 id 写入 `ad_image_site_media`。
- **每站倍率**：站点覆盖生效；未覆盖时回退全局。
- **按站点单飞锁**：A 运行中 → 再启动 A 被拒；启动 B 允许。
- **V22 回填**：迁移前已存在的 `wc_sync_record` 行，迁移后能以 `site_code='zokomart'` 读到。
- **前端**：站点列表载入并默认全选；start 携带 `siteCodes`；能同时轮询多个 job 并按站点渲染进度。

## 10. 部署

1. 把 `backend/src/main/resources/application-local.yml` 改写为 `sites:` 结构，
   并补上 kianosmart 的 `base-url`/`consumer-key`/`consumer-secret`（**仅本地文件，禁入库**）。
2. `mvn clean package -DskipTests` 重建并重启 jar（以 jar 运行，不重建会跑旧字节码）。
3. 前端 `pnpm build` 部署。
4. 首次同步 kianosmart 会创建全部产品并上传全部图片（预期行为）。
5. 广告图上传依赖 `app.wc.public-file-base-url` 公网可达（两站共用同一来源地址）。
