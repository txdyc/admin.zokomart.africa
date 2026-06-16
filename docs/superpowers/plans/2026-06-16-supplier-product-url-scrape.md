# 供应商产品「从 URL 获取」(抓取导入) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在「平台目录 → 供应商产品」页新增「从URL获取」：选供应商+品牌、输入 URL，后端抓取 `morgan.dzncm.com` 价格表并解析，预览后复用导入管线入库。

**Architecture:** 后端用 `java.net.http.HttpClient` 抓取 + Jsoup 解析（Morgan 专用、host 白名单防 SSRF），把抓取行 `ScrapedProductRow` 喂给从既有 CSV 导入抽出的共享 upsert 逻辑（best-effort/skip/overwrite/逐行报告）。给 `supplier_product` 新增 qty_per_box/box_price/stock_status 三列保真。前端新增抓取对话框（抓取→只读预览→确认导入），列表/表单展示新字段。

**Tech Stack:** SpringBoot 3.5 + MyBatis-Plus + Sa-Token + Jsoup；Vue3 + Ant Design Vue + Vitest。

**仓库根目录：** 后端 `D:\GHANA\claude\admin.zokomart.africa\backend`，前端 `D:\GHANA\claude\admin.zokomart.africa\frontend`。Maven 用 JDK 21：`JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" ...`。前端 `pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" <script>`。用绝对路径，勿 `cd`。本机 MySQL(`zokomart_admin`)/Redis 已运行；超管 `superadmin / Admin@123`。

**分支栈：** 两仓 `feat/supplier-product-url-scrape`，栈在 `feat/supplier-product-import` 之上（复用其导入逻辑）。后端分支已建。前端分支见 FF0。

**行号语义：** CSV 导入沿用「表头=第1行，首数据行=2」。URL 导入的行号 = 预览表格中的 1-based 行号（首个产品 = 行 1），以便错误明细与预览表一一对应。

---

## File Structure

### 后端 `backend/`
- Modify `pom.xml` — 加 `org.jsoup:jsoup`。
- Modify `.../common/result/ResultCode.java` — 加 SCRAPE_URL_NOT_ALLOWED(40011)/SCRAPE_FETCH_FAILED(40012)/SCRAPE_EMPTY(40013)。
- Create `src/main/resources/db/migration/V10__supplier_product_scrape_columns.sql`。
- Modify `.../supplierproduct/entity/SupplierProduct.java`、`.../dto/SupplierProductSaveDTO.java`、`.../vo/SupplierProductVO.java` — 加 qtyPerBox/boxPrice/stockStatus。
- Create `.../supplierproduct/dto/ScrapedProductRow.java` — 抓取行（兼作 scrape 出参与 import-scraped 入参元素）。
- Create `.../supplierproduct/scrape/MorganPriceListParser.java` — 纯函数解析器。
- Create `.../supplierproduct/scrape/ScrapeProperties.java` — host 白名单配置。
- Create `.../supplierproduct/service/SupplierProductScrapeService.java` + `impl/SupplierProductScrapeServiceImpl.java`。
- Modify `.../supplierproduct/service/SupplierProductImportService.java` + `impl/SupplierProductImportServiceImpl.java` — 抽 `assertImportable`/`upsertRow`，加 `importScrapedRows`。
- Create `.../supplierproduct/dto/ScrapeRequest.java`、`.../dto/ImportScrapedRequest.java`。
- Modify `.../supplierproduct/controller/SupplierProductController.java` — 加 `/scrape`、`/import-scraped` 端点。
- Modify `src/main/resources/application.yml` — 加 `app.scrape.allowed-hosts`。
- Create tests: `.../supplierproduct/MorganPriceListParserTest.java`（+ `src/test/resources/morgan-sample.html`）、`.../supplierproduct/SupplierProductScrapeApiTest.java`。

### 前端 `frontend/`
- Modify `src/types/product.d.ts` — 加 `ScrapedProductRow`，扩展 VO/SaveDTO。
- Modify `src/api/product/supplierProduct.ts` — 加 `apiScrapeProducts`、`apiImportScraped`。
- Create `src/views/product/supplier-product/SupplierProductScrapeModal.vue`。
- Modify `src/views/product/supplier-product/index.vue` — 「从URL获取」按钮+挂载；列表加 3 列；新增/编辑表单加 3 字段。
- Create `tests/unit/supplier-product-scrape-modal.spec.ts`。

---

## 后端任务

### Task BB1: 加 jsoup 依赖

**Files:** Modify `pom.xml`

- [ ] **Step 1: 在 `<dependencies>` 内 `commons-csv` 之后加入**

```xml
    <dependency>
      <groupId>org.jsoup</groupId>
      <artifactId>jsoup</artifactId>
      <version>1.17.2</version>
    </dependency>
```

- [ ] **Step 2: 验证解析**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" dependency:resolve`
Expected: BUILD SUCCESS（下载 jsoup 1.17.2）。

- [ ] **Step 3: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add pom.xml
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "build: add jsoup for URL scraping

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task BB2: 抓取错误码

**Files:** Modify `src/main/java/africa/zokomart/admin/common/result/ResultCode.java`

- [ ] **Step 1: 把 `IMPORT_TOO_MANY_ROWS(...)` 行末分号改为逗号并追加 3 个码**

把：
```java
    IMPORT_TOO_MANY_ROWS(40010, "导入行数超过上限（最多 1000 行）");
```
改为：
```java
    IMPORT_TOO_MANY_ROWS(40010, "导入行数超过上限（最多 1000 行）"),

    // 供应商产品 URL 抓取
    SCRAPE_URL_NOT_ALLOWED(40011, "不允许抓取该 URL"),
    SCRAPE_FETCH_FAILED(40012, "抓取目标页失败"),
    SCRAPE_EMPTY(40013, "未从目标页解析到产品");
```

- [ ] **Step 2: 编译**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/common/result/ResultCode.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(common): scrape result codes

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task BB3: V10 迁移 + 实体/DTO/VO 新增 3 字段

**Files:**
- Create `src/main/resources/db/migration/V10__supplier_product_scrape_columns.sql`
- Modify `.../supplierproduct/entity/SupplierProduct.java`、`.../dto/SupplierProductSaveDTO.java`、`.../vo/SupplierProductVO.java`

- [ ] **Step 1: 迁移文件**

```sql
-- ===========================================================================
-- V10: 供应商产品新增「每箱数量 / 整箱价 / 库存状态」三列，用于 URL 抓取导入保真。
-- ===========================================================================
ALTER TABLE supplier_product
  ADD COLUMN qty_per_box  INT            DEFAULT NULL COMMENT '每箱数量',
  ADD COLUMN box_price    DECIMAL(12,2)  DEFAULT NULL COMMENT '整箱价 (GH)',
  ADD COLUMN stock_status VARCHAR(64)    DEFAULT NULL COMMENT '库存状态文本';
```

- [ ] **Step 2: 实体加字段**

在 `SupplierProduct.java` 的 `private String remark;` 之后加：
```java
    private Integer qtyPerBox;
    private BigDecimal boxPrice;
    private String stockStatus;
```
（`BigDecimal` 已 import。）

- [ ] **Step 3: SaveDTO 加字段**

在 `SupplierProductSaveDTO.java` 的 `private String remark;` 之后加：
```java
    private Integer qtyPerBox;
    private BigDecimal boxPrice;
    private String stockStatus;
```

- [ ] **Step 4: VO 加字段**

在 `SupplierProductVO.java` 的 `private String remark;` 之后加：
```java
    private Integer qtyPerBox;
    private BigDecimal boxPrice;
    private String stockStatus;
```

- [ ] **Step 5: 编译**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/resources/db/migration/V10__supplier_product_scrape_columns.sql src/main/java/africa/zokomart/admin/module/supplierproduct/entity/SupplierProduct.java src/main/java/africa/zokomart/admin/module/supplierproduct/dto/SupplierProductSaveDTO.java src/main/java/africa/zokomart/admin/module/supplierproduct/vo/SupplierProductVO.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(supplierproduct): add qtyPerBox/boxPrice/stockStatus columns (V10)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task BB4: ScrapedProductRow

**Files:** Create `.../supplierproduct/dto/ScrapedProductRow.java`

- [ ] **Step 1: 创建**

```java
package africa.zokomart.admin.module.supplierproduct.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 从供应商价格表 URL 抓取到的一行（scrape 出参，import-scraped 入参元素）。 */
@Data
public class ScrapedProductRow {
    private String productName;
    private String productCode;
    private Integer qtyPerBox;
    private String imageUrl;
    private BigDecimal unitPrice;
    private BigDecimal boxPrice;
    private String stockStatus;
}
```

- [ ] **Step 2: 编译**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/supplierproduct/dto/ScrapedProductRow.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(supplierproduct): ScrapedProductRow dto

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task BB5: Morgan 解析器（TDD）

**Files:**
- Create `src/test/resources/morgan-sample.html`
- Test `src/test/java/africa/zokomart/admin/supplierproduct/MorganPriceListParserTest.java`
- Create `.../supplierproduct/scrape/MorganPriceListParser.java`

- [ ] **Step 1: 测试夹具 HTML**（`src/test/resources/morgan-sample.html`，含 2 有效行 + 1 缺名行）

```html
<table><thead><tr><th>Serial No.</th><th>Product Name</th><th>Product Code</th><th>Qty/Box</th><th>Product Image</th><th>Unit Price (GH)</th><th>Box Price (GH)</th><th>Stock Status</th><th>Order Qty</th><th>Total</th></tr></thead>
<tbody>
<tr><td>0</td><td>Electric Juicer</td><td>JC-3028S</td><td><span id="Qty">6</span> PC/BOX</td><td><img src="/uploadfile/thumb/x/200x200_auto.jpg" onclick="showDownloadOptions(this)" data-image-large="/uploadfile/202601/eafe.jpg"></td><td><span>220</span></td><td><span>1320</span></td><td><span class="stock-full">Stock Sufficient</span></td><td><input></td><td>0</td></tr>
<tr><td>1</td><td>Blender</td><td>BL-100</td><td><span id="Qty">12</span> PC/BOX</td><td><img src="/t.jpg" data-image-large="/uploadfile/202602/blend.jpg"></td><td><span>90</span></td><td><span>1080</span></td><td><span class="stock-less">Stock Less</span></td><td><input></td><td>0</td></tr>
<tr><td>2</td><td></td><td>NO-NAME</td><td><span id="Qty">1</span> PC/BOX</td><td><img data-image-large="/x.jpg"></td><td><span>1</span></td><td><span>1</span></td><td><span>x</span></td><td><input></td><td>0</td></tr>
</tbody></table>
```

- [ ] **Step 2: 写失败测试**

```java
package africa.zokomart.admin.supplierproduct;

import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
import africa.zokomart.admin.module.supplierproduct.scrape.MorganPriceListParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MorganPriceListParserTest {

    private String fixture() throws Exception {
        return Files.readString(Path.of("src/test/resources/morgan-sample.html"), StandardCharsets.UTF_8);
    }

    @Test
    void parses_rows_skips_missing_name_and_absolutizes_image() throws Exception {
        List<ScrapedProductRow> rows =
                MorganPriceListParser.parse(fixture(), "https://morgan.dzncm.com/price81469/");

        assertEquals(2, rows.size(), "缺名称的第三行应被跳过");

        ScrapedProductRow a = rows.get(0);
        assertEquals("Electric Juicer", a.getProductName());
        assertEquals("JC-3028S", a.getProductCode());
        assertEquals(6, a.getQtyPerBox());
        assertEquals("https://morgan.dzncm.com/uploadfile/202601/eafe.jpg", a.getImageUrl());
        assertEquals(0, new BigDecimal("220").compareTo(a.getUnitPrice()));
        assertEquals(0, new BigDecimal("1320").compareTo(a.getBoxPrice()));
        assertEquals("Stock Sufficient", a.getStockStatus());

        ScrapedProductRow b = rows.get(1);
        assertEquals("Blender", b.getProductName());
        assertEquals(12, b.getQtyPerBox());
        assertEquals("Stock Less", b.getStockStatus());
    }
}
```

- [ ] **Step 3: 运行，确认失败**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -Dtest=MorganPriceListParserTest test`
Expected: 编译失败 / 找不到 `MorganPriceListParser`。

- [ ] **Step 4: 实现解析器**

```java
package africa.zokomart.admin.module.supplierproduct.scrape;

import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Morgan 价格表（morgan.dzncm.com）专用解析器：把表格 HTML 解析为产品行。
 * 弹框产品图取缩略图 img 的 data-image-large 属性，按 baseUrl 补全为绝对 URL。
 * 纯函数，不联网。
 */
public final class MorganPriceListParser {

    private static final Pattern INT = Pattern.compile("\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    private MorganPriceListParser() {
    }

    public static List<ScrapedProductRow> parse(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        List<ScrapedProductRow> out = new ArrayList<>();
        for (Element tr : doc.select("table tbody tr")) {
            Elements tds = tr.select("td");
            if (tds.size() < 8) {
                continue;
            }
            String name = tds.get(1).text().trim();
            String code = tds.get(2).text().trim();
            if (name.isEmpty() || code.isEmpty()) {
                continue;
            }
            ScrapedProductRow r = new ScrapedProductRow();
            r.setProductName(name);
            r.setProductCode(code);
            r.setQtyPerBox(firstInt(tds.get(3).text()));
            Element img = tds.get(4).selectFirst("img[data-image-large]");
            if (img != null) {
                r.setImageUrl(img.absUrl("data-image-large"));
            }
            r.setUnitPrice(firstDecimal(tds.get(5).text()));
            r.setBoxPrice(firstDecimal(tds.get(6).text()));
            String stock = tds.get(7).text().trim();
            r.setStockStatus(stock.isEmpty() ? null : stock);
            out.add(r);
        }
        return out;
    }

    private static Integer firstInt(String s) {
        Matcher m = INT.matcher(s);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    private static BigDecimal firstDecimal(String s) {
        Matcher m = DECIMAL.matcher(s);
        return m.find() ? new BigDecimal(m.group()) : null;
    }
}
```

- [ ] **Step 5: 运行，确认通过**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -Dtest=MorganPriceListParserTest test`
Expected: BUILD SUCCESS，1 用例通过。

- [ ] **Step 6: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/supplierproduct/scrape/MorganPriceListParser.java src/test/java/africa/zokomart/admin/supplierproduct/MorganPriceListParserTest.java src/test/resources/morgan-sample.html
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(supplierproduct): Morgan price-list parser + test

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task BB6: 抓取 service（含 host 白名单）

**Files:**
- Create `.../supplierproduct/scrape/ScrapeProperties.java`
- Create `.../supplierproduct/service/SupplierProductScrapeService.java`
- Create `.../supplierproduct/service/impl/SupplierProductScrapeServiceImpl.java`
- Modify `src/main/resources/application.yml`

- [ ] **Step 1: 配置属性类**

```java
package africa.zokomart.admin.module.supplierproduct.scrape;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** 抓取白名单：仅允许这些 host 被抓取（防 SSRF）。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.scrape")
public class ScrapeProperties {
    private List<String> allowedHosts = List.of("morgan.dzncm.com");
}
```

- [ ] **Step 2: application.yml 加配置**

在 `app:` 节点下（与 `upload:` 同级）加：
```yaml
  scrape:
    allowed-hosts:
      - morgan.dzncm.com
```

- [ ] **Step 3: service 接口**

```java
package africa.zokomart.admin.module.supplierproduct.service;

import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;

import java.util.List;

public interface SupplierProductScrapeService {

    /** 抓取并解析供应商价格表 URL（host 受白名单限制），返回产品行（不入库）。 */
    List<ScrapedProductRow> scrape(String url);
}
```

- [ ] **Step 4: service 实现**

```java
package africa.zokomart.admin.module.supplierproduct.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
import africa.zokomart.admin.module.supplierproduct.scrape.MorganPriceListParser;
import africa.zokomart.admin.module.supplierproduct.scrape.ScrapeProperties;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductScrapeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SupplierProductScrapeServiceImpl implements SupplierProductScrapeService {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

    private final ScrapeProperties props;

    @Override
    public List<ScrapedProductRow> scrape(String url) {
        URI uri = validate(url);
        String html = fetch(uri);
        List<ScrapedProductRow> rows = MorganPriceListParser.parse(html, url);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.SCRAPE_EMPTY);
        }
        return rows;
    }

    private URI validate(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SCRAPE_URL_NOT_ALLOWED);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null) {
            throw new BusinessException(ResultCode.SCRAPE_URL_NOT_ALLOWED);
        }
        boolean allowed = props.getAllowedHosts().stream()
                .anyMatch(h -> h.equalsIgnoreCase(host.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new BusinessException(ResultCode.SCRAPE_URL_NOT_ALLOWED);
        }
        return uri;
    }

    private String fetch(URI uri) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA)
                .GET()
                .build();
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BusinessException(ResultCode.SCRAPE_FETCH_FAILED);
            }
            return resp.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SCRAPE_FETCH_FAILED);
        }
    }
}
```

- [ ] **Step 5: 编译**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/supplierproduct/scrape/ScrapeProperties.java src/main/java/africa/zokomart/admin/module/supplierproduct/service/SupplierProductScrapeService.java src/main/java/africa/zokomart/admin/module/supplierproduct/service/impl/SupplierProductScrapeServiceImpl.java src/main/resources/application.yml
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(supplierproduct): scrape service with host allowlist

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task BB7: 抽共享 upsert + 新增 importScrapedRows

**Files:**
- Modify `.../supplierproduct/service/SupplierProductImportService.java`
- Modify `.../supplierproduct/service/impl/SupplierProductImportServiceImpl.java`

- [ ] **Step 1: 接口加方法**

在 `SupplierProductImportService` 接口（已有 `importCsv`）中加：
```java

    /** 导入抓取到的行（best-effort，skip/overwrite），复用与 CSV 相同的 upsert 规则。 */
    africa.zokomart.admin.module.supplierproduct.vo.SupplierProductImportResultVO importScrapedRows(
            Long supplierId, Long brandId, String mode,
            java.util.List<africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow> rows);
```
（若 `SupplierProductImportResultVO` 已 import 则用短名；这里用全名避免依赖现有 import 行。）

- [ ] **Step 2: 重构实现 —— 抽 `assertImportable` 与 `upsertRow`，`importCsv` 改用之，新增 `importScrapedRows`**

在 `SupplierProductImportServiceImpl` 中：

(a) 顶部 import 增加：
```java
import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
```

(b) 把 `importCsv` 方法体中「整体前置校验」三段（supplier/brand/authorized）替换为一行调用，并在循环内改用 `upsertRow`。将 `importCsv` 改为：
```java
    @Override
    public SupplierProductImportResultVO importCsv(Long supplierId, Long brandId, String mode, MultipartFile file) {
        assertImportable(supplierId, brandId);
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
                SupplierProductSaveDTO dto = new SupplierProductSaveDTO();
                dto.setName(get(rec, H_NAME));
                dto.setProductCode(code);
                dto.setCategoryId(CategoryPathResolver.resolve(categories, get(rec, H_CATEGORY)));
                dto.setWholesalePrice(parsePrice(get(rec, H_WHOLESALE), "批发价"));
                dto.setRetailPrice(parsePrice(get(rec, H_RETAIL), "零售价"));
                dto.setMinPurchaseQty(parseMoq(get(rec, H_MOQ)));
                dto.setImageUrl(emptyToNull(get(rec, H_IMAGE)));
                dto.setRemark(emptyToNull(get(rec, H_REMARK)));
                applyOutcome(upsertRow(supplierId, brandId, overwrite, seenCodes, dto), result);
            } catch (BusinessException e) {
                recordError(result, line, code, e.getMessage());
            } catch (Exception e) {
                recordError(result, line, code, "行处理异常: " + e.getMessage());
            }
        }
        return result;
    }
```

(c) 新增 `importScrapedRows`（放在 `importCsv` 之后）：
```java
    @Override
    public SupplierProductImportResultVO importScrapedRows(Long supplierId, Long brandId, String mode,
                                                           List<ScrapedProductRow> rows) {
        assertImportable(supplierId, brandId);
        boolean overwrite = "overwrite".equalsIgnoreCase(mode);

        SupplierProductImportResultVO result = new SupplierProductImportResultVO();
        result.setTotal(rows == null ? 0 : rows.size());
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        Set<String> seenCodes = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            ScrapedProductRow row = rows.get(i);
            int line = i + 1; // URL 导入行号 = 预览表第 i+1 行
            String code = row.getProductCode();
            try {
                SupplierProductSaveDTO dto = new SupplierProductSaveDTO();
                dto.setName(row.getProductName());
                dto.setProductCode(code);
                dto.setWholesalePrice(row.getUnitPrice());
                dto.setImageUrl(row.getImageUrl());
                dto.setQtyPerBox(row.getQtyPerBox());
                dto.setBoxPrice(row.getBoxPrice());
                dto.setStockStatus(row.getStockStatus());
                applyOutcome(upsertRow(supplierId, brandId, overwrite, seenCodes, dto), result);
            } catch (BusinessException e) {
                recordError(result, line, code, e.getMessage());
            } catch (Exception e) {
                recordError(result, line, code, "行处理异常: " + e.getMessage());
            }
        }
        return result;
    }
```

(d) 新增私有枚举与辅助方法（放在 `parse(...)` 之前）：
```java
    private enum RowOutcome { CREATED, UPDATED, SKIPPED }

    private void assertImportable(Long supplierId, Long brandId) {
        if (supplierService.getById(supplierId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        if (brandService.getById(brandId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "品牌不存在");
        }
        if (!supplierBrandService.isAuthorized(supplierId, brandId)) {
            throw new BusinessException(ResultCode.BRAND_NOT_AUTHORIZED);
        }
    }

    /** 校验必填+批次内查重，按 skip/overwrite 落库；返回结果或抛业务异常。设置 supplierId/brandId/status。 */
    private RowOutcome upsertRow(Long supplierId, Long brandId, boolean overwrite,
                                 Set<String> seenCodes, SupplierProductSaveDTO dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        String code = dto.getProductCode() == null ? "" : dto.getProductCode().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品名称为空");
        }
        if (code.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品编码为空");
        }
        if (!seenCodes.add(code)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同一批次内编码重复");
        }
        dto.setSupplierId(supplierId);
        dto.setBrandId(brandId);
        if (dto.getStatus() == null) {
            dto.setStatus(1);
        }
        SupplierProduct existing = supplierProductService.findBySupplierAndCode(supplierId, code);
        if (existing != null) {
            if (!overwrite) {
                return RowOutcome.SKIPPED;
            }
            dto.setId(existing.getId());
            supplierProductService.updateSupplierProduct(dto);
            return RowOutcome.UPDATED;
        }
        supplierProductService.createSupplierProduct(dto);
        return RowOutcome.CREATED;
    }

    private void applyOutcome(RowOutcome o, SupplierProductImportResultVO result) {
        switch (o) {
            case CREATED -> result.setCreated(result.getCreated() + 1);
            case UPDATED -> result.setUpdated(result.getUpdated() + 1);
            case SKIPPED -> result.setSkipped(result.getSkipped() + 1);
        }
    }

    private void recordError(SupplierProductImportResultVO result, int line, String code, String reason) {
        result.setFailed(result.getFailed() + 1);
        result.getErrors().add(new ImportRowError(line, code, reason));
    }
```

> 注意：CSV 路径里 `dto.setStatus(1)` 现由 `upsertRow` 兜底（之前显式 set），行为一致。原来的「文件内编码重复」文案改为「同一批次内编码重复」，CSV 测试未断言该文案，不受影响。

- [ ] **Step 3: 编译 + 跑 CSV 既有测试确认无回归**

Run:
```
JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -Dtest=SupplierProductImportApiTest test
```
Expected: BUILD SUCCESS，2 用例通过（首次会跑 V10 迁移）。

- [ ] **Step 4: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/supplierproduct/service/SupplierProductImportService.java src/main/java/africa/zokomart/admin/module/supplierproduct/service/impl/SupplierProductImportServiceImpl.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "refactor(supplierproduct): share upsert; add importScrapedRows

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task BB8: 端点（scrape + import-scraped）

**Files:**
- Create `.../supplierproduct/dto/ScrapeRequest.java`
- Create `.../supplierproduct/dto/ImportScrapedRequest.java`
- Modify `.../supplierproduct/controller/SupplierProductController.java`

- [ ] **Step 1: 请求 DTO**

`ScrapeRequest.java`:
```java
package africa.zokomart.admin.module.supplierproduct.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScrapeRequest {
    @NotBlank(message = "URL 不能为空")
    private String url;
}
```

`ImportScrapedRequest.java`:
```java
package africa.zokomart.admin.module.supplierproduct.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ImportScrapedRequest {
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    @NotNull(message = "品牌不能为空")
    private Long brandId;
    private String mode;
    private List<ScrapedProductRow> rows;
}
```

- [ ] **Step 2: 控制器加字段与两个端点**

在 `SupplierProductController` 顶部 import 增加：
```java
import africa.zokomart.admin.module.supplierproduct.dto.ImportScrapedRequest;
import africa.zokomart.admin.module.supplierproduct.dto.ScrapeRequest;
import africa.zokomart.admin.module.supplierproduct.dto.ScrapedProductRow;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductScrapeService;
```
在已有 `private final SupplierProductImportService supplierProductImportService;` 之后加：
```java
    private final SupplierProductScrapeService supplierProductScrapeService;
```
在 `importCsv(...)` 方法之后加：
```java
    @PostMapping("/api/supplier-products/scrape")
    @SaCheckPermission("supplierProduct:import")
    public Result<java.util.List<ScrapedProductRow>> scrape(@Valid @RequestBody ScrapeRequest req) {
        return Result.ok(supplierProductScrapeService.scrape(req.getUrl()));
    }

    @PostMapping("/api/supplier-products/import-scraped")
    @SaCheckPermission("supplierProduct:import")
    public Result<SupplierProductImportResultVO> importScraped(@Valid @RequestBody ImportScrapedRequest req) {
        return Result.ok(supplierProductImportService.importScrapedRows(
                req.getSupplierId(), req.getBrandId(), req.getMode(), req.getRows()));
    }
```
（`SupplierProductImportResultVO` 已在该文件 import；若未，则加 `import africa.zokomart.admin.module.supplierproduct.vo.SupplierProductImportResultVO;`。`@Valid` 已 import。）

- [ ] **Step 3: 编译**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/supplierproduct/dto/ScrapeRequest.java src/main/java/africa/zokomart/admin/module/supplierproduct/dto/ImportScrapedRequest.java src/main/java/africa/zokomart/admin/module/supplierproduct/controller/SupplierProductController.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(supplierproduct): scrape + import-scraped endpoints

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task BB9: API 测试（host 白名单 + import-scraped）

**Files:** Test `src/test/java/africa/zokomart/admin/supplierproduct/SupplierProductScrapeApiTest.java`

> 不做联网 happy-path（避免依赖外网）。覆盖：非白名单 URL 拒绝(40011)；import-scraped 复用 best-effort（好/坏/dup + 新列落库 + skip→overwrite）。

- [ ] **Step 1: 写测试**

```java
package africa.zokomart.admin.supplierproduct;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * URL 抓取/导入集成测试：非白名单 URL 拒绝；import-scraped best-effort + 新列落库 + skip/overwrite。
 * 超管 token；自建数据并清理。不做联网抓取。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SupplierProductScrapeApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

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
    void scrape_rejects_non_allowlisted_host() throws Exception {
        String t = token();
        mvc.perform(post("/api/supplier-products/scrape").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://evil.example.com/x\"}"))
                .andExpect(jsonPath("$.code").value(40011));
        // http 协议也拒绝
        mvc.perform(post("/api/supplier-products/scrape").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://morgan.dzncm.com/x\"}"))
                .andExpect(jsonPath("$.code").value(40011));
    }

    @Test
    void import_scraped_best_effort_and_new_columns() throws Exception {
        String t = token();
        long ts = System.currentTimeMillis();
        long supplierId = postForId("/api/suppliers",
                "{\"name\":\"SC_Sup_" + ts + "\",\"contactPhone\":\"024\",\"status\":1}", t);
        long brandId = postForId("/api/brands",
                "{\"name\":\"SC_Brand_" + ts + "\",\"sort\":1,\"status\":1}", t);
        mvc.perform(put("/api/suppliers/" + supplierId + "/authorized-brands").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandIds\":[" + brandId + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        String c1 = "SC_A_" + ts, c2 = "SC_B_" + ts;
        // 3 行：c1(好,带新列) / c2(好) / c1(批次内重复 -> 失败)
        String rows = "["
                + "{\"productName\":\"Juicer\",\"productCode\":\"" + c1 + "\",\"qtyPerBox\":6,"
                + "\"imageUrl\":\"https://morgan.dzncm.com/uploadfile/202601/eafe.jpg\",\"unitPrice\":220,\"boxPrice\":1320,\"stockStatus\":\"Stock Sufficient\"},"
                + "{\"productName\":\"Blender\",\"productCode\":\"" + c2 + "\",\"qtyPerBox\":12,\"unitPrice\":90,\"boxPrice\":1080,\"stockStatus\":\"Stock Less\"},"
                + "{\"productName\":\"Dup\",\"productCode\":\"" + c1 + "\",\"unitPrice\":1,\"boxPrice\":1}"
                + "]";
        String body = "{\"supplierId\":" + supplierId + ",\"brandId\":" + brandId + ",\"mode\":\"skip\",\"rows\":" + rows + "}";

        mvc.perform(post("/api/supplier-products/import-scraped").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.failed").value(1));

        // 新列落库：查 c1
        MvcResult pr = mvc.perform(get("/api/supplier-products").header("Authorization", t)
                        .param("supplierId", String.valueOf(supplierId)).param("keyword", c1)).andReturn();
        var rec = om.readTree(pr.getResponse().getContentAsString()).at("/data/records/0");
        org.junit.jupiter.api.Assertions.assertEquals(6, rec.at("/qtyPerBox").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("Stock Sufficient", rec.at("/stockStatus").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("1320")
                .compareTo(new java.math.BigDecimal(rec.at("/boxPrice").asText())));

        // 再次 skip：c1/c2 已存在 -> skipped=2，dup 仍失败
        mvc.perform(post("/api/supplier-products/import-scraped").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.skipped").value(2))
                .andExpect(jsonPath("$.data.failed").value(1));

        // overwrite：c1 改单价 -> updated=1
        String body2 = "{\"supplierId\":" + supplierId + ",\"brandId\":" + brandId + ",\"mode\":\"overwrite\",\"rows\":["
                + "{\"productName\":\"Juicer2\",\"productCode\":\"" + c1 + "\",\"unitPrice\":999}]}";
        mvc.perform(post("/api/supplier-products/import-scraped").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        // 清理
        for (String code : new String[]{c1, c2}) {
            MvcResult q = mvc.perform(get("/api/supplier-products").header("Authorization", t)
                    .param("supplierId", String.valueOf(supplierId)).param("keyword", code)).andReturn();
            for (var n : om.readTree(q.getResponse().getContentAsString()).at("/data/records")) {
                mvc.perform(delete("/api/supplier-products/" + n.at("/id").asLong()).header("Authorization", t));
            }
        }
        mvc.perform(delete("/api/suppliers/" + supplierId).header("Authorization", t));
        mvc.perform(delete("/api/brands/" + brandId).header("Authorization", t));
    }
}
```

- [ ] **Step 2: 运行**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -Dtest=SupplierProductScrapeApiTest test`
Expected: BUILD SUCCESS，2 用例通过。

- [ ] **Step 3: 全量回归**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" test`
Expected: BUILD SUCCESS，报告总数（应在既有 57 基础上 +3：解析器 1 + 抓取 API 2）。

- [ ] **Step 4: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/test/java/africa/zokomart/admin/supplierproduct/SupplierProductScrapeApiTest.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "test(supplierproduct): scrape allowlist + import-scraped api

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 前端任务

### Task FF0: 前端建分支（栈在 import 之上）

- [ ] **Step 1:**

Run（在 `frontend/`）：先丢弃自动生成文件的潜在改动，再从 import 分支切出本分支：
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -- src/types/components.d.ts 2>/dev/null; true
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout feat/supplier-product-import
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -b feat/supplier-product-url-scrape
```
Expected: `Switched to a new branch 'feat/supplier-product-url-scrape'`。

---

### Task FF1: 类型

**Files:** Modify `src/types/product.d.ts`

- [ ] **Step 1: 扩展 `SupplierProductVO`**（在 `remark: string | null;` 之后加）

```typescript
  qtyPerBox: number | null;
  boxPrice: number | null;
  stockStatus: string | null;
```

- [ ] **Step 2: 扩展 `SupplierProductSaveDTO`**（在其 `remark?: string | null;` 之后加）

```typescript
  qtyPerBox?: number | null;
  boxPrice?: number | null;
  stockStatus?: string | null;
```

- [ ] **Step 3: 文件末尾加抓取行类型**

```typescript

// ---- 从 URL 抓取的产品行 ----
export interface ScrapedProductRow {
  productName: string;
  productCode: string;
  qtyPerBox: number | null;
  imageUrl: string | null;
  unitPrice: number | null;
  boxPrice: number | null;
  stockStatus: string | null;
}
```

- [ ] **Step 4: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add src/types/product.d.ts
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "feat(types): scraped row + supplier-product extra columns

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task FF2: API

**Files:** Modify `src/api/product/supplierProduct.ts`

- [ ] **Step 1: 顶部类型 import 增加**

把现有 `import type { ... SupplierProductImportResult } from '@/types/product';` 改为追加 `ScrapedProductRow`：
```typescript
import type {
  SupplierProductVO,
  SupplierProductSaveDTO,
  SupplierProductQuery,
  SupplierProductImportResult,
  ScrapedProductRow,
} from '@/types/product';
```

- [ ] **Step 2: 文件末尾加两个方法**

```typescript

// 从 URL 抓取产品（仅解析，不入库）
export const apiScrapeProducts = (url: string) =>
  http.post<ScrapedProductRow[]>('/supplier-products/scrape', { url });

// 导入抓取到的行
export const apiImportScraped = (payload: {
  supplierId: Id;
  brandId: Id;
  mode: 'skip' | 'overwrite';
  rows: ScrapedProductRow[];
}) => http.post<SupplierProductImportResult>('/supplier-products/import-scraped', payload);
```
（`http` 与 `Id` 已在文件顶部 import。）

- [ ] **Step 3: 类型检查**

Run: `pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" exec vue-tsc --noEmit`
Expected: 无新增错误。

- [ ] **Step 4: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add src/api/product/supplierProduct.ts
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "feat(api): scrape + import-scraped endpoints

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task FF3: 抓取对话框组件

**Files:** Create `src/views/product/supplier-product/SupplierProductScrapeModal.vue`

- [ ] **Step 1: 创建组件**

```vue
<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { SelectOption } from '@/components/SchemaForm.vue';
import { apiScrapeProducts, apiImportScraped } from '@/api/product/supplierProduct';
import { apiAuthorizedBrands } from '@/api/basedata/supplierBrand';
import type { ScrapedProductRow, SupplierProductImportResult } from '@/types/product';
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

const form = reactive<{ supplierId?: Id; brandId?: Id; url: string; mode: 'skip' | 'overwrite' }>({
  url: '',
  mode: 'skip',
});
const brandOptions = ref<SelectOption[]>([]);
const rows = ref<ScrapedProductRow[]>([]);
const result = ref<SupplierProductImportResult | null>(null);
const scraping = ref(false);
const importing = ref(false);

async function loadBrands(supplierId?: Id) {
  brandOptions.value = [];
  form.brandId = undefined;
  if (supplierId == null) return;
  const list = await apiAuthorizedBrands(supplierId);
  brandOptions.value = list.map((b) => ({ label: b.brandName ?? String(b.brandId), value: b.brandId }));
  if (brandOptions.value.length === 0) {
    message.warning('该供应商暂无已授权品牌，请先在供应商管理里授权');
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      rows.value = [];
      result.value = null;
      form.url = '';
      form.mode = 'skip';
      form.supplierId = (props.defaultSupplierId ?? undefined) as Id | undefined;
      loadBrands(form.supplierId);
    }
  },
);
watch(() => form.supplierId, (v) => loadBrands(v));

const previewColumns = [
  { title: '#', key: 'idx', width: 50, customRender: ({ index }: { index: number }) => index + 1 },
  { title: '名称', dataIndex: 'productName' },
  { title: '编码', dataIndex: 'productCode', width: 130 },
  { title: '每箱量', dataIndex: 'qtyPerBox', width: 80 },
  { title: '图片', dataIndex: 'imageUrl', key: 'imageUrl', width: 70 },
  { title: '单价', dataIndex: 'unitPrice', width: 80 },
  { title: '箱价', dataIndex: 'boxPrice', width: 90 },
  { title: '库存状态', dataIndex: 'stockStatus', width: 130 },
];

async function onScrape() {
  if (!form.url.trim()) return message.warning('请输入 URL') as unknown as void;
  scraping.value = true;
  result.value = null;
  try {
    rows.value = await apiScrapeProducts(form.url.trim());
    message.success(`抓取到 ${rows.value.length} 条产品`);
  } finally {
    scraping.value = false;
  }
}

async function onImport() {
  if (form.supplierId == null) {
    message.warning('请选择供应商');
    return;
  }
  if (form.brandId == null) {
    message.warning('请选择品牌');
    return;
  }
  if (rows.value.length === 0) {
    message.warning('请先抓取产品');
    return;
  }
  importing.value = true;
  try {
    result.value = await apiImportScraped({
      supplierId: form.supplierId,
      brandId: form.brandId,
      mode: form.mode,
      rows: rows.value,
    });
    message.success(
      `导入完成：新增 ${result.value.created}，更新 ${result.value.updated}，跳过 ${result.value.skipped}，失败 ${result.value.failed}`,
    );
    emit('imported');
  } finally {
    importing.value = false;
  }
}

function onClose() {
  emit('update:open', false);
}

defineExpose({ form, brandOptions, rows, result, onScrape, onImport });
</script>

<template>
  <a-modal :open="open" title="从 URL 获取供应商产品" :width="900" @cancel="onClose">
    <a-form layout="vertical">
      <a-form-item label="供应商" required>
        <a-select v-model:value="form.supplierId" :options="supplierOptions" placeholder="选择供应商"
          show-search option-filter-prop="label" style="width: 100%" />
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
      <a-form-item label="产品列表 URL" required>
        <a-space style="width: 100%">
          <a-input v-model:value="form.url" placeholder="https://morgan.dzncm.com/price81469/" style="width: 520px" />
          <a-button type="primary" :loading="scraping" data-test="do-scrape" @click="onScrape">抓取</a-button>
        </a-space>
      </a-form-item>
    </a-form>

    <a-table v-if="rows.length" size="small" :pagination="{ pageSize: 8 }" :data-source="rows"
      :columns="previewColumns" row-key="productCode" :scroll="{ y: 320 }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'imageUrl'">
          <a-image v-if="record.imageUrl" :src="record.imageUrl" :width="36" />
          <span v-else class="text-gray-400">—</span>
        </template>
      </template>
    </a-table>

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
        <a-button type="primary" :loading="importing" :disabled="rows.length === 0" data-test="do-import-scraped"
          @click="onImport">确认导入</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
```

- [ ] **Step 2: 类型检查**

Run: `pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" exec vue-tsc --noEmit`
Expected: 无新增错误。

- [ ] **Step 3: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add src/views/product/supplier-product/SupplierProductScrapeModal.vue
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "feat(supplier-product): scrape-from-URL modal

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task FF4: 接入页面（按钮 + 列表列 + 表单字段）

**Files:** Modify `src/views/product/supplier-product/index.vue`

- [ ] **Step 1: 脚本区 import 与状态**

在 import 区（`import SupplierProductImportModal ...` 之后）加：
```typescript
import SupplierProductScrapeModal from './SupplierProductScrapeModal.vue';
```
在 `function onImported() { ... }` 之后加：
```typescript
const scrapeOpen = ref(false);
function openScrape() {
  scrapeOpen.value = true;
}
```
把 `defineExpose({ ..., openImport, onImported });` 追加 `openScrape`：
```typescript
defineExpose({ openCreate, openEdit, onSubmit, onDelete, onFilterChange, openImport, onImported, openScrape });
```

- [ ] **Step 2: 列表新增 3 列**

在 `columns` 数组中、`{ title: 'MOQ', ... }` 之后加：
```typescript
  { title: '每箱量', dataIndex: 'qtyPerBox', key: 'qtyPerBox', width: 80 },
  { title: '箱价', dataIndex: 'boxPrice', key: 'boxPrice', width: 90 },
  { title: '库存状态', dataIndex: 'stockStatus', key: 'stockStatus', width: 120 },
```

- [ ] **Step 3: 新增/编辑表单加 3 字段**

在 `formSchema` 数组中、`{ field: 'minPurchaseQty', ... }` 之后加：
```typescript
  { field: 'qtyPerBox', label: '每箱数量', component: 'number', props: { min: 0, precision: 0 } },
  { field: 'boxPrice', label: '整箱价 (GHS)', component: 'number', props: { min: 0, precision: 2 } },
  { field: 'stockStatus', label: '库存状态', component: 'input' },
```
并在 `openEdit` 的 `formInitial.value = { ... }` 对象里、`remark: row.remark,` 之后加：
```typescript
    qtyPerBox: row.qtyPerBox ?? undefined,
    boxPrice: row.boxPrice ?? undefined,
    stockStatus: row.stockStatus ?? undefined,
```

- [ ] **Step 4: 模板区加按钮 + 挂载对话框**

把现有「导入」按钮所在 `<a-space>` 内追加一个按钮（在「导入」之后）：
```vue
          <a-button v-perm="'supplierProduct:import'" data-test="supplier-product-scrape" @click="openScrape">
            从URL获取
          </a-button>
```
在已有 `<SupplierProductImportModal ... />` 之后加：
```vue
    <SupplierProductScrapeModal
      v-model:open="scrapeOpen"
      :supplier-options="supplierOptions"
      :default-supplier-id="filter.supplierId"
      @imported="onImported"
    />
```

- [ ] **Step 5: 类型检查 + 构建**

Run:
```
pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" build
```
Expected: vue-tsc 通过、vite 构建成功。

- [ ] **Step 6: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -- src/types/components.d.ts 2>/dev/null; true
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add src/views/product/supplier-product/index.vue
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "feat(supplier-product): wire scrape button + show new columns/fields

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task FF5: 对话框组件测试

**Files:** Test `tests/unit/supplier-product-scrape-modal.spec.ts`

- [ ] **Step 1: 写测试**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import SupplierProductScrapeModal from '@/views/product/supplier-product/SupplierProductScrapeModal.vue';

const scrapeMock = vi.fn();
const importMock = vi.fn();
const brandsMock = vi.fn();

vi.mock('@/api/product/supplierProduct', () => ({
  apiScrapeProducts: (url: string) => scrapeMock(url),
  apiImportScraped: (p: any) => importMock(p),
}));
vi.mock('@/api/basedata/supplierBrand', () => ({
  apiAuthorizedBrands: (id: any) => brandsMock(id),
}));
vi.mock('ant-design-vue', () => ({ message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }));

const stubs = {
  'a-modal': true, 'a-form': true, 'a-form-item': true, 'a-select': true, 'a-radio-group': true,
  'a-radio-button': true, 'a-input': true, 'a-button': true, 'a-space': true, 'a-table': true,
  'a-image': true, 'a-descriptions': true, 'a-descriptions-item': true,
};

describe('SupplierProductScrapeModal', () => {
  beforeEach(() => {
    scrapeMock.mockReset();
    importMock.mockReset();
    brandsMock.mockReset();
    brandsMock.mockResolvedValue([{ brandId: '10', brandName: 'Morgan' }]);
  });

  it('scrapes and stores rows', async () => {
    scrapeMock.mockResolvedValue([
      { productName: 'Juicer', productCode: 'A1', qtyPerBox: 6, imageUrl: 'x', unitPrice: 220, boxPrice: 1320, stockStatus: 'Stock Sufficient' },
    ]);
    const wrapper = mount(SupplierProductScrapeModal, {
      props: { open: true, supplierOptions: [{ label: 'S1', value: '1' }], defaultSupplierId: '1' },
      global: { stubs },
    });
    await new Promise((r) => setTimeout(r, 0));
    wrapper.vm.form.url = 'https://morgan.dzncm.com/price81469/';
    await wrapper.vm.onScrape();
    expect(scrapeMock).toHaveBeenCalledWith('https://morgan.dzncm.com/price81469/');
    expect(wrapper.vm.rows.length).toBe(1);
  });

  it('imports scraped rows with supplierId/brandId/mode', async () => {
    importMock.mockResolvedValue({ total: 1, created: 1, updated: 0, skipped: 0, failed: 0, errors: [] });
    const wrapper = mount(SupplierProductScrapeModal, {
      props: { open: true, supplierOptions: [{ label: 'S1', value: '1' }], defaultSupplierId: '1' },
      global: { stubs },
    });
    await new Promise((r) => setTimeout(r, 0));
    wrapper.vm.form.supplierId = '1';
    wrapper.vm.form.brandId = '10';
    wrapper.vm.rows = [{ productName: 'Juicer', productCode: 'A1', qtyPerBox: 6, imageUrl: 'x', unitPrice: 220, boxPrice: 1320, stockStatus: 'Stock Sufficient' }];
    await wrapper.vm.onImport();
    expect(importMock).toHaveBeenCalledTimes(1);
    const payload = importMock.mock.calls[0][0];
    expect(payload.supplierId).toBe('1');
    expect(payload.brandId).toBe('10');
    expect(payload.mode).toBe('skip');
    expect(payload.rows.length).toBe(1);
    expect(wrapper.vm.result?.created).toBe(1);
  });
});
```

- [ ] **Step 2: 运行**

Run: `pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" exec vitest run tests/unit/supplier-product-scrape-modal.spec.ts`
Expected: 2 用例通过。

- [ ] **Step 3: 全量单测 + 构建**

Run:
```
pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" test:unit
pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" build
```
Expected: 全部通过、build 成功。

- [ ] **Step 4: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -- src/types/components.d.ts 2>/dev/null; true
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add tests/unit/supplier-product-scrape-modal.spec.ts
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "test(supplier-product): scrape modal component test

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 收尾

- [ ] **联调**：后端重建+重启（jar 跑的是旧码，参见 [[backend-runtime-rebuild]]）后，在供应商产品页用真实 URL `https://morgan.dzncm.com/price81469/` 实测：抓取→预览 136 行（图为弹框大图）→确认导入。
- [ ] **完成分支**：用 superpowers:finishing-a-development-branch（两仓 `feat/supplier-product-url-scrape`，栈在 import 分支之上）。

---

## Self-Review 结论（计划编写者自查）

- **Spec 覆盖**：3 新列+迁移(BB3)、Jsoup(BB1)、解析器取 data-image-large+绝对化(BB5)、抓取 service host 白名单/https/防 SSRF(BB6)、复用导入 importRows→实为共享 assertImportable/upsertRow + importScrapedRows(BB7)、scrape/import-scraped 端点复用 supplierProduct:import(BB8)、错误码(BB2)、前端按钮/对话框/预览/确认/列表列/表单字段(FF1-FF4)、测试(BB5/BB9/FF5) 均覆盖。
- **偏差**：spec 写「importRows(List<DTO>)」，计划落为更安全的 `importScrapedRows(List<ScrapedProductRow>)` + 共享 `assertImportable`/`upsertRow`（保留 CSV 逐行解析错误语义，零回归风险）；URL 行号=预览 1-based 行（非 CSV 的 +2），已在 header「行号语义」说明。均为合理细化。
- **类型一致**：后端 `ScrapedProductRow`(productName/productCode/qtyPerBox/imageUrl/unitPrice/boxPrice/stockStatus) ↔ 前端 `ScrapedProductRow` 字段一致；`importScrapedRows` 入参顺序 (supplierId,brandId,mode,rows) ↔ 控制器调用一致 ↔ 前端 `apiImportScraped` payload 一致；新增 entity/DTO/VO 字段名 qtyPerBox/boxPrice/stockStatus 三处一致；端点路径 `/supplier-products/scrape`、`/import-scraped`（baseURL 含 /api）一致。
- **占位符**：无 TODO/TBD；每个改动步骤含完整代码与命令。
