# 独立站同步（WooCommerce 商品同步）— 设计文档

- 日期：2026-06-17
- 范围：`backend/`（admin.zokomart.africa）+ `frontend/`（front.admin.zokomart.africa）
- 特性分支：两仓 `feat/wc-sync`

## 1. 目标

把本后台中**选定供应商 + 一个或多个品牌**的产品，单向推送到一个 WordPress + WooCommerce 独立站售卖，**不改动 WordPress 代码**——通过 WooCommerce 自带的 REST API 推送。本后台是商品主数据源。

## 2. 关键决策（已与用户确认）

| # | 决策 | 选择 |
|---|------|------|
| 1 | 同步方向 | **单向推送**（后台 → WooCommerce）；不拉回订单 |
| 2 | 触发 | **手动**：选供应商+品牌→批量推；返回逐条结果 |
| 3 | 库存 | **同步数量**：`inventory_stock.quantity` → WC `manage_stock=true, stock_quantity`（无库存行=0） |
| 4 | 品牌映射 | 品牌名 → **WC 商品分类 category**（不存在则自动建） |
| 5 | 零售价 | `retailPrice>0` 用它；否则 `round(wholesalePrice × 倍率)`，倍率默认 **1.7**（可配置，不回填落库） |
| 6 | 停用处理 | 已同步产品被停用(status=0) → WC 置 **draft**（下架保留，不删除） |
| 7 | 入口 | 复用「供应商产品」页加「同步到独立站」按钮+对话框（不建顶级菜单） |
| 8 | WC 分类层级 | 仅用品牌名作分类，不推我方 categoryId 层级 |

## 3. 配置（`application-local.yml`，密钥不入库）
```yaml
app:
  wc:
    base-url: https://你的站点          # WooCommerce 站点根（HTTPS）
    consumer-key: ck_xxx               # WC 后台生成的 REST API key
    consumer-secret: cs_xxx
    price-multiplier: 1.7              # 零售价倍率，默认 1.7
```
- `application.yml` 仅放占位/默认（multiplier=1.7）；key/secret/base-url 放本地未提交 profile。
- WC REST 鉴权：HTTPS + HTTP Basic Auth（consumer-key 作用户名、consumer-secret 作密码）。

## 4. 后端设计（新 `module/wcsync`）

### 4.1 配置类
`WcSyncProperties`（`@ConfigurationProperties("app.wc")`）：baseUrl、consumerKey、consumerSecret、priceMultiplier（默认 1.7）。

### 4.2 WooCommerce 客户端
`WooCommerceClient`（封装 `java.net.http.HttpClient`，Basic Auth）：
- `Long ensureCategory(String brandName)`：`GET /wp-json/wc/v3/products/categories?search=名`，命中返回 id；否则 `POST .../categories` 建并返回 id。
- `WcBatchResult batchUpsert(List<WcProduct> toCreate, List<WcProduct> toUpdate)`：`POST /wp-json/wc/v3/products/batch`（每批 ≤100，分块），返回每条的 id/sku/错误。
- `Long findProductIdBySku(String sku)`：`GET /wp-json/wc/v3/products?sku=...` 兜底（无映射记录时判断建/改）。
- 非 2xx / 网络异常 → 抛 `BusinessException`（整请求级失败用专用错误码）。

### 4.3 同步服务
`WcSyncService.syncSupplierBrands(Long supplierId, List<Long> brandIds) -> WcSyncResultVO`：
1. 校验：WC 配置齐全（缺失→`WC_NOT_CONFIGURED`）；supplier/brand 存在。
2. 选品：`supplier_product` where `supplier_id=? AND brand_id IN (?) AND deleted=0`。
   - `status=1`（启用）→ 正常 upsert（active/publish）。
   - `status=0`（停用）但**已有同步记录** → 置 WC `draft`；未同步过的停用品跳过。
3. 品牌→分类：对涉及的每个 brandId，`ensureCategory(品牌名)` 得 WC 分类 id（本次缓存）。
4. 字段映射（每个产品 → `WcProduct`）：
   - `sku = productCode`
   - `name`
   - `regular_price = retailPrice>0 ? retailPrice : round(wholesalePrice × multiplier, 2)`
   - `manage_stock=true`，`stock_quantity = inventory_stock.quantity`（无行=0）
   - `images = imageUrl 非空 ? [{src: 绝对URL}] : 省略`
   - `categories = [{id: 品牌分类id}]`
   - `status = (supplier_product.status==1) ? "publish" : "draft"`
5. 幂等 upsert：用 `wc_sync_record` 决定建/改；无记录者先 `findProductIdBySku` 兜底（处理此前手动建过或上次没记录的情况），命中则 update、否则 create。经 `batchUpsert` 批量执行。成功后写回 `wc_sync_record`（wc_product_id、sku、last_status、last_synced_time、last_error）。
6. 汇总返回 `WcSyncResultVO`：total/created/updated/drafted/failed + `List<WcSyncRowError>{supplierProductId, productCode, reason}`。best-effort：单条失败不影响其它，记入 errors + 该记录 last_error。

### 4.4 数据表（迁移 V13）
```sql
CREATE TABLE wc_sync_record (
    supplier_product_id BIGINT NOT NULL COMMENT '后台供应商产品(主键关联)',
    wc_product_id       BIGINT       DEFAULT NULL COMMENT 'WooCommerce 商品 id',
    sku                 VARCHAR(64)  DEFAULT NULL,
    last_status         VARCHAR(32)  DEFAULT NULL COMMENT 'CREATED/UPDATED/DRAFTED/FAILED',
    last_synced_time    DATETIME     DEFAULT NULL,
    last_error          VARCHAR(512) DEFAULT NULL,
    PRIMARY KEY (supplier_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WooCommerce 同步记录';
```
+ 菜单按钮 `wc:sync`（挂「供应商产品」菜单 1110 下，新按钮 id 取未占用段，如 2065），superadmin 通配可见。

### 4.5 端点
`POST /api/wc-sync/supplier-brands`，权限 `@SaCheckPermission("wc:sync")`，body `{ supplierId, brandIds:[...] }` → `Result<WcSyncResultVO>`。

## 5. 前端设计（复用供应商产品页）
- 「供应商产品」页加 **「同步到独立站」** 按钮（`v-perm="'wc:sync'"`）。
- 同步对话框 `SupplierProductWcSyncModal.vue`：供应商下拉（默认当前 CascadeFilter 供应商）+ 品牌**多选**（仅已授权品牌，复用 `apiAuthorizedBrands`）+ 「同步」按钮 → `apiWcSync({supplierId, brandIds})` → 展示 created/updated/drafted/failed 计数 + 逐条错误表（同导入/抓取结果区）。
- `src/api/wcSync.ts` + 类型 `WcSyncResult`/`WcSyncRowError`。

## 6. 测试
- **后端**：
  - `WcSyncService` 单测：把 `WooCommerceClient` 抽为接口并 **mock**，自建供应商/品牌/产品/库存，断言——价格规则（已填用已填、未填 1.7×并四舍五入）、`stock_quantity` 取 inventory 数量（无库存=0）、品牌→分类 id、status=0 且有记录→draft、有记录走 update / 无记录走 create、单条失败记入 errors。
  - WC HTTP 客户端本身不连真实站点（联调时手测）；可对 `WooCommerceClient` 的 URL/鉴权拼装做轻量单测。
- **前端**：同步对话框组件测试（mock 接口；供应商变更刷新授权品牌；提交 payload 含 supplierId/brandIds；渲染结果计数）。

## 7. 部署/运维注意（非代码）
- 图片 `imageUrl` 必须 **WP 服务器公网可达**（Morgan 图公开 ✓；后台 `/files` 图需公网可访问）。
- WC 站点须 HTTPS（保护 Basic Auth 凭据）。
- 在 WooCommerce → 设置 → 高级 → REST API 生成读写 key/secret。

## 8. 改动范围与交付
- `backend/`：`module/wcsync`（WcSyncProperties、WooCommerceClient(接口+impl)、WcProduct/WcSyncResultVO/WcSyncRowError、WcSyncService(+impl)、WcSyncController、WcSyncRecord 实体+mapper）、V13 迁移、application.yml 占位、单测。
- `frontend/`：同步对话框 + 页面按钮、api、类型、组件测试。
- 两仓 `feat/wc-sync` 分支。

## 9. 不做（YAGNI）
- 不拉回 WooCommerce 订单（单向）。
- 不做实时/定时同步（仅手动触发）。
- 不在 WC 删除商品（停用→draft）。
- 不推我方分类层级（WC 分类仅用品牌）。
- 不做每商品独立倍率（全局可配置倍率）。
- 暂只支持单个 WC 站点。
