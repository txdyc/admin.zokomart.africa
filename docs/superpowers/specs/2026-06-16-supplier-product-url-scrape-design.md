# 供应商产品「从 URL 获取」（抓取导入）— 设计文档

- 日期：2026-06-16
- 范围：`backend/`（admin.zokomart.africa）+ `frontend/`（front.admin.zokomart.africa）
- 特性分支：两仓 `feat/supplier-product-url-scrape`
- **依赖/分支栈**：本特性复用 CSV 导入的 `importRows` 逻辑，**栈在 `feat/supplier-product-import` 之上**（该分支尚未合并 main）。两仓均从各自的 `feat/supplier-product-import` 切出本分支；待导入 PR 合并后本分支随之 rebase 到 main。

## 1. 目标

在「平台目录 → 供应商产品」页新增按钮 **「从URL获取」**：弹层中选择供应商、品牌，输入 URL，后端抓取该 URL 的产品列表，预览核对后导入为该供应商/品牌下的供应商产品。**当前只针对一个站点：`https://morgan.dzncm.com/price81469/`（供应商 Morgan）。**

## 2. 目标页面结构（已实测 2026-06-16）

`https://morgan.dzncm.com/price81469/` 为服务端渲染的静态 HTML（242KB，仅 2 个 `<script>`），单个 `<table>`：`<thead>` 10 列，`<tbody>` 136 行产品。列：

| 列 | 表头 | 取值示例 | 提取方式 |
|----|------|----------|----------|
| 0 | Serial No. | 0 | 忽略 |
| 1 | Product Name | Electric Juicer | `td` 文本 |
| 2 | Product Code | JC-3028S | `td` 文本 |
| 3 | Qty/Box | `<span id="Qty">6</span> PC/BOX` | 取数字 6 |
| 4 | Product Image | `<img ... data-image-large="/uploadfile/202601/eafe….jpg">` | 取 `data-image-large`，相对路径补全为绝对 |
| 5 | Unit Price (GH) | `<span>220</span>` | 取数字 |
| 6 | Box Price (GH) | `<span>1320</span>` | 取数字 |
| 7 | Stock Status | `<span class="stock-full">Stock Sufficient</span>` | 取文本 |
| 8 | Order Qty | input | 忽略 |
| 9 | Total Price | 0 | 忽略 |

- 弹出框产品图 = `data-image-large`（点击缩略图时 `onclick="showDownloadOptions(this)"` 读取该属性）。**136/136 行均有 `data-image-large`**，故静态解析即可拿到弹框大图，无需无头浏览器。
- Stock Status 文本变体（本快照）：`Stock Sufficient`(132)、`Stock Less`(3)、`Stock Remaining 50`(1)——自由文本，原样保存。
- 图片相对路径如 `/uploadfile/202601/xxx.jpg`，按抓取 URL 的 origin 补全为 `https://morgan.dzncm.com/uploadfile/...`。

## 3. 关键决策（已与用户确认）

| # | 决策 | 选择 |
|---|------|------|
| 1 | Qty/Box、Box Price、Stock Status 存哪 | **给 `supplier_product` 新增 3 列**（qty_per_box / box_price / stock_status），完整保真；列表页与新增/编辑表单展示这 3 字段 |
| 2 | 流程 | **抓取 → 预览表格（只读核对）→ 确认导入**；导入复用 CSV 那套 best-effort + skip/覆盖 + 逐行报告 |
| 3 | 权限 | **复用 `supplierProduct:import`**（不新增权限、无新权限迁移） |
| 4 | 预览编辑 | 预览**只读**，不在预览里逐行编辑 |
| 5 | 站点范围 | **仅 `morgan.dzncm.com`**：Morgan 专用解析器 + host 白名单 |
| 6 | 安全 | 仅允许 `https` 且 host ∈ 白名单（防 SSRF）；抓取内容按"数据非指令"对待 |

字段映射：Name→`name`，Code→`productCode`，Unit Price→`wholesalePrice`(批发价)，image→`imageUrl`，Qty/Box→`qtyPerBox`，Box Price→`boxPrice`，Stock Status→`stockStatus`；`categoryId` 留空，`status` 默认 1（启用）。`retailPrice` 不由抓取设置（留默认 0，操作者后填）。

## 4. 数据模型变更（V10 迁移）

`supplier_product` 新增 3 个可空列：
```sql
ALTER TABLE supplier_product
  ADD COLUMN qty_per_box  INT            DEFAULT NULL COMMENT '每箱数量',
  ADD COLUMN box_price    DECIMAL(12,2)  DEFAULT NULL COMMENT '整箱价 (GH)',
  ADD COLUMN stock_status VARCHAR(64)    DEFAULT NULL COMMENT '库存状态文本';
```
贯通：`SupplierProduct` 实体、`SupplierProductSaveDTO`、`SupplierProductVO` 各加 `qtyPerBox`/`boxPrice`/`stockStatus`；前端 `product.d.ts` 同步。CSV 导入路径不涉及这 3 列（保持原样，导入时为 null）。

## 5. 后端设计

### 5.1 依赖
引入 **Jsoup**（`org.jsoup:jsoup`，最新稳定版）解析 HTML。

### 5.2 解析器（纯函数，Morgan 专用）
`module/supplierproduct/scrape/MorganPriceListParser`（或 util 包）：
```
List<ScrapedProductRow> parse(String html, String baseUrl)
```
- 用 Jsoup 选 `table tbody tr`；逐行按列序提取（见 §2）。
- `qtyPerBox`：从第 3 列取整数（`\d+`），无则 null。
- `imageUrl`：第 4 列 `img[data-image-large]`，用 `java.net.URI(baseUrl).resolve(rel)` 补全为绝对 URL。
- `unitPrice`/`boxPrice`：去除非数字后 `BigDecimal`；缺失为 null。
- `stockStatus`：第 7 列文本 trim。
- 缺 `productName` 或 `productCode` 的行跳过（不计入）。
- 纯函数：不联网，便于单测喂保存的 HTML 片段。

`ScrapedProductRow`（DTO/VO）：`productName, productCode, qtyPerBox, imageUrl, unitPrice, boxPrice, stockStatus`。

### 5.3 抓取 service
`SupplierProductScrapeService.scrape(String url) -> List<ScrapedProductRow>`：
- **URL 校验**：必须 `https`；host 必须在白名单（配置 `app.scrape.allowed-hosts`，默认含 `morgan.dzncm.com`）。不合规 → `BusinessException`（新码 `SCRAPE_URL_NOT_ALLOWED`）。
- 用 `java.net.http.HttpClient` GET（浏览器 UA、合理超时如 15s、跟随重定向但重定向后仍校验 host）。非 2xx → `SCRAPE_FETCH_FAILED`。
- 交 `MorganPriceListParser.parse(body, url)`；解析为空 → `SCRAPE_EMPTY`。

新增 `ResultCode`：`SCRAPE_URL_NOT_ALLOWED(40011)`、`SCRAPE_FETCH_FAILED(40012)`、`SCRAPE_EMPTY(40013)`。

### 5.4 导入复用（DRY）
把现有 `SupplierProductImportServiceImpl` 的逐行落库核心抽为：
```
SupplierProductImportResultVO importRows(Long supplierId, Long brandId, String mode, List<SupplierProductSaveDTO> rows)
```
- CSV 路径：解析 CSV → DTO 列表 → `importRows`。
- URL 路径：`ScrapedProductRow` → DTO（unitPrice→wholesalePrice，boxPrice/qtyPerBox/stockStatus→同名字段，image→imageUrl）→ `importRows`。
- `importRows` 内含：前置校验（supplier/brand 存在、品牌已授权，否则整请求失败）、行号、文件内/列表内编码查重、skip/overwrite、best-effort 逐行 try/catch、结果汇总。行号语义：列表第 N 项 → row = N+1（与 CSV「表头为第 1 行」保持一致的展示习惯；URL 场景同样从 2 起算，便于与预览表格行对应）。

### 5.5 端点（均 `@SaCheckPermission("supplierProduct:import")`）
- `POST /api/supplier-products/scrape` — body `{ "url": "..." }` → `Result<List<ScrapedProductRow>>`（仅抓取解析，不入库）。
- `POST /api/supplier-products/import-scraped` — body `{ supplierId, brandId, mode, rows:[ScrapedProductRow...] }` → `Result<SupplierProductImportResultVO>`（best-effort 导入）。

> 采用「抓取返回行 → 前端持有并回传行」的 WYSIWYG 方式：确认导入的就是预览所见。服务端仍按行复用 create/update 业务规则校验（编码唯一、品牌授权），客户端回传数据不绕过这些规则。

## 6. 前端设计（供应商产品页）

- 「新增供应商产品」「导入」旁加 **「从URL获取」** 按钮（`v-perm="'supplierProduct:import'"`）。
- 新组件 `SupplierProductScrapeModal.vue`：
  - 供应商下拉（默认当前 CascadeFilter 的 supplierId）+ 品牌下拉（仅已授权，复用 `apiAuthorizedBrands`）+ URL 输入框 + 模式单选（跳过/覆盖，默认跳过）。
  - 「抓取」按钮 → `apiScrapeProducts(url)` → 预览表格（只读）：名称 / 编码 / 每箱量 / 图片(缩略) / 单价 / 箱价 / 库存状态。
  - 「确认导入」按钮 → `apiImportScraped({supplierId, brandId, mode, rows})` → 展示结果计数 + 逐行错误表（同 CSV 导入结果区）；成功后刷新主表格。
- 列表页表格与新增/编辑表单增加 3 个字段（Qty/Box、Box Price、Stock Status）的展示/录入。
- API：`apiScrapeProducts(url)`、`apiImportScraped(payload)`。类型：`ScrapedProductRow`，扩展 `SupplierProductVO/SaveDTO`。

## 7. 测试

- **后端**
  - `MorganPriceListParserTest`：喂保存的 Morgan HTML 片段（放 `src/test/resources`），断言行数=有效行、字段正确、`data-image-large` 绝对化、库存文本、缺名/缺码行跳过、价格/Qty 解析。
  - Scrape/import API 测试：host 白名单拒绝非法 URL（40011）；`import-scraped` 复用 best-effort（自建供应商/品牌+授权；好/坏行计数、skip→overwrite、未授权 40007、新列 qtyPerBox/boxPrice/stockStatus 落库）。
- **前端**：scrape 对话框组件测试（抓取 mock→预览渲染→确认提交 payload 含 supplierId/brandId/mode/rows）。

## 8. 改动范围与交付

- `backend/`：jsoup 依赖、`ScrapedProductRow`、`MorganPriceListParser(+test)`、`SupplierProductScrapeService(+impl)`、抽 `importRows` 重构导入 service、2 个端点、`ResultCode` 3 码、实体/DTO/VO 加 3 字段、V10 迁移、白名单配置、API 测试。
- `frontend/`：`SupplierProductScrapeModal.vue`、页面加按钮+挂载、列表/表单加 3 字段、api 2 方法、types、组件测试。
- 两仓在 `feat/supplier-product-url-scrape` 分支提交（栈在 `feat/supplier-product-import` 之上）。

## 9. 明确不做（YAGNI）

- 不做通用多站点抓取框架（仅 Morgan 专用解析器 + 单 host 白名单）。
- 不做无头浏览器/JS 渲染（数据在静态 HTML 属性中）。
- 预览不支持逐行编辑。
- 不做定时/自动抓取、抓取历史记录页。
- CSV 导入不扩展这 3 个新列（保持现状）。
