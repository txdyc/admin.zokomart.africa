# 供应商产品 CSV 批量导入 — 设计文档

- 日期：2026-06-15
- 范围：`backend/`（admin.zokomart.africa）+ `frontend/`（front.admin.zokomart.africa）
- 特性分支：两仓各自 `feat/supplier-product-import`

## 1. 目标

在 **平台目录 → 供应商产品** 页面增加"批量导入"功能：操作者在对话框中**选定一个供应商 + 一个品牌**，上传 CSV 文件，将文件中的产品行批量导入为该供应商、该品牌下的供应商产品。

## 2. 背景与约束（既有模型）

`supplier_product` 表（实体 `SupplierProduct extends BaseEntity`）字段：
`supplierId`(必填)、`name`(必填)、`brandId`(可选)、`categoryId`(可选)、`productCode`(必填)、
`wholesalePrice`、`retailPrice`、`imageUrl`、`minPurchaseQty`(默认 1)、`skuId`、`status`(默认 1)、`remark`。

既有两条硬业务规则（`SupplierProductServiceImpl`）：
1. **产品编码在同一供应商下唯一**（`existsProductCode`）。
2. 若设置了 `brandId`，**供应商必须已被授权该品牌**，否则抛 `BRAND_NOT_AUTHORIZED`（`supplierBrandService.isAuthorized`）。

`category` 表为树状（`parentId`/`name`/`sort`/`status`），**名称不唯一**（实测 `Appliances`×3、`Fridges`×3，同名不同父级）。

权限：`supplierProduct:list/create/update/delete` 当前仅授予角色 901（BUYER）；superadmin 走通配 `*`（`StpInterfaceImpl` 对超管返回 `["*"]`）。

## 3. 关键决策（已与用户确认）

| # | 决策 | 选择 |
|---|------|------|
| 1 | 坏行处理 | **尽力导入 + 逐行报告**：合法行入库，非法行跳过并返回行号+原因；不回滚已成功行 |
| 2 | 编码已存在 | **导入时可选 跳过/覆盖**，默认"跳过"。覆盖=用 CSV 值更新该供应商下同编码的现有产品 |
| 3 | CSV 分类列 | **分类路径 `父>子`**，按 `>` 分级逐级精确匹配；留空=不设分类；找不到或仍重名→该行报错 |
| 4 | 表头/模板 | **中文表头 + 提供模板下载**（UTF-8 带 BOM，Excel 可直接打开） |
| 5 | 规模/同步性 | **同步**处理，单次 **≤ 1000 行**；沿用已配置 5MB multipart 上限 |
| 6 | CSV 解析 | 后端引入 **Apache Commons CSV** 依赖（处理带引号/逗号字段与 BOM） |
| 7 | 状态列 | **不放进 CSV**，导入产品状态默认"启用"(1) |

## 4. CSV 格式

中文表头，列顺序固定（按表头名解析，列序可容错）：

```
产品名称,产品编码,分类路径,批发价,零售价,最小采购量,图片URL,备注
海尔双门冰箱,HR-BCD-201,Appliances>Fridges,1200.00,1599.00,5,,样例备注
```

- **必填**：`产品名称`、`产品编码`。
- `分类路径`：`父>子`（如 `Appliances>Fridges`）；留空=不设分类。
- `批发价`/`零售价`：默认 0，不可为负。
- `最小采购量`：默认 1，≥1 的整数。
- `图片URL`、`备注`：可选。
- 供应商、品牌、状态**不在 CSV** 中——供应商/品牌由对话框选择并套用到所有行，状态默认启用。

## 5. 后端设计（module/supplierproduct）

### 5.1 端点

- `POST /api/supplier-products/import` — multipart/form-data
  - 参数：`file`(MultipartFile)、`supplierId`(Long)、`brandId`(Long)、`mode`(String，`skip` 默认 / `overwrite`)
  - 权限：`@SaCheckPermission("supplierProduct:import")`
  - 返回：`Result<ImportResultVO>`
- `GET /api/supplier-products/import/template` — 下载模板
  - 权限：`@SaCheckPermission("supplierProduct:import")`
  - 返回：`text/csv`、UTF-8 带 BOM、`Content-Disposition: attachment; filename="supplier_product_template.csv"`，含表头 + 1 行示例

### 5.2 新增/改动类

- `SupplierProductImportService` + `impl`（独立文件，单一职责：编排导入）。
- `ImportResultVO`：`total`、`created`、`updated`、`skipped`、`failed`、`errors: List<ImportRowError>`。
- `ImportRowError`：`row`(int)、`productCode`(String, 可空)、`reason`(String)。
- `CategoryPathResolver`（basedata 下的辅助组件）：一次性载入全部分类，按 `(parentId, name)` 建索引，提供 `resolve(path): Long`（命中唯一返回 id；空路径返回 null；未命中/某级多匹配抛可识别异常）。
- `SupplierProductService` 增 `findBySupplierAndCode(supplierId, productCode)`（覆盖模式定位现有行）；导入按行复用既有 `createSupplierProduct` / `updateSupplierProduct` 以保持业务规则统一。
- `ResultCode` 视需要新增导入相关错误码（如 `IMPORT_TOO_MANY_ROWS`、`IMPORT_EMPTY` 等）。

### 5.3 导入流程

1. **前置整体校验**（任一不过 → 整请求快速失败，不处理任何行）：
   - supplier 存在；brand 存在；`supplierBrandService.isAuthorized(supplierId, brandId)` 为真，否则返回 `BRAND_NOT_AUTHORIZED`。
   - 文件非空、可解析为 CSV、数据行数 ≤ 1000。
2. **逐行处理**（数据行行号从 2 起算，对齐 Excel）：
   - 必填校验：`产品名称`、`产品编码` 非空。
   - 解析 `批发价`/`零售价`→ `BigDecimal` 且 ≥ 0；`最小采购量`→ int 且 ≥ 1（空则用默认）。
   - 分类路径 → `categoryId`（空=null；未找到/重名→行错误）。
   - **文件内查重**：同一文件中重复的 `产品编码` → 第二次起报错"文件内编码重复"。
   - 落库分支（套用对话框的 supplierId/brandId）：
     - 已存在 + `skip` → `skipped`，报告"该供应商下编码已存在，已跳过"。
     - 已存在 + `overwrite` → 用 CSV 值 `updateSupplierProduct`，计入 `updated`。
     - 不存在 → `createSupplierProduct`，计入 `created`。
   - 每行独立 `try/catch`，业务异常转为 `ImportRowError`（行号+编码+原因），继续下一行（**不回滚已成功行**）。
3. 返回 `ImportResultVO` 汇总。

### 5.4 解析与编码

- 引入 `org.apache.commons:commons-csv`（`pom.xml`），按表头名读取，容错列序。
- 读取时去除 UTF-8 BOM；以 UTF-8 解码。
- 模板生成：UTF-8 带 BOM 前缀 + 中文表头 + 示例行，`ResponseEntity<byte[]>` 返回。

### 5.5 迁移 V9

```
-- 菜单按钮：供应商产品导入（挂 1110 供应商产品）
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, ...) VALUES
(2063, 1110, '导入供应商产品', 3, 'supplierProduct:import', ...);
-- 授予角色 901（与其它 supplierProduct 权限一致；superadmin 通配自动覆盖）
INSERT INTO sys_role_menu (...) SELECT ... role_id=901, menu_id=2063;
```

## 6. 前端设计（供应商产品页）

- 在"新增供应商产品"按钮旁加 **"导入"** 按钮，`v-perm="'supplierProduct:import'"`。
- **导入对话框**（a-modal）：
  - 供应商下拉（默认带入当前 CascadeFilter 的 supplierId）。
  - 品牌下拉：仅列**该供应商已授权品牌**（复用 `apiAuthorizedBrands(supplierId)`）；供应商变更时刷新；无授权品牌则提示"请先在供应商管理给该供应商授权品牌"。
  - 模式单选：`跳过已存在`(默认) / `覆盖更新`。
  - 文件选择：a-upload（accept `.csv`，`:before-upload` 拦截不自动上传，仅暂存文件）。
  - "下载模板"链接 → 调模板端点触发下载。
  - 提交：组装 `FormData`(file/supplierId/brandId/mode) → `apiSupplierProductImport`。
  - 结果区：展示 created/updated/skipped/failed 计数 + 错误明细表（行号/编码/原因）；成功（failed=0 或部分成功）后刷新主表格列表。
- **API**（`src/api/product/supplierProduct.ts`）：
  - `apiSupplierProductImport(form: FormData)` → `POST /supplier-products/import`（multipart）。
  - `apiSupplierProductImportTemplate()` → `GET /supplier-products/import/template`（blob 下载）。
- **类型**（`src/types/product.d.ts`）：`ImportResultVO`、`ImportRowError`、`ImportMode`。
- 请求层需支持 multipart 与 blob 下载（确认 `@/utils/request` 能力，必要时补充）。

## 7. 测试

- **后端**
  - ImportService 单测：必填缺失、价格非法、MOQ 非法、分类路径命中/重名/未找到、文件内重复、skip vs overwrite、品牌未授权快速失败、超 1000 行拒绝。
  - API 测试：multipart 上传 → 断言计数与错误明细；模板下载 200 + Content-Disposition。
  - CategoryPathResolver 单测：重名靠父级区分、路径未命中。
- **前端**
  - 导入对话框组件测试：供应商变更刷新授权品牌、组装并提交 FormData、结果计数与错误表渲染。
  - 可选 e2e：上传含 1 好 1 坏行的 CSV，断言结果展示与列表刷新。

## 8. 改动范围与交付

- `backend/`：import service/impl、controller 两个端点、ImportResultVO/ImportRowError、CategoryPathResolver、SupplierProductService 增查询、ResultCode 增码、V9 迁移、commons-csv 依赖、相应测试。
- `frontend/`：供应商产品页加按钮+导入对话框、supplierProduct api 两个方法、product 类型、相应测试。
- 两仓各自在 `feat/supplier-product-import` 分支提交。

## 9. 明确不做（YAGNI）

- 不做异步任务/任务状态表（同步 + 行数上限足够）。
- CSV 不含供应商/品牌/状态列。
- 不做 Excel(.xlsx) 解析，仅 CSV。
- 不做导入历史/审计页面（落库本身有审计字段即可）。
