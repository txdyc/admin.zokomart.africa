# 供应商产品 CSV 批量导入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在「平台目录 → 供应商产品」页提供 CSV 批量导入：对话框选定供应商+品牌，上传 CSV，尽力导入并逐行报告结果。

**Architecture:** 后端新增同步导入端点 `POST /api/supplier-products/import`（multipart），按行复用既有 `createSupplierProduct`/`updateSupplierProduct` 业务规则，分类按 `父>子` 路径解析；前端新增导入对话框组件并接入页面。两仓各自 `feat/supplier-product-import` 分支。

**Tech Stack:** 后端 SpringBoot 3.5 + MyBatis-Plus + Sa-Token + Apache Commons CSV；前端 Vue3 + Ant Design Vue + Vitest。

**与 spec 的偏差（已在计划阶段确认）：** 模板下载改为**前端生成**（带 BOM 的 Blob），不做后端模板端点——后端鉴权走 Authorization 头、响应拦截器统一按 JSON 解包，浏览器直链下载文件不可行；前端生成 UX 等价且更简单。后端仅保留导入 POST 端点与 `supplierProduct:import` 权限。

**仓库根目录：** 后端 `D:\GHANA\claude\admin.zokomart.africa\backend`，前端 `D:\GHANA\claude\admin.zokomart.africa\frontend`。两仓均已在分支 `feat/supplier-product-import`（后端已建；前端需新建，见 Task F0）。

**前置：** 本机 MySQL(`zokomart_admin`)、Redis 已运行；后端测试为 `@SpringBootTest` 连真实本地库，按既有用例风格自建数据并清理；超管账号 `superadmin / Admin@123`。

---

## File Structure

### 后端 `backend/`
- Modify `pom.xml` — 加 `commons-csv` 依赖。
- Modify `src/main/java/africa/zokomart/admin/common/result/ResultCode.java` — 加 `IMPORT_FILE_INVALID(40009)`、`IMPORT_TOO_MANY_ROWS(40010)`。
- Create `src/main/resources/db/migration/V9__supplier_product_import_perm.sql` — 菜单按钮 + 角色授权。
- Create `src/main/java/africa/zokomart/admin/module/supplierproduct/vo/ImportRowError.java`。
- Create `src/main/java/africa/zokomart/admin/module/supplierproduct/vo/SupplierProductImportResultVO.java`。
- Create `src/main/java/africa/zokomart/admin/module/basedata/util/CategoryPathResolver.java` — 纯函数：`(List<Category>, path) -> Long`。
- Modify `.../supplierproduct/service/SupplierProductService.java` + `impl/SupplierProductServiceImpl.java` — 加 `findBySupplierAndCode`。
- Create `.../supplierproduct/service/SupplierProductImportService.java` + `impl/SupplierProductImportServiceImpl.java`。
- Modify `.../supplierproduct/controller/SupplierProductController.java` — 加导入端点。
- Create `src/test/java/africa/zokomart/admin/basedata/CategoryPathResolverTest.java`。
- Create `src/test/java/africa/zokomart/admin/supplierproduct/SupplierProductImportApiTest.java`。

### 前端 `frontend/`
- Modify `src/types/product.d.ts` — 加导入相关类型。
- Modify `src/api/product/supplierProduct.ts` — 加 `apiSupplierProductImport`。
- Create `src/views/product/supplier-product/SupplierProductImportModal.vue` — 导入对话框（含模板生成）。
- Modify `src/views/product/supplier-product/index.vue` — 加「导入」按钮 + 挂载对话框。
- Create `tests/unit/supplier-product-import-modal.spec.ts`。

---

## 后端任务

### Task B1: 加 commons-csv 依赖

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 在 `<dependencies>` 末尾（`spring-boot-starter-test` 之后）加入依赖**

```xml
    <dependency>
      <groupId>org.apache.commons</groupId>
      <artifactId>commons-csv</artifactId>
      <version>1.11.0</version>
    </dependency>
```

- [ ] **Step 2: 验证依赖可解析**

Run: `mvn -q -o dependency:resolve 2>nul || mvn -q dependency:resolve`
Expected: BUILD SUCCESS（首次需联网下载 commons-csv 1.11.0）。

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add commons-csv for supplier-product import

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task B2: 新增导入错误码

**Files:**
- Modify: `src/main/java/africa/zokomart/admin/common/result/ResultCode.java`

- [ ] **Step 1: 在 `BRAND_IN_USE(...)` 行后追加两个码（注意把上一行末尾分号改为逗号）**

把：
```java
    BRAND_IN_USE(40008, "该品牌下已有供应商产品，无法取消授权");
```
改为：
```java
    BRAND_IN_USE(40008, "该品牌下已有供应商产品，无法取消授权"),

    // 供应商产品导入
    IMPORT_FILE_INVALID(40009, "导入文件为空或格式无法解析"),
    IMPORT_TOO_MANY_ROWS(40010, "导入行数超过上限（最多 1000 行）");
```

- [ ] **Step 2: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/africa/zokomart/admin/common/result/ResultCode.java
git commit -m "feat(common): add import result codes

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task B3: 分类路径解析器（纯函数，TDD）

**Files:**
- Create: `src/main/java/africa/zokomart/admin/module/basedata/util/CategoryPathResolver.java`
- Test: `src/test/java/africa/zokomart/admin/basedata/CategoryPathResolverTest.java`

- [ ] **Step 1: 写失败测试**

```java
package africa.zokomart.admin.basedata;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.util.CategoryPathResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CategoryPathResolverTest {

    private Category cat(long id, Long parentId, String name) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(parentId);
        c.setName(name);
        return c;
    }

    /** 数据：Electronics(1,parent0) > Phones(2,parent1); Appliances(3,0) 与 Appliances(4,2) 同名不同父 */
    private List<Category> data() {
        return List.of(
                cat(1, 0L, "Electronics"),
                cat(2, 1L, "Phones"),
                cat(3, 0L, "Appliances"),
                cat(4, 2L, "Appliances"));
    }

    @Test
    void blank_path_returns_null() {
        assertNull(CategoryPathResolver.resolve(data(), null));
        assertNull(CategoryPathResolver.resolve(data(), "   "));
    }

    @Test
    void resolves_nested_path() {
        assertEquals(2L, CategoryPathResolver.resolve(data(), "Electronics>Phones"));
        assertEquals(1L, CategoryPathResolver.resolve(data(), "Electronics"));
    }

    @Test
    void duplicate_name_disambiguated_by_path() {
        // 顶层 Appliances=3；Electronics>Phones>Appliances=4
        assertEquals(3L, CategoryPathResolver.resolve(data(), "Appliances"));
        assertEquals(4L, CategoryPathResolver.resolve(data(), "Electronics>Phones>Appliances"));
    }

    @Test
    void not_found_throws() {
        assertThrows(BusinessException.class,
                () -> CategoryPathResolver.resolve(data(), "Electronics>Nope"));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -q -Dtest=CategoryPathResolverTest test`
Expected: 编译失败 / 找不到 `CategoryPathResolver`。

- [ ] **Step 3: 实现解析器**

```java
package africa.zokomart.admin.module.basedata.util;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.entity.Category;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 "父>子" 分类路径解析为唯一 categoryId。空路径返回 null；
 * 未命中或某级出现重名（无法唯一确定）抛 BusinessException。
 */
public final class CategoryPathResolver {

    private CategoryPathResolver() {
    }

    public static Long resolve(List<Category> all, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            long pid = c.getParentId() == null ? 0L : c.getParentId();
            byParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(c);
        }
        long parent = 0L;
        Long current = null;
        String[] segments = path.split(">");
        for (String raw : segments) {
            String seg = raw.trim();
            if (seg.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "分类路径非法: " + path);
            }
            List<Category> matched = byParent.getOrDefault(parent, List.of()).stream()
                    .filter(c -> seg.equals(c.getName()))
                    .toList();
            if (matched.size() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "分类未找到或重名: " + path);
            }
            current = matched.get(0).getId();
            parent = current;
        }
        return current;
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `mvn -q -Dtest=CategoryPathResolverTest test`
Expected: BUILD SUCCESS，4 个用例通过。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/basedata/util/CategoryPathResolver.java src/test/java/africa/zokomart/admin/basedata/CategoryPathResolverTest.java
git commit -m "feat(basedata): category path resolver (parent>child)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task B4: 导入结果 VO

**Files:**
- Create: `src/main/java/africa/zokomart/admin/module/supplierproduct/vo/ImportRowError.java`
- Create: `src/main/java/africa/zokomart/admin/module/supplierproduct/vo/SupplierProductImportResultVO.java`

- [ ] **Step 1: 创建 ImportRowError**

```java
package africa.zokomart.admin.module.supplierproduct.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 导入失败行：行号（按 Excel 习惯，表头为第 1 行）、产品编码、原因。 */
@Data
@AllArgsConstructor
public class ImportRowError {
    private int row;
    private String productCode;
    private String reason;
}
```

- [ ] **Step 2: 创建 SupplierProductImportResultVO**

```java
package africa.zokomart.admin.module.supplierproduct.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 导入结果汇总。total=数据行数；created/updated/skipped/failed 之和应等于 total。 */
@Data
public class SupplierProductImportResultVO {
    private int total;
    private int created;
    private int updated;
    private int skipped;
    private int failed;
    private List<ImportRowError> errors = new ArrayList<>();
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/supplierproduct/vo/ImportRowError.java src/main/java/africa/zokomart/admin/module/supplierproduct/vo/SupplierProductImportResultVO.java
git commit -m "feat(supplierproduct): import result VOs

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task B5: SupplierProductService 增 findBySupplierAndCode

**Files:**
- Modify: `src/main/java/africa/zokomart/admin/module/supplierproduct/service/SupplierProductService.java`
- Modify: `src/main/java/africa/zokomart/admin/module/supplierproduct/service/impl/SupplierProductServiceImpl.java`

- [ ] **Step 1: 接口加方法**

在 `SupplierProductService` 接口中、`existsByCategoryId` 之后加入：
```java

    /** 按 (供应商, 产品编码) 定位现有产品；不存在返回 null（导入覆盖模式用）。 */
    SupplierProduct findBySupplierAndCode(Long supplierId, String productCode);
```

- [ ] **Step 2: 实现类加方法**

在 `SupplierProductServiceImpl` 中、`existsByCategoryId(...)` 方法之后加入：
```java

    @Override
    public SupplierProduct findBySupplierAndCode(Long supplierId, String productCode) {
        return getOne(Wrappers.<SupplierProduct>lambdaQuery()
                .eq(SupplierProduct::getSupplierId, supplierId)
                .eq(SupplierProduct::getProductCode, productCode), false);
    }
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/supplierproduct/service/SupplierProductService.java src/main/java/africa/zokomart/admin/module/supplierproduct/service/impl/SupplierProductServiceImpl.java
git commit -m "feat(supplierproduct): findBySupplierAndCode lookup

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task B6: 导入服务（接口 + 实现）

**Files:**
- Create: `src/main/java/africa/zokomart/admin/module/supplierproduct/service/SupplierProductImportService.java`
- Create: `src/main/java/africa/zokomart/admin/module/supplierproduct/service/impl/SupplierProductImportServiceImpl.java`

- [ ] **Step 1: 创建接口**

```java
package africa.zokomart.admin.module.supplierproduct.service;

import africa.zokomart.admin.module.supplierproduct.vo.SupplierProductImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface SupplierProductImportService {

    /**
     * 批量导入供应商产品。
     * @param supplierId 目标供应商
     * @param brandId    目标品牌（必须已授权给该供应商）
     * @param mode       "skip"（默认，编码已存在则跳过）或 "overwrite"（覆盖更新）
     * @param file       CSV 文件（UTF-8，中文表头）
     */
    SupplierProductImportResultVO importCsv(Long supplierId, Long brandId, String mode, MultipartFile file);
}
```

- [ ] **Step 2: 创建实现**

```java
package africa.zokomart.admin.module.supplierproduct.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.entity.Brand;
import africa.zokomart.admin.module.basedata.entity.Category;
import africa.zokomart.admin.module.basedata.entity.Supplier;
import africa.zokomart.admin.module.basedata.mapper.CategoryMapper;
import africa.zokomart.admin.module.basedata.service.BrandService;
import africa.zokomart.admin.module.basedata.service.SupplierBrandService;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.basedata.util.CategoryPathResolver;
import africa.zokomart.admin.module.supplierproduct.dto.SupplierProductSaveDTO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductImportService;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductService;
import africa.zokomart.admin.module.supplierproduct.vo.ImportRowError;
import africa.zokomart.admin.module.supplierproduct.vo.SupplierProductImportResultVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SupplierProductImportServiceImpl implements SupplierProductImportService {

    private static final int MAX_ROWS = 1000;
    private static final String H_NAME = "产品名称";
    private static final String H_CODE = "产品编码";
    private static final String H_CATEGORY = "分类路径";
    private static final String H_WHOLESALE = "批发价";
    private static final String H_RETAIL = "零售价";
    private static final String H_MOQ = "最小采购量";
    private static final String H_IMAGE = "图片URL";
    private static final String H_REMARK = "备注";

    private final SupplierProductService supplierProductService;
    private final SupplierBrandService supplierBrandService;
    private final SupplierService supplierService;
    private final BrandService brandService;
    private final CategoryMapper categoryMapper;

    @Override
    public SupplierProductImportResultVO importCsv(Long supplierId, Long brandId, String mode, MultipartFile file) {
        // 1) 整体前置校验
        Supplier supplier = supplierService.getById(supplierId);
        if (supplier == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        Brand brand = brandService.getById(brandId);
        if (brand == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "品牌不存在");
        }
        if (!supplierBrandService.isAuthorized(supplierId, brandId)) {
            throw new BusinessException(ResultCode.BRAND_NOT_AUTHORIZED);
        }
        boolean overwrite = "overwrite".equalsIgnoreCase(mode);

        List<CSVRecord> records = parse(file);
        List<Category> categories = categoryMapper.selectList(null);

        SupplierProductImportResultVO result = new SupplierProductImportResultVO();
        result.setTotal(records.size());
        Set<String> seenCodes = new HashSet<>();

        for (CSVRecord rec : records) {
            int line = (int) rec.getRecordNumber() + 1; // 表头为第 1 行
            String code = get(rec, H_CODE);
            try {
                String name = get(rec, H_NAME);
                if (name.isEmpty()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "产品名称为空");
                }
                if (code.isEmpty()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "产品编码为空");
                }
                if (!seenCodes.add(code)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "文件内编码重复");
                }

                SupplierProductSaveDTO dto = new SupplierProductSaveDTO();
                dto.setSupplierId(supplierId);
                dto.setBrandId(brandId);
                dto.setName(name);
                dto.setProductCode(code);
                dto.setCategoryId(CategoryPathResolver.resolve(categories, get(rec, H_CATEGORY)));
                dto.setWholesalePrice(parsePrice(get(rec, H_WHOLESALE), "批发价"));
                dto.setRetailPrice(parsePrice(get(rec, H_RETAIL), "零售价"));
                dto.setMinPurchaseQty(parseMoq(get(rec, H_MOQ)));
                dto.setImageUrl(emptyToNull(get(rec, H_IMAGE)));
                dto.setRemark(emptyToNull(get(rec, H_REMARK)));
                dto.setStatus(1);

                SupplierProduct existing = supplierProductService.findBySupplierAndCode(supplierId, code);
                if (existing != null) {
                    if (!overwrite) {
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }
                    dto.setId(existing.getId());
                    supplierProductService.updateSupplierProduct(dto);
                    result.setUpdated(result.getUpdated() + 1);
                } else {
                    supplierProductService.createSupplierProduct(dto);
                    result.setCreated(result.getCreated() + 1);
                }
            } catch (BusinessException e) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add(new ImportRowError(line, code, e.getMessage()));
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add(new ImportRowError(line, code, "行处理异常: " + e.getMessage()));
            }
        }
        return result;
    }

    private List<CSVRecord> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(stripBom(file.getBytes())), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build())) {
            if (!parser.getHeaderMap().containsKey(H_NAME) || !parser.getHeaderMap().containsKey(H_CODE)) {
                throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
            }
            List<CSVRecord> records = parser.getRecords();
            if (records.size() > MAX_ROWS) {
                throw new BusinessException(ResultCode.IMPORT_TOO_MANY_ROWS);
            }
            return records;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
    }

    private static byte[] stripBom(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            byte[] out = new byte[b.length - 3];
            System.arraycopy(b, 3, out, 0, out.length);
            return out;
        }
        return b;
    }

    /** 取列值并 trim；列不存在或为空返回 ""。 */
    private static String get(CSVRecord rec, String column) {
        if (!rec.isMapped(column) || !rec.isSet(column)) {
            return "";
        }
        String v = rec.get(column);
        return v == null ? "" : v.trim();
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal parsePrice(String s, String label) {
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal v;
        try {
            v = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, label + "非法: " + s);
        }
        if (v.signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, label + "不能为负");
        }
        return v;
    }

    private static Integer parseMoq(String s) {
        if (s.isEmpty()) {
            return 1;
        }
        int v;
        try {
            v = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "最小采购量非法: " + s);
        }
        if (v < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "最小采购量不能小于 1");
        }
        return v;
    }
}
```

- [ ] **Step 3: 编译验证（确认依赖的 `SupplierService`/`BrandService` 接口存在且有 `getById`，由 MyBatis-Plus `IService` 提供）**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。若 `SupplierService`/`BrandService` 包路径不同，按编译报错调整 import（它们位于 `africa.zokomart.admin.module.basedata.service`）。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/supplierproduct/service/SupplierProductImportService.java src/main/java/africa/zokomart/admin/module/supplierproduct/service/impl/SupplierProductImportServiceImpl.java
git commit -m "feat(supplierproduct): CSV import service (best-effort, skip/overwrite)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task B7: 导入端点 + V9 权限迁移

**Files:**
- Modify: `src/main/java/africa/zokomart/admin/module/supplierproduct/controller/SupplierProductController.java`
- Create: `src/main/resources/db/migration/V9__supplier_product_import_perm.sql`

- [ ] **Step 1: V9 迁移**

```sql
-- ===========================================================================
-- V9: 供应商产品 CSV 批量导入权限。
--     菜单按钮 supplierProduct:import（挂"供应商产品" 1110），授予采购员 BUYER(901)。
--     superadmin 走通配 *，无需显式授权。
-- ===========================================================================
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2063, 1110, '导入供应商产品', 3, 'supplierProduct:import', NULL, NULL, NULL, 5, 1, 1, NOW(), 0, 0);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT 901 * 100000 + m.id, 901, m.id, NOW() FROM sys_menu m WHERE m.id = 2063;
```

- [ ] **Step 2: 控制器加端点**

在 `SupplierProductController` 中加字段与端点。先在类顶部已有 `private final SupplierProductService supplierProductService;` 之后加：
```java
    private final africa.zokomart.admin.module.supplierproduct.service.SupplierProductImportService supplierProductImportService;
```
再在 `create(...)` 方法之后加：
```java
    @PostMapping("/api/supplier-products/import")
    @SaCheckPermission("supplierProduct:import")
    public Result<africa.zokomart.admin.module.supplierproduct.vo.SupplierProductImportResultVO> importCsv(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("supplierId") Long supplierId,
            @RequestParam("brandId") Long brandId,
            @RequestParam(value = "mode", defaultValue = "skip") String mode) {
        return Result.ok(supplierProductImportService.importCsv(supplierId, brandId, mode, file));
    }
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/supplierproduct/controller/SupplierProductController.java src/main/resources/db/migration/V9__supplier_product_import_perm.sql
git commit -m "feat(supplierproduct): import endpoint + V9 perm seed

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task B8: 导入端点集成测试（TDD）

**Files:**
- Test: `src/test/java/africa/zokomart/admin/supplierproduct/SupplierProductImportApiTest.java`

- [ ] **Step 1: 写测试（自建供应商/品牌/分类，授权品牌；上传含 好行/坏行 的 CSV；断言 created/skipped/failed 及覆盖模式；清理）**

```java
package africa.zokomart.admin.supplierproduct;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 供应商产品 CSV 导入集成测试：尽力导入 + 逐行报告、skip/overwrite、品牌未授权快速失败。
 * 以超管 token 操作；自建数据测试结束清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SupplierProductImportApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private long postForId(String url, String body, String token) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv",
                ("﻿" + content).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void import_best_effort_skip_then_overwrite() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();

        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"IMP_Sup_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"IMP_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        long catId = postForId("/api/categories",
                "{\"name\":\"IMP_Cat_" + ts + "\",\"parentId\":0,\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandIds\":[" + brandId + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        String catName = "IMP_Cat_" + ts;
        // 4 行：1 好(带分类) / 1 名称为空(坏) / 1 分类未找到(坏) / 1 好
        String body = "产品名称,产品编码,分类路径,批发价,零售价,最小采购量,图片URL,备注\n"
                + "好货A," + "A" + ts + "," + catName + ",100,200,5,,ok\n"
                + ",B" + ts + ",,1,2,1,,缺名称\n"
                + "好货C,C" + ts + ",不存在的分类,1,2,1,,坏分类\n"
                + "好货D,D" + ts + ",,1,2,1,,ok\n";

        MvcResult r = mvc.perform(multipart("/api/supplier-products/import").file(csv(body))
                        .param("supplierId", String.valueOf(supplierId))
                        .param("brandId", String.valueOf(brandId))
                        .param("mode", "skip")
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.failed").value(2))
                .andReturn();
        // 坏行行号应为 3 与 4（表头第 1 行）
        String json = r.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"row\":3"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"row\":4"));

        // 再次 skip 同文件：两条好行已存在 → skipped=2
        mvc.perform(multipart("/api/supplier-products/import").file(csv(body))
                        .param("supplierId", String.valueOf(supplierId))
                        .param("brandId", String.valueOf(brandId))
                        .param("mode", "skip")
                        .header("Authorization", t))
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.skipped").value(2))
                .andExpect(jsonPath("$.data.failed").value(2));

        // overwrite：好行 A 改零售价 → updated 计入
        String body2 = "产品名称,产品编码,分类路径,批发价,零售价,最小采购量,图片URL,备注\n"
                + "好货A改名,A" + ts + ",,100,999,5,,upd\n";
        mvc.perform(multipart("/api/supplier-products/import").file(csv(body2))
                        .param("supplierId", String.valueOf(supplierId))
                        .param("brandId", String.valueOf(brandId))
                        .param("mode", "overwrite")
                        .header("Authorization", t))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        // 清理：删该供应商下所有产品（按编码查询页拿到后逐个删）
        for (String code : new String[]{"A" + ts, "C" + ts, "D" + ts}) {
            MvcResult pr = mvc.perform(get("/api/supplier-products").header("Authorization", t)
                            .param("supplierId", String.valueOf(supplierId)).param("keyword", code))
                    .andReturn();
            var recs = om.readTree(pr.getResponse().getContentAsString()).at("/data/records");
            for (var n : recs) {
                mvc.perform(delete("/api/supplier-products/" + n.at("/id").asLong()).header("Authorization", t));
            }
        }
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
        mvc.perform(delete("/api/categories/" + catId).header("Authorization", t));
    }

    @Test
    void import_rejects_unauthorized_brand() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"IMP_Sup2_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"IMP_Brand2_" + ts + "\",\"sort\":1,\"status\":1}", t);
        // 未授权该品牌 → 整请求失败 40007
        mvc.perform(multipart("/api/supplier-products/import")
                        .file(csv("产品名称,产品编码,分类路径,批发价,零售价,最小采购量,图片URL,备注\nX,X" + ts + ",,1,1,1,,\n"))
                        .param("supplierId", String.valueOf(supplierId))
                        .param("brandId", String.valueOf(brandId))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(40007));

        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `mvn -q -Dtest=SupplierProductImportApiTest test`
Expected: BUILD SUCCESS，两个用例通过（首跑会触发 V9 迁移建权限）。若失败，按断言定位（常见：行号偏移、计数）。

- [ ] **Step 3: Commit**

```bash
git add src/test/java/africa/zokomart/admin/supplierproduct/SupplierProductImportApiTest.java
git commit -m "test(supplierproduct): CSV import api (best-effort/skip/overwrite/auth)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

- [ ] **Step 4: 跑全量后端测试确认无回归**

Run: `mvn -q test`
Expected: BUILD SUCCESS。

---

## 前端任务

### Task F0: 前端建分支

**Files:** （无文件改动）

- [ ] **Step 1: 切到 main 并建特性分支**

Run（在 `frontend/` 下）：
```bash
git checkout main && git checkout -b feat/supplier-product-import
```
Expected: `Switched to a new branch 'feat/supplier-product-import'`。

---

### Task F1: 导入相关类型

**Files:**
- Modify: `frontend/src/types/product.d.ts`

- [ ] **Step 1: 在文件末尾追加类型**

```typescript

// ---- 供应商产品 CSV 导入 ----
export type ImportMode = 'skip' | 'overwrite';

export interface ImportRowError {
  row: number;
  productCode: string | null;
  reason: string;
}

export interface SupplierProductImportResult {
  total: number;
  created: number;
  updated: number;
  skipped: number;
  failed: number;
  errors: ImportRowError[];
}
```

- [ ] **Step 2: 类型检查**

Run（在 `frontend/`）：`pnpm exec vue-tsc --noEmit`
Expected: 无新增类型错误（若项目无该脚本，跳过，靠后续 build 验证）。

- [ ] **Step 3: Commit**

```bash
git add src/types/product.d.ts
git commit -m "feat(types): supplier-product import result types

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task F2: 导入 API

**Files:**
- Modify: `frontend/src/api/product/supplierProduct.ts`

- [ ] **Step 1: 顶部 import 增加类型**

把：
```typescript
import type {
  SupplierProductVO,
  SupplierProductSaveDTO,
  SupplierProductQuery,
} from '@/types/product';
```
改为：
```typescript
import type {
  SupplierProductVO,
  SupplierProductSaveDTO,
  SupplierProductQuery,
  SupplierProductImportResult,
} from '@/types/product';
```

- [ ] **Step 2: 文件末尾加导入方法（multipart，复用 request 实例，拦截器会解包出 data）**

```typescript

// CSV 批量导入：multipart（file + supplierId + brandId + mode）
export const apiSupplierProductImport = (form: FormData) =>
  request.post('/supplier-products/import', form) as unknown as Promise<SupplierProductImportResult>;
```

注意：文件顶部已 `import { http } from '@/utils/request';`，需改为同时引入 `request`：
把 `import { http } from '@/utils/request';` 改为 `import { http, request } from '@/utils/request';`（`request` 已在该模块导出）。

- [ ] **Step 3: 类型检查/构建片段验证**

Run（在 `frontend/`）：`pnpm exec vue-tsc --noEmit`
Expected: 无新增错误。

- [ ] **Step 4: Commit**

```bash
git add src/api/product/supplierProduct.ts
git commit -m "feat(api): supplier-product CSV import endpoint

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task F3: 导入对话框组件（含模板生成）

**Files:**
- Create: `frontend/src/views/product/supplier-product/SupplierProductImportModal.vue`

- [ ] **Step 1: 创建组件**

```vue
<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { UploadProps } from 'ant-design-vue';
import type { SelectOption } from '@/components/SchemaForm.vue';
import { apiSupplierProductImport } from '@/api/product/supplierProduct';
import { apiAuthorizedBrands } from '@/api/basedata/supplierBrand';
import type { ImportMode, SupplierProductImportResult } from '@/types/product';
import type { Id } from '@/types/api';

const props = defineProps<{
  open: boolean;
  supplierOptions: SelectOption[];
  defaultSupplierId?: Id | null;
}>();
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'imported'): void;
}>();

const TEMPLATE_HEADERS = ['产品名称', '产品编码', '分类路径', '批发价', '零售价', '最小采购量', '图片URL', '备注'];

const form = reactive<{ supplierId?: Id; brandId?: Id; mode: ImportMode }>({ mode: 'skip' });
const brandOptions = ref<SelectOption[]>([]);
const file = ref<File | null>(null);
const submitting = ref(false);
const result = ref<SupplierProductImportResult | null>(null);

async function loadBrands(supplierId?: Id) {
  brandOptions.value = [];
  form.brandId = undefined;
  if (supplierId == null) return;
  const list = await apiAuthorizedBrands(supplierId);
  brandOptions.value = list.map((b) => ({ label: b.brandName, value: b.brandId }));
  if (brandOptions.value.length === 0) {
    message.warning('该供应商暂无已授权品牌，请先在供应商管理里授权');
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      result.value = null;
      file.value = null;
      form.mode = 'skip';
      form.supplierId = (props.defaultSupplierId ?? undefined) as Id | undefined;
      loadBrands(form.supplierId);
    }
  },
);
watch(() => form.supplierId, (v) => loadBrands(v));

const beforeUpload: UploadProps['beforeUpload'] = (f) => {
  const ok = f.name.toLowerCase().endsWith('.csv');
  if (!ok) message.error('只能上传 .csv 文件');
  else file.value = f as unknown as File;
  return false; // 阻止自动上传，仅暂存
};

function downloadTemplate() {
  const sample = ['示例冰箱', 'HR-BCD-201', 'Electronics>Phones', '1200.00', '1599.00', '5', '', '样例备注'];
  const csv = '﻿' + [TEMPLATE_HEADERS.join(','), sample.join(',')].join('\r\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'supplier_product_template.csv';
  a.click();
  URL.revokeObjectURL(a.href);
}

async function onSubmit() {
  if (form.supplierId == null) return message.warning('请选择供应商');
  if (form.brandId == null) return message.warning('请选择品牌');
  if (!file.value) return message.warning('请选择 CSV 文件');
  const fd = new FormData();
  fd.append('file', file.value);
  fd.append('supplierId', String(form.supplierId));
  fd.append('brandId', String(form.brandId));
  fd.append('mode', form.mode);
  submitting.value = true;
  try {
    result.value = await apiSupplierProductImport(fd);
    message.success(`导入完成：新增 ${result.value.created}，更新 ${result.value.updated}，跳过 ${result.value.skipped}，失败 ${result.value.failed}`);
    emit('imported');
  } finally {
    submitting.value = false;
  }
}

function onClose() {
  emit('update:open', false);
}

defineExpose({ form, brandOptions, beforeUpload, downloadTemplate, onSubmit, result });
</script>

<template>
  <a-modal :open="open" title="导入供应商产品" :width="720" :confirm-loading="submitting" @cancel="onClose">
    <a-form layout="vertical">
      <a-form-item label="供应商" required>
        <a-select v-model:value="form.supplierId" :options="supplierOptions" placeholder="选择供应商" show-search
          option-filter-prop="label" style="width: 100%" />
      </a-form-item>
      <a-form-item label="品牌（仅列已授权）" required>
        <a-select v-model:value="form.brandId" :options="brandOptions" placeholder="选择品牌" style="width: 100%" />
      </a-form-item>
      <a-form-item label="编码已存在时">
        <a-radio-group v-model:value="form.mode">
          <a-radio-button value="skip">跳过</a-radio-button>
          <a-radio-button value="overwrite">覆盖更新</a-radio-button>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="CSV 文件">
        <a-space direction="vertical" style="width: 100%">
          <a-upload :before-upload="beforeUpload" :max-count="1" accept=".csv" :show-upload-list="true">
            <a-button data-test="pick-csv">选择 CSV 文件</a-button>
          </a-upload>
          <a @click="downloadTemplate">下载模板</a>
        </a-space>
      </a-form-item>
    </a-form>

    <div v-if="result" class="mt-2">
      <a-descriptions size="small" :column="4" bordered>
        <a-descriptions-item label="总行数">{{ result.total }}</a-descriptions-item>
        <a-descriptions-item label="新增">{{ result.created }}</a-descriptions-item>
        <a-descriptions-item label="更新">{{ result.updated }}</a-descriptions-item>
        <a-descriptions-item label="跳过">{{ result.skipped }}</a-descriptions-item>
        <a-descriptions-item label="失败">{{ result.failed }}</a-descriptions-item>
      </a-descriptions>
      <a-table v-if="result.errors.length" class="mt-2" size="small" :pagination="false"
        :data-source="result.errors"
        :columns="[
          { title: '行号', dataIndex: 'row', width: 80 },
          { title: '产品编码', dataIndex: 'productCode', width: 160 },
          { title: '原因', dataIndex: 'reason' },
        ]" row-key="row" />
    </div>

    <template #footer>
      <a-space>
        <a-button @click="onClose">关闭</a-button>
        <a-button type="primary" :loading="submitting" data-test="do-import" @click="onSubmit">开始导入</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
```

- [ ] **Step 2: 类型检查**

Run（在 `frontend/`）：`pnpm exec vue-tsc --noEmit`
Expected: 无新增错误（确认 `apiAuthorizedBrands` 从 `@/api/basedata/supplierBrand` 导出、`SupplierBrandVO` 有 `brandId`/`brandName`——见现有 `SupplierBrandDrawer.vue` 用法）。

- [ ] **Step 3: Commit**

```bash
git add src/views/product/supplier-product/SupplierProductImportModal.vue
git commit -m "feat(supplier-product): import modal (supplier/brand/mode + template + result)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task F4: 接入供应商产品页

**Files:**
- Modify: `frontend/src/views/product/supplier-product/index.vue`

- [ ] **Step 1: 脚本区引入组件与状态**

在 `<script setup>` 顶部 import 区（`import CascadeFilter ...` 之后）加：
```typescript
import SupplierProductImportModal from './SupplierProductImportModal.vue';
```
在 `const submitting = ref(false);` 之后加：
```typescript
const importOpen = ref(false);
function openImport() {
  importOpen.value = true;
}
function onImported() {
  tableRef.value?.reload();
}
```
并把 `defineExpose({ openCreate, openEdit, onSubmit, onDelete, onFilterChange });` 改为：
```typescript
defineExpose({ openCreate, openEdit, onSubmit, onDelete, onFilterChange, openImport, onImported });
```

- [ ] **Step 2: 模板区加按钮 + 挂载对话框**

把"新增供应商产品"按钮所在的 `<div class="mb-3">...</div>` 改为（在按钮后并排加导入按钮）：
```vue
      <div class="mb-3">
        <a-space>
          <a-button
            v-perm="'supplierProduct:create'"
            type="primary"
            data-test="supplier-product-create"
            @click="openCreate"
          >
            新增供应商产品
          </a-button>
          <a-button v-perm="'supplierProduct:import'" data-test="supplier-product-import" @click="openImport">
            导入
          </a-button>
        </a-space>
      </div>
```
在最外层 `</div>` 之前（紧跟现有 `<a-modal>...</a-modal>` 之后）加：
```vue
    <SupplierProductImportModal
      v-model:open="importOpen"
      :supplier-options="supplierOptions"
      :default-supplier-id="filter.supplierId"
      @imported="onImported"
    />
```

- [ ] **Step 3: 类型检查**

Run（在 `frontend/`）：`pnpm exec vue-tsc --noEmit`
Expected: 无新增错误。

- [ ] **Step 4: Commit**

```bash
git add src/views/product/supplier-product/index.vue
git commit -m "feat(supplier-product): wire import button + modal into page

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task F5: 对话框组件测试（TDD）

**Files:**
- Test: `frontend/tests/unit/supplier-product-import-modal.spec.ts`

- [ ] **Step 1: 写测试（mock api：供应商变更加载授权品牌；提交组装 FormData；结果渲染）**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import SupplierProductImportModal from '@/views/product/supplier-product/SupplierProductImportModal.vue';

const importMock = vi.fn();
const brandsMock = vi.fn();

vi.mock('@/api/product/supplierProduct', () => ({
  apiSupplierProductImport: (form: FormData) => importMock(form),
}));
vi.mock('@/api/basedata/supplierBrand', () => ({
  apiAuthorizedBrands: (id: any) => brandsMock(id),
}));
vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));

describe('SupplierProductImportModal', () => {
  beforeEach(() => {
    importMock.mockReset();
    brandsMock.mockReset();
    brandsMock.mockResolvedValue([{ brandId: '10', brandName: 'Morgan' }]);
  });

  it('loads authorized brands when opened with default supplier', async () => {
    const wrapper = mount(SupplierProductImportModal, {
      props: { open: false, supplierOptions: [{ label: 'S1', value: '1' }], defaultSupplierId: '1' },
      global: { stubs: { 'a-modal': true, 'a-form': true, 'a-form-item': true, 'a-select': true,
        'a-radio-group': true, 'a-radio-button': true, 'a-upload': true, 'a-button': true,
        'a-space': true, 'a-descriptions': true, 'a-descriptions-item': true, 'a-table': true } },
    });
    await wrapper.setProps({ open: true });
    await new Promise((r) => setTimeout(r, 0));
    expect(brandsMock).toHaveBeenCalledWith('1');
    expect(wrapper.vm.brandOptions).toEqual([{ label: 'Morgan', value: '10' }]);
  });

  it('submits a FormData with supplierId/brandId/mode and renders result', async () => {
    importMock.mockResolvedValue({ total: 2, created: 1, updated: 0, skipped: 0, failed: 1,
      errors: [{ row: 3, productCode: 'X', reason: '产品名称为空' }] });
    const wrapper = mount(SupplierProductImportModal, {
      props: { open: true, supplierOptions: [{ label: 'S1', value: '1' }], defaultSupplierId: '1' },
      global: { stubs: { 'a-modal': true, 'a-form': true, 'a-form-item': true, 'a-select': true,
        'a-radio-group': true, 'a-radio-button': true, 'a-upload': true, 'a-button': true,
        'a-space': true, 'a-descriptions': true, 'a-descriptions-item': true, 'a-table': true } },
    });
    await new Promise((r) => setTimeout(r, 0));
    wrapper.vm.form.supplierId = '1';
    wrapper.vm.form.brandId = '10';
    // 暂存一个文件
    (wrapper.vm as any).beforeUpload({ name: 'a.csv' });
    await wrapper.vm.onSubmit();
    expect(importMock).toHaveBeenCalledTimes(1);
    const fd = importMock.mock.calls[0][0] as FormData;
    expect(fd.get('supplierId')).toBe('1');
    expect(fd.get('brandId')).toBe('10');
    expect(fd.get('mode')).toBe('skip');
    expect(wrapper.vm.result?.failed).toBe(1);
  });
});
```

- [ ] **Step 2: 运行测试，确认（先红后绿）**

Run（在 `frontend/`）：`pnpm exec vitest run tests/unit/supplier-product-import-modal.spec.ts`
Expected: 2 个用例通过。若 `beforeUpload` 因 antd 类型签名报错，测试里用 `(wrapper.vm as any).beforeUpload(...)` 调用（已如此）。

- [ ] **Step 3: Commit**

```bash
git add tests/unit/supplier-product-import-modal.spec.ts
git commit -m "test(supplier-product): import modal component test

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

- [ ] **Step 4: 跑全量前端单测 + 构建确认无回归**

Run（在 `frontend/`）：`pnpm test && pnpm build`
Expected: 全部通过、build 成功。

---

## 收尾

- [ ] **后端推送 + PR**：`git push -u origin feat/supplier-product-import`，开 PR 合并到 main。
- [ ] **前端推送 + PR**：同上（前端仓库）。
- [ ] **联调**：按 [[backend-runtime-rebuild]] 提示——后端以 jar 运行，需 `mvn clean package -DskipTests` 重新构建并重启，才能让运行进程包含新端点与 V9 迁移；前端 `pnpm dev` 后在供应商产品页用模板 CSV 实测一次。

---

## Self-Review 结论（计划编写者自查）

- **Spec 覆盖**：尽力导入+逐行报告(B6/B8)、skip/overwrite(B5/B6/B8)、分类路径(B3)、中文表头+模板(F3)、≤1000行(B6)、commons-csv(B1)、状态默认1(B6)、V9权限(B7)、前端对话框/按钮(F3/F4) 均有对应任务。
- **偏差**：模板改前端生成（已在标题区与 F3 说明），不做后端模板端点——已记录。
- **类型一致**：后端 `SupplierProductImportResultVO`/`ImportRowError` 字段与前端 `SupplierProductImportResult`/`ImportRowError` 一致；端点路径 `/api/supplier-products/import` 与前端 `/supplier-products/import`（baseURL 含 `/api`）一致；`apiAuthorizedBrands` 返回 `SupplierBrandVO[]`（`brandId`/`brandName`）与 F3 用法一致。
- **占位符**：无 TODO/TBD；每个改动步骤含完整代码与命令。
