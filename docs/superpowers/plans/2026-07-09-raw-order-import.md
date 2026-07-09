# Raw Order CSV Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import original ("raw") order data from CSV into a new `raw_order` table, with a Raw Orders list page + import modal in the admin frontend.

**Architecture:** New backend module `africa.zokomart.admin.module.raworder` (entity/mapper/service/controller, V15 Flyway migration with table + menu seed) mirroring the proven supplier-product CSV import pattern (Apache Commons CSV, best-effort per-row import, error report). Frontend adds `views/order/raw/` list page + import modal following the customer page / SupplierProductImportModal patterns.

**Tech Stack:** SpringBoot 3.5 + MyBatis-Plus + Sa-Token + Flyway + Apache Commons CSV (backend); Vue3 + Ant Design Vue + vitest (frontend).

**Spec:** `docs/superpowers/specs/2026-07-09-raw-order-import-design.md`

**Repos:** Backend tasks run in `D:\GHANA\claude\admin.zokomart.africa\backend`, frontend tasks in `D:\GHANA\claude\admin.zokomart.africa\frontend`. These are two independent git repos — commit each change to its own repo only.

**Environment notes:**
- Backend build needs JDK 21: `export JAVA_HOME="D:/GHANA/tools/jdk-21"` style override may be needed (see `application-local.yml` conventions); MySQL + Redis must be running locally for `mvn test` (tests are MockMvc integration tests against the real Spring context, logging in as `superadmin`/`Admin@123`).
- CSV column headers `date, brand, price, customer_name, ...` are an external data contract — do not rename.
- Existing ResultCodes reused: `IMPORT_FILE_INVALID` (40009), `IMPORT_TOO_MANY_ROWS` (40010).
- Menu IDs: highest existing are dir 1008, page 1116, button 2065 → this feature uses **1009 / 1117 / 2066 / 2067**.

---

### Task 1: Create feature branches

**Files:** none (git only)

- [ ] **Step 1: Branch backend**

```bash
cd "D:\GHANA\claude\admin.zokomart.africa\backend"
git checkout main && git pull && git checkout -b feat/raw-order-import
```

- [ ] **Step 2: Branch frontend**

```bash
cd "D:\GHANA\claude\admin.zokomart.africa\frontend"
git checkout main && git pull && git checkout -b feat/raw-order-import
```

Expected: both repos on `feat/raw-order-import` (`git branch --show-current`).

---

### Task 2: Backend — V15 migration (table + menu seed)

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__raw_order.sql`

- [ ] **Step 1: Write the migration**

```sql
-- ===========================================================================
-- V15: 原始订单（Raw Order）CSV 导入。
--   独立于 sales_order：按 CSV 原始字段存储，状态仅 PAID / RECIPIENT_REFUSED /
--   UNABLE_TO_CONTACT_RECIPIENT / RECIPIENT_UNABLE_TO_PAY 四种。
--   菜单：目录 1009 订单管理 / 页面 1117 原始订单 / 按钮 2066 raw-order:list、2067 raw-order:import。
--   superadmin 通配自动可见；其它角色后续在角色管理里授权。
-- ===========================================================================

CREATE TABLE raw_order (
    id            BIGINT         NOT NULL COMMENT '主键',
    order_date    DATE           NOT NULL COMMENT '订单日期（CSV date 列）',
    brand         VARCHAR(128)   NOT NULL COMMENT '品牌（原始文本，不关联 brand 表）',
    price         DECIMAL(12, 2) NOT NULL COMMENT '价格',
    customer_name VARCHAR(128)   NOT NULL COMMENT '客户姓名',
    city          VARCHAR(128)   NOT NULL COMMENT '城市',
    address       VARCHAR(512)   NOT NULL COMMENT '地址',
    telephone     VARCHAR(32)    NOT NULL COMMENT '电话',
    product_name  VARCHAR(255)   NOT NULL COMMENT '产品名称',
    product_code  VARCHAR(64)    NOT NULL COMMENT '产品编码',
    quantity      INT            NOT NULL COMMENT '数量',
    status        VARCHAR(40)    NOT NULL
        COMMENT 'PAID/RECIPIENT_REFUSED/UNABLE_TO_CONTACT_RECIPIENT/RECIPIENT_UNABLE_TO_PAY',
    cod           DECIMAL(12, 2) NOT NULL COMMENT '代收货款 COD',
    freight       DECIMAL(12, 2) NOT NULL COMMENT '运费',
    balance       DECIMAL(12, 2) NOT NULL COMMENT '结余',
    create_time   DATETIME                DEFAULT NULL,
    update_time   DATETIME                DEFAULT NULL,
    create_by     BIGINT                  DEFAULT NULL,
    update_by     BIGINT                  DEFAULT NULL,
    deleted       TINYINT        NOT NULL DEFAULT 0,
    version       INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_raw_order_date (order_date),
    KEY idx_raw_order_status (status),
    KEY idx_raw_order_telephone (telephone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '原始订单（CSV 导入）';

INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(1009, 0,    '订单管理',     1, NULL,               '/order',     NULL,              'ant-design:shopping-outlined', 9, 1, 1, NOW(), 0, 0),
(1117, 1009, '原始订单',     2, NULL,               '/order/raw', 'order/raw/index', NULL,                           1, 1, 1, NOW(), 0, 0),
(2066, 1117, '查询原始订单', 3, 'raw-order:list',   NULL,         NULL,              NULL,                           1, 1, 1, NOW(), 0, 0),
(2067, 1117, '导入原始订单', 3, 'raw-order:import', NULL,         NULL,              NULL,                           2, 1, 1, NOW(), 0, 0);
```

- [ ] **Step 2: Verify migration applies**

Run: `cd "D:\GHANA\claude\admin.zokomart.africa\backend" && mvn -q compile && mvn -q test -Dtest=RawOrderApiTest 2>&1 | head -5`

At this point `RawOrderApiTest` doesn't exist yet — instead verify Flyway by running any existing fast test which boots the context, e.g.:

Run: `mvn test -Dtest=SupplierProductImportApiTest`
Expected: test PASSES and the app log shows Flyway migrating to version 15. Confirm table exists: `raw_order` in the dev database.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V15__raw_order.sql
git commit -m "feat(raworder): V15 raw_order table and order menu seed

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Backend — module skeleton (constant / entity / mapper / VOs)

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/constant/RawOrderStatus.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/entity/RawOrder.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/mapper/RawOrderMapper.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/vo/RawOrderVO.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/vo/RawOrderRowError.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/vo/RawOrderImportResultVO.java`

- [ ] **Step 1: RawOrderStatus.java**

```java
package africa.zokomart.admin.module.raworder.constant;

import java.util.Set;

/** 原始订单状态：仅 CSV 允许的四个值，其它一律视为非法。 */
public final class RawOrderStatus {

    private RawOrderStatus() {
    }

    public static final String PAID = "PAID";
    public static final String RECIPIENT_REFUSED = "RECIPIENT_REFUSED";
    public static final String UNABLE_TO_CONTACT_RECIPIENT = "UNABLE_TO_CONTACT_RECIPIENT";
    public static final String RECIPIENT_UNABLE_TO_PAY = "RECIPIENT_UNABLE_TO_PAY";

    public static final Set<String> ALL = Set.of(
            PAID, RECIPIENT_REFUSED, UNABLE_TO_CONTACT_RECIPIENT, RECIPIENT_UNABLE_TO_PAY);
}
```

- [ ] **Step 2: RawOrder.java**

```java
package africa.zokomart.admin.module.raworder.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("raw_order")
public class RawOrder extends BaseEntity {
    private LocalDate orderDate;
    private String brand;
    private BigDecimal price;
    private String customerName;
    private String city;
    private String address;
    private String telephone;
    private String productName;
    private String productCode;
    private Integer quantity;
    private String status;
    private BigDecimal cod;
    private BigDecimal freight;
    private BigDecimal balance;
}
```

- [ ] **Step 3: RawOrderMapper.java**

```java
package africa.zokomart.admin.module.raworder.mapper;

import africa.zokomart.admin.module.raworder.entity.RawOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RawOrderMapper extends BaseMapper<RawOrder> {
}
```

- [ ] **Step 4: RawOrderVO.java**

```java
package africa.zokomart.admin.module.raworder.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RawOrderVO {
    private Long id;
    private LocalDate orderDate;
    private String brand;
    private BigDecimal price;
    private String customerName;
    private String city;
    private String address;
    private String telephone;
    private String productName;
    private String productCode;
    private Integer quantity;
    private String status;
    private BigDecimal cod;
    private BigDecimal freight;
    private BigDecimal balance;
    private LocalDateTime createTime;
}
```

- [ ] **Step 5: RawOrderRowError.java**

```java
package africa.zokomart.admin.module.raworder.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 导入失败行：行号（按 Excel 习惯，表头为第 1 行）、产品编码、原因。 */
@Data
@AllArgsConstructor
public class RawOrderRowError {
    private int row;
    private String productCode;
    private String reason;
}
```

- [ ] **Step 6: RawOrderImportResultVO.java**

```java
package africa.zokomart.admin.module.raworder.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 导入结果汇总。total=数据行数；success + failed 应等于 total。 */
@Data
public class RawOrderImportResultVO {
    private int total;
    private int success;
    private int failed;
    private List<RawOrderRowError> errors = new ArrayList<>();
}
```

- [ ] **Step 7: Compile and commit**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

```bash
git add src/main/java/africa/zokomart/admin/module/raworder
git commit -m "feat(raworder): entity, mapper, status constants, result VOs

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Backend — failing integration test

**Files:**
- Create: `backend/src/test/java/africa/zokomart/admin/raworder/RawOrderApiTest.java`

- [ ] **Step 1: Write the failing test**

Note on row numbers: `CSVRecord.getRecordNumber()` is 1-based over data rows; the service adds 1 so data row 1 reports as row 2 (header = row 1), matching the supplier-product import convention.

```java
package africa.zokomart.admin.raworder;

import africa.zokomart.admin.module.raworder.entity.RawOrder;
import africa.zokomart.admin.module.raworder.mapper.RawOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
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
 * 原始订单 CSV 导入集成测试：尽力导入 + 逐行报告 + 列表查询；表头缺列/空文件整体拒绝。
 * 以超管 token 操作；测试数据按唯一电话号码清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RawOrderApiTest {

    static final String HEADER =
            "date,brand,price,customer_name,city,address,telephone,product_name,product_code,quantity,status,cod,freight,balance";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    RawOrderMapper rawOrderMapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "orders.csv", "text/csv",
                ("﻿" + content).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void import_best_effort_then_page_query() throws Exception {
        String t = token();
        String tel = "0555" + System.currentTimeMillis();

        // 行号（表头=1）：2 好 / 3 坏状态 / 4 坏日期 / 5 数量 0 / 6 缺 customer_name / 7 负 cod / 8 好
        String body = HEADER + "\n"
                + "2026-07-01,Hisense,1200.00,Ama Mensah,Accra,12 High St," + tel + ",Fridge 201,RAW-A,2,PAID,1200.00,50.00,0.00\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,RAW-B,1,SHIPPED,100.00,10.00,0.00\n"
                + "07/01/2026,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,RAW-C,1,PAID,100.00,10.00,0.00\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,RAW-D,0,PAID,100.00,10.00,0.00\n"
                + "2026-07-01,Hisense,100.00,,Accra,addr," + tel + ",TV 32,RAW-E,1,PAID,100.00,10.00,0.00\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,RAW-F,1,PAID,-5.00,10.00,0.00\n"
                + "2026-07-02,Nasco,300.00,Esi Boateng,Kumasi,5 Low Rd," + tel + ",Blender X,RAW-G,1,RECIPIENT_REFUSED,0.00,20.00,300.00\n";

        MvcResult r = mvc.perform(multipart("/api/raw-orders/import").file(csv(body))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.success").value(2))
                .andExpect(jsonPath("$.data.failed").value(5))
                .andReturn();
        String json = r.getResponse().getContentAsString();
        for (int row = 3; row <= 7; row++) {
            Assertions.assertTrue(json.contains("\"row\":" + row), "missing error row " + row);
        }

        // 列表：按电话关键字查 → 2 条；再叠加状态过滤 → 1 条
        mvc.perform(get("/api/raw-orders").header("Authorization", t)
                        .param("keyword", tel))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/raw-orders").header("Authorization", t)
                        .param("keyword", tel).param("status", "PAID"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productCode").value("RAW-A"));
        // 日期范围过滤：只含 07-02 → 1 条
        mvc.perform(get("/api/raw-orders").header("Authorization", t)
                        .param("keyword", tel)
                        .param("dateStart", "2026-07-02").param("dateEnd", "2026-07-02"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("RECIPIENT_REFUSED"));

        // 清理（无删除接口，直接走 mapper 逻辑删除）
        rawOrderMapper.delete(new LambdaQueryWrapper<RawOrder>().eq(RawOrder::getTelephone, tel));
    }

    @Test
    void import_rejects_missing_header_column() throws Exception {
        String t = token();
        // 缺 balance 列 → 整文件拒绝 40009
        String header = HEADER.replace(",balance", "");
        mvc.perform(multipart("/api/raw-orders/import")
                        .file(csv(header + "\n2026-07-01,B,1,C,Accra,A,024,P,PC,1,PAID,1,1\n"))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void import_rejects_empty_file() throws Exception {
        String t = token();
        mvc.perform(multipart("/api/raw-orders/import")
                        .file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void import_rejects_too_many_rows() throws Exception {
        String t = token();
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < 1001; i++) {
            sb.append("2026-07-01,B,1,C,Accra,A,024,P,PC").append(i).append(",1,PAID,1,1,0\n");
        }
        mvc.perform(multipart("/api/raw-orders/import").file(csv(sb.toString()))
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(40010)); // IMPORT_TOO_MANY_ROWS，整体拒绝不入库
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=RawOrderApiTest`
Expected: FAIL — compilation succeeds but requests return 404-ish (`$.code` missing) because no controller exists yet. If it fails to compile because `RawOrderMapper` is missing, Task 3 wasn't completed.

- [ ] **Step 3: Commit the test**

```bash
git add src/test/java/africa/zokomart/admin/raworder/RawOrderApiTest.java
git commit -m "test(raworder): failing integration test for import and list

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Backend — services + controller (make the test pass)

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/service/RawOrderService.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/service/impl/RawOrderServiceImpl.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/service/RawOrderImportService.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/service/impl/RawOrderImportServiceImpl.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/controller/RawOrderController.java`

- [ ] **Step 1: RawOrderService.java**

```java
package africa.zokomart.admin.module.raworder.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.raworder.vo.RawOrderVO;

import java.time.LocalDate;

public interface RawOrderService {

    PageResult<RawOrderVO> page(LocalDate dateStart, LocalDate dateEnd, String status,
                                String brand, String keyword, long current, long size);
}
```

- [ ] **Step 2: RawOrderServiceImpl.java**

```java
package africa.zokomart.admin.module.raworder.service.impl;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.raworder.entity.RawOrder;
import africa.zokomart.admin.module.raworder.mapper.RawOrderMapper;
import africa.zokomart.admin.module.raworder.service.RawOrderService;
import africa.zokomart.admin.module.raworder.vo.RawOrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RawOrderServiceImpl implements RawOrderService {

    private final RawOrderMapper rawOrderMapper;

    @Override
    public PageResult<RawOrderVO> page(LocalDate dateStart, LocalDate dateEnd, String status,
                                       String brand, String keyword, long current, long size) {
        LambdaQueryWrapper<RawOrder> qw = new LambdaQueryWrapper<RawOrder>()
                .ge(dateStart != null, RawOrder::getOrderDate, dateStart)
                .le(dateEnd != null, RawOrder::getOrderDate, dateEnd)
                .eq(StringUtils.hasText(status), RawOrder::getStatus, status)
                .like(StringUtils.hasText(brand), RawOrder::getBrand,
                        brand == null ? null : brand.trim());
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            qw.and(w -> w.like(RawOrder::getCustomerName, k).or().like(RawOrder::getTelephone, k));
        }
        qw.orderByDesc(RawOrder::getOrderDate).orderByDesc(RawOrder::getId);
        IPage<RawOrderVO> page = rawOrderMapper.selectPage(new Page<>(current, size), qw)
                .convert(this::toVo);
        return PageResult.of(page);
    }

    private RawOrderVO toVo(RawOrder o) {
        RawOrderVO vo = new RawOrderVO();
        BeanUtils.copyProperties(o, vo);
        return vo;
    }
}
```

- [ ] **Step 3: RawOrderImportService.java**

```java
package africa.zokomart.admin.module.raworder.service;

import africa.zokomart.admin.module.raworder.vo.RawOrderImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface RawOrderImportService {

    RawOrderImportResultVO importCsv(MultipartFile file);
}
```

- [ ] **Step 4: RawOrderImportServiceImpl.java**

```java
package africa.zokomart.admin.module.raworder.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.raworder.constant.RawOrderStatus;
import africa.zokomart.admin.module.raworder.entity.RawOrder;
import africa.zokomart.admin.module.raworder.mapper.RawOrderMapper;
import africa.zokomart.admin.module.raworder.service.RawOrderImportService;
import africa.zokomart.admin.module.raworder.vo.RawOrderImportResultVO;
import africa.zokomart.admin.module.raworder.vo.RawOrderRowError;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * 原始订单 CSV 导入：UTF-8（容忍 BOM），表头必须含全部 14 列，逐行尽力导入，
 * 坏行记录 {row, productCode, reason} 后继续，不整体回滚。
 */
@Service
@RequiredArgsConstructor
public class RawOrderImportServiceImpl implements RawOrderImportService {

    private static final int MAX_ROWS = 1000;
    /** CSV 表头是外部数据契约，不可改名。 */
    private static final List<String> REQUIRED_HEADERS = List.of(
            "date", "brand", "price", "customer_name", "city", "address", "telephone",
            "product_name", "product_code", "quantity", "status", "cod", "freight", "balance");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final RawOrderMapper rawOrderMapper;

    @Override
    public RawOrderImportResultVO importCsv(MultipartFile file) {
        List<CSVRecord> records = parse(file);
        RawOrderImportResultVO result = new RawOrderImportResultVO();
        result.setTotal(records.size());
        for (CSVRecord rec : records) {
            int row = (int) rec.getRecordNumber() + 1; // 表头为第 1 行
            String code = get(rec, "product_code");
            try {
                rawOrderMapper.insert(toEntity(rec));
                result.setSuccess(result.getSuccess() + 1);
            } catch (BusinessException e) {
                recordError(result, row, code, e.getMessage());
            } catch (Exception e) {
                recordError(result, row, code, "行处理异常: " + e.getMessage());
            }
        }
        return result;
    }

    private RawOrder toEntity(CSVRecord rec) {
        RawOrder o = new RawOrder();
        o.setOrderDate(parseDate(require(rec, "date")));
        o.setBrand(require(rec, "brand"));
        o.setPrice(parseAmount(require(rec, "price"), "price"));
        o.setCustomerName(require(rec, "customer_name"));
        o.setCity(require(rec, "city"));
        o.setAddress(require(rec, "address"));
        o.setTelephone(require(rec, "telephone"));
        o.setProductName(require(rec, "product_name"));
        o.setProductCode(require(rec, "product_code"));
        o.setQuantity(parseQuantity(require(rec, "quantity")));
        o.setStatus(parseStatus(require(rec, "status")));
        o.setCod(parseAmount(require(rec, "cod"), "cod"));
        o.setFreight(parseAmount(require(rec, "freight"), "freight"));
        o.setBalance(parseAmount(require(rec, "balance"), "balance"));
        return o;
    }

    private List<CSVRecord> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(stripBom(file.getBytes())), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build())) {
            for (String h : REQUIRED_HEADERS) {
                if (!parser.getHeaderMap().containsKey(h)) {
                    throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
                }
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

    private static String require(CSVRecord rec, String column) {
        String v = get(rec, column);
        if (v.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, column + " 列为空");
        }
        return v;
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "date 非法（需 yyyy-MM-dd）: " + s);
        }
    }

    private static BigDecimal parseAmount(String s, String label) {
        BigDecimal v;
        try {
            v = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, label + " 非法: " + s);
        }
        if (v.signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, label + " 不能为负: " + s);
        }
        return v;
    }

    private static Integer parseQuantity(String s) {
        int v;
        try {
            v = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "quantity 非法: " + s);
        }
        if (v < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "quantity 不能小于 1: " + s);
        }
        return v;
    }

    private static String parseStatus(String s) {
        if (!RawOrderStatus.ALL.contains(s)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "status 非法: " + s);
        }
        return s;
    }

    private static void recordError(RawOrderImportResultVO result, int row, String code, String reason) {
        result.setFailed(result.getFailed() + 1);
        result.getErrors().add(new RawOrderRowError(row, code, reason));
    }
}
```

- [ ] **Step 5: RawOrderController.java**

```java
package africa.zokomart.admin.module.raworder.controller;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.raworder.service.RawOrderImportService;
import africa.zokomart.admin.module.raworder.service.RawOrderService;
import africa.zokomart.admin.module.raworder.vo.RawOrderImportResultVO;
import africa.zokomart.admin.module.raworder.vo.RawOrderVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "原始订单")
public class RawOrderController {

    private final RawOrderService rawOrderService;
    private final RawOrderImportService rawOrderImportService;

    @GetMapping("/api/raw-orders")
    @SaCheckPermission("raw-order:list")
    public Result<PageResult<RawOrderVO>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(rawOrderService.page(dateStart, dateEnd, status, brand, keyword, current, size));
    }

    @PostMapping("/api/raw-orders/import")
    @SaCheckPermission("raw-order:import")
    public Result<RawOrderImportResultVO> importCsv(@RequestParam("file") MultipartFile file) {
        return Result.ok(rawOrderImportService.importCsv(file));
    }
}
```

- [ ] **Step 6: Run the integration test**

Run: `mvn test -Dtest=RawOrderApiTest`
Expected: 3 tests PASS.

- [ ] **Step 7: Run the full backend suite**

Run: `mvn test`
Expected: BUILD SUCCESS, no failures.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/raworder
git commit -m "feat(raworder): raw order list + best-effort CSV import endpoints

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Frontend — types + API module

**Files:**
- Create: `frontend/src/types/order.d.ts`
- Create: `frontend/src/api/order/rawOrder.ts`

- [ ] **Step 1: types/order.d.ts**

```ts
import type { Id } from './api';

export type RawOrderStatus =
  | 'PAID'
  | 'RECIPIENT_REFUSED'
  | 'UNABLE_TO_CONTACT_RECIPIENT'
  | 'RECIPIENT_UNABLE_TO_PAY';

export interface RawOrderVO {
  id: Id;
  orderDate: string;
  brand: string;
  price: number;
  customerName: string;
  city: string;
  address: string;
  telephone: string;
  productName: string;
  productCode: string;
  quantity: number;
  status: RawOrderStatus;
  cod: number;
  freight: number;
  balance: number;
  createTime: string | null;
}

export interface RawOrderQuery {
  dateStart?: string;
  dateEnd?: string;
  status?: string;
  brand?: string;
  keyword?: string;
  current?: number;
  size?: number;
}

export interface RawOrderImportError {
  row: number;
  productCode: string;
  reason: string;
}

export interface RawOrderImportResult {
  total: number;
  success: number;
  failed: number;
  errors: RawOrderImportError[];
}
```

- [ ] **Step 2: api/order/rawOrder.ts**

```ts
import { http } from '@/utils/request';
import type { PageResult } from '@/types/api';
import type { RawOrderVO, RawOrderQuery, RawOrderImportResult } from '@/types/order';

export const apiRawOrderPage = (q: RawOrderQuery) =>
  http.get<PageResult<RawOrderVO>>('/raw-orders', q);

export const apiRawOrderImport = (form: FormData) =>
  http.post<RawOrderImportResult>('/raw-orders/import', form);
```

- [ ] **Step 3: Typecheck and commit**

Run: `cd "D:\GHANA\claude\admin.zokomart.africa\frontend" && pnpm build`
Expected: build succeeds (vue-tsc + vite).

```bash
git add src/types/order.d.ts src/api/order/rawOrder.ts
git commit -m "feat(order): raw order types and API module

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Frontend — i18n keys

**Files:**
- Modify: `frontend/src/locales/lang/en-US.ts` (append a top-level `rawOrder` section before the file's closing `}`)
- Modify: `frontend/src/locales/lang/zh-CN.ts` (same position)

Note: status display texts in en-US must match the spec exactly (Paid / Refused by Recipient / Unable to Contact Recipient / Recipient Unable to Pay). Keep key order identical in both files.

- [ ] **Step 1: en-US.ts — add section**

```ts
  rawOrder: {
    title: 'Raw Orders',
    date: 'Date',
    brand: 'Brand',
    price: 'Price',
    customerName: 'Customer Name',
    city: 'City',
    address: 'Address',
    telephone: 'Telephone',
    productName: 'Product Name',
    productCode: 'Product Code',
    quantity: 'Qty',
    cod: 'COD',
    freight: 'Freight',
    balance: 'Balance',
    dateRange: 'Date Range',
    keywordPlaceholder: 'Customer Name / Telephone',
    importBtn: 'Import CSV',
    importTitle: 'Import Raw Orders',
    pickCsv: 'Choose CSV file',
    csvOnly: 'Only .csv files are supported',
    selectCsv: 'Please choose a CSV file',
    downloadTemplate: 'Download CSV template',
    startImport: 'Start Import',
    importDone: 'Import finished: {success} succeeded, {failed} failed',
    totalRows: 'Total',
    successRows: 'Success',
    failedRows: 'Failed',
    rowNo: 'Row',
    reason: 'Reason',
    statusText: {
      PAID: 'Paid',
      RECIPIENT_REFUSED: 'Refused by Recipient',
      UNABLE_TO_CONTACT_RECIPIENT: 'Unable to Contact Recipient',
      RECIPIENT_UNABLE_TO_PAY: 'Recipient Unable to Pay',
    },
  },
```

- [ ] **Step 2: zh-CN.ts — add section**

```ts
  rawOrder: {
    title: '原始订单',
    date: '日期',
    brand: '品牌',
    price: '价格',
    customerName: '客户姓名',
    city: '城市',
    address: '地址',
    telephone: '电话',
    productName: '产品名称',
    productCode: '产品编码',
    quantity: '数量',
    cod: '代收货款',
    freight: '运费',
    balance: '结余',
    dateRange: '日期范围',
    keywordPlaceholder: '客户姓名 / 电话',
    importBtn: '导入 CSV',
    importTitle: '导入原始订单',
    pickCsv: '选择 CSV 文件',
    csvOnly: '仅支持 .csv 文件',
    selectCsv: '请先选择 CSV 文件',
    downloadTemplate: '下载 CSV 模板',
    startImport: '开始导入',
    importDone: '导入完成：成功 {success} 条，失败 {failed} 条',
    totalRows: '总行数',
    successRows: '成功',
    failedRows: '失败',
    rowNo: '行号',
    reason: '原因',
    statusText: {
      PAID: '已支付',
      RECIPIENT_REFUSED: '收件人拒收',
      UNABLE_TO_CONTACT_RECIPIENT: '无法联系收件人',
      RECIPIENT_UNABLE_TO_PAY: '收件人无力支付',
    },
  },
```

- [ ] **Step 3: Commit**

```bash
git add src/locales/lang/en-US.ts src/locales/lang/zh-CN.ts
git commit -m "feat(order): raw order i18n keys (en/zh)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: Frontend — failing unit tests for page + modal

**Files:**
- Create: `frontend/tests/unit/raw-order-page.spec.ts`
- Create: `frontend/tests/unit/raw-order-import-modal.spec.ts`

- [ ] **Step 1: raw-order-page.spec.ts**

```ts
import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import RawOrderPage from '@/views/order/raw/index.vue';

vi.mock('@/api/order/rawOrder', () => ({
  apiRawOrderPage: vi.fn().mockResolvedValue({ records: [], total: 0, current: 1, size: 10 }),
  apiRawOrderImport: vi.fn(),
}));

const stubs = {
  'a-card': true, 'a-form': true, 'a-form-item': true, 'a-input': true,
  'a-select': true, 'a-range-picker': true, 'a-button': true, 'a-space': true,
  'a-tag': true, BasicTable: true, RawOrderImportModal: true,
};

describe('RawOrderPage', () => {
  it('search maps dateRange into dateStart/dateEnd, reset clears all', () => {
    const wrapper = mount(RawOrderPage, { global: { stubs } });
    wrapper.vm.searchForm.dateRange = ['2026-07-01', '2026-07-09'];
    wrapper.vm.searchForm.status = 'PAID';
    wrapper.vm.searchForm.brand = 'Hisense';
    wrapper.vm.searchForm.keyword = '0555';
    wrapper.vm.onSearch();
    expect(wrapper.vm.query).toMatchObject({
      dateStart: '2026-07-01',
      dateEnd: '2026-07-09',
      status: 'PAID',
      brand: 'Hisense',
      keyword: '0555',
    });
    expect(wrapper.vm.query.dateRange).toBeUndefined();
    wrapper.vm.onReset();
    expect(wrapper.vm.query).toEqual({});
  });
});
```

- [ ] **Step 2: raw-order-import-modal.spec.ts**

```ts
import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import RawOrderImportModal from '@/views/order/raw/RawOrderImportModal.vue';
import { apiRawOrderImport } from '@/api/order/rawOrder';

vi.mock('@/api/order/rawOrder', () => ({
  apiRawOrderImport: vi.fn().mockResolvedValue({
    total: 2,
    success: 1,
    failed: 1,
    errors: [{ row: 3, productCode: 'RAW-B', reason: 'status 非法: SHIPPED' }],
  }),
}));

const stubs = {
  'a-modal': { template: '<div><slot /><slot name="footer" /></div>' },
  'a-form': true, 'a-form-item': true, 'a-upload': true, 'a-button': true,
  'a-space': true, 'a-descriptions': true, 'a-descriptions-item': true, 'a-table': true,
};

describe('RawOrderImportModal', () => {
  it('beforeUpload keeps csv, rejects others', () => {
    const wrapper = mount(RawOrderImportModal, { props: { open: true }, global: { stubs } });
    expect(wrapper.vm.beforeUpload(new File(['x'], 'orders.csv'))).toBe(false);
    expect(wrapper.vm.file?.name).toBe('orders.csv');
    wrapper.vm.file = null;
    wrapper.vm.beforeUpload(new File(['x'], 'orders.xlsx'));
    expect(wrapper.vm.file).toBeNull();
  });

  it('onSubmit uploads FormData, exposes result, emits imported', async () => {
    const wrapper = mount(RawOrderImportModal, { props: { open: true }, global: { stubs } });
    await wrapper.vm.onSubmit(); // 未选文件 → 不调接口
    expect(apiRawOrderImport).not.toHaveBeenCalled();

    wrapper.vm.beforeUpload(new File(['csv'], 'orders.csv'));
    await wrapper.vm.onSubmit();
    expect(apiRawOrderImport).toHaveBeenCalledTimes(1);
    expect(apiRawOrderImport.mock.calls[0][0]).toBeInstanceOf(FormData);
    expect(wrapper.vm.result?.failed).toBe(1);
    expect(wrapper.emitted('imported')).toBeTruthy();
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `pnpm vitest run tests/unit/raw-order-page.spec.ts tests/unit/raw-order-import-modal.spec.ts`
Expected: FAIL — cannot resolve `@/views/order/raw/index.vue` / `RawOrderImportModal.vue` (files don't exist yet).

- [ ] **Step 4: Commit the tests**

```bash
git add tests/unit/raw-order-page.spec.ts tests/unit/raw-order-import-modal.spec.ts
git commit -m "test(order): failing specs for raw order page and import modal

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 9: Frontend — import modal + list page (make tests pass)

**Files:**
- Create: `frontend/src/views/order/raw/RawOrderImportModal.vue`
- Create: `frontend/src/views/order/raw/index.vue`

- [ ] **Step 1: RawOrderImportModal.vue**

```vue
<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import type { UploadProps } from 'ant-design-vue';
import { apiRawOrderImport } from '@/api/order/rawOrder';
import type { RawOrderImportResult } from '@/types/order';

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'imported'): void;
}>();

const { t } = useI18n();

// CSV 表头是与后端解析约定的数据契约（英文列名），不作 UI 翻译。
const TEMPLATE_HEADERS = [
  'date', 'brand', 'price', 'customer_name', 'city', 'address', 'telephone',
  'product_name', 'product_code', 'quantity', 'status', 'cod', 'freight', 'balance',
];

const file = ref<File | null>(null);
const submitting = ref(false);
const result = ref<RawOrderImportResult | null>(null);

watch(
  () => props.open,
  (v) => {
    if (v) {
      result.value = null;
      file.value = null;
    }
  },
);

const beforeUpload: UploadProps['beforeUpload'] = (f) => {
  const ok = f.name.toLowerCase().endsWith('.csv');
  if (!ok) message.error(t('rawOrder.csvOnly'));
  else file.value = f as unknown as File;
  return false; // 阻止自动上传，仅暂存
};

function downloadTemplate() {
  const sample = [
    '2026-07-01', 'Hisense', '1200.00', 'Ama Mensah', 'Accra', '12 High St', '0244000000',
    'Fridge 201', 'HR-201', '2', 'PAID', '1200.00', '50.00', '0.00',
  ];
  const csv = '﻿' + [TEMPLATE_HEADERS.join(','), sample.join(',')].join('\r\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'raw_order_template.csv';
  a.click();
  URL.revokeObjectURL(a.href);
}

async function onSubmit() {
  if (!file.value) {
    message.warning(t('rawOrder.selectCsv'));
    return;
  }
  const fd = new FormData();
  fd.append('file', file.value);
  submitting.value = true;
  try {
    result.value = await apiRawOrderImport(fd);
    message.success(t('rawOrder.importDone', {
      success: result.value.success,
      failed: result.value.failed,
    }));
    emit('imported');
  } finally {
    submitting.value = false;
  }
}

function onClose() {
  emit('update:open', false);
}

defineExpose({ file, beforeUpload, downloadTemplate, onSubmit, result });
</script>

<template>
  <a-modal :open="open" :title="t('rawOrder.importTitle')" :width="720" :confirm-loading="submitting" @cancel="onClose">
    <a-form layout="vertical">
      <a-form-item :label="t('rawOrder.pickCsv')">
        <a-space direction="vertical" style="width: 100%">
          <a-upload :before-upload="beforeUpload" :max-count="1" accept=".csv" :show-upload-list="true">
            <a-button data-test="pick-csv">{{ t('rawOrder.pickCsv') }}</a-button>
          </a-upload>
          <a @click="downloadTemplate">{{ t('rawOrder.downloadTemplate') }}</a>
        </a-space>
      </a-form-item>
    </a-form>

    <div v-if="result" class="mt-2">
      <a-descriptions size="small" :column="3" bordered>
        <a-descriptions-item :label="t('rawOrder.totalRows')">{{ result.total }}</a-descriptions-item>
        <a-descriptions-item :label="t('rawOrder.successRows')">{{ result.success }}</a-descriptions-item>
        <a-descriptions-item :label="t('rawOrder.failedRows')">{{ result.failed }}</a-descriptions-item>
      </a-descriptions>
      <a-table v-if="result.errors.length" class="mt-2" size="small" :pagination="false"
        :data-source="result.errors"
        :columns="[
          { title: t('rawOrder.rowNo'), dataIndex: 'row', width: 80 },
          { title: t('rawOrder.productCode'), dataIndex: 'productCode', width: 160 },
          { title: t('rawOrder.reason'), dataIndex: 'reason' },
        ]" row-key="row" />
    </div>

    <template #footer>
      <a-space>
        <a-button @click="onClose">{{ t('common.cancel') }}</a-button>
        <a-button type="primary" :loading="submitting" data-test="do-import" @click="onSubmit">{{ t('rawOrder.startImport') }}</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
```

- [ ] **Step 2: index.vue**

```vue
<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import type { TableColumnsType } from 'ant-design-vue';
import BasicTable from '@/components/BasicTable.vue';
import RawOrderImportModal from './RawOrderImportModal.vue';
import { apiRawOrderPage } from '@/api/order/rawOrder';
import type { RawOrderStatus } from '@/types/order';

const { t } = useI18n();

const money = (n: number | null | undefined) => (n ?? 0).toFixed(2);

const STATUS_COLORS: Record<RawOrderStatus, string> = {
  PAID: 'green',
  RECIPIENT_REFUSED: 'red',
  UNABLE_TO_CONTACT_RECIPIENT: 'orange',
  RECIPIENT_UNABLE_TO_PAY: 'volcano',
};
const STATUS_KEYS = Object.keys(STATUS_COLORS) as RawOrderStatus[];

const tableRef = ref<InstanceType<typeof BasicTable>>();
const searchForm = reactive<{
  dateRange?: [string, string];
  status?: string;
  brand?: string;
  keyword?: string;
}>({});
const query = ref<Record<string, any>>({});

const onSearch = () => {
  const { dateRange, ...rest } = searchForm;
  query.value = {
    ...rest,
    dateStart: dateRange?.[0],
    dateEnd: dateRange?.[1],
  };
  // 去掉 undefined 字段，保持 reset 后 query 为 {}
  Object.keys(query.value).forEach((k) => query.value[k] === undefined && delete query.value[k]);
};
const onReset = () => {
  searchForm.dateRange = undefined;
  searchForm.status = undefined;
  searchForm.brand = undefined;
  searchForm.keyword = undefined;
  query.value = {};
};

const statusOptions = computed(() =>
  STATUS_KEYS.map((s) => ({ label: t(`rawOrder.statusText.${s}`), value: s })),
);

const columns = computed<TableColumnsType>(() => [
  { title: t('rawOrder.date'), dataIndex: 'orderDate', key: 'orderDate', width: 110 },
  { title: t('rawOrder.brand'), dataIndex: 'brand', key: 'brand', width: 110 },
  { title: t('rawOrder.productName'), dataIndex: 'productName', key: 'productName' },
  { title: t('rawOrder.productCode'), dataIndex: 'productCode', key: 'productCode', width: 130 },
  { title: t('rawOrder.quantity'), dataIndex: 'quantity', key: 'quantity', width: 70 },
  { title: t('rawOrder.price'), dataIndex: 'price', key: 'price', width: 100 },
  { title: t('rawOrder.customerName'), dataIndex: 'customerName', key: 'customerName', width: 140 },
  { title: t('rawOrder.telephone'), dataIndex: 'telephone', key: 'telephone', width: 130 },
  { title: t('rawOrder.city'), dataIndex: 'city', key: 'city', width: 100 },
  { title: t('common.status'), dataIndex: 'status', key: 'status', width: 200 },
  { title: t('rawOrder.cod'), dataIndex: 'cod', key: 'cod', width: 100 },
  { title: t('rawOrder.freight'), dataIndex: 'freight', key: 'freight', width: 100 },
  { title: t('rawOrder.balance'), dataIndex: 'balance', key: 'balance', width: 100 },
]);

const importOpen = ref(false);
const onImported = () => tableRef.value?.reload();

defineExpose({ searchForm, query, onSearch, onReset, importOpen });
</script>

<template>
  <div>
    <a-card :bordered="false" class="mb-3">
      <a-form layout="inline">
        <a-form-item :label="t('rawOrder.dateRange')">
          <a-range-picker v-model:value="searchForm.dateRange" value-format="YYYY-MM-DD" />
        </a-form-item>
        <a-form-item :label="t('common.status')">
          <a-select
            v-model:value="searchForm.status"
            :options="statusOptions"
            allow-clear
            :placeholder="t('common.pleaseSelect')"
            style="width: 220px"
          />
        </a-form-item>
        <a-form-item :label="t('rawOrder.brand')">
          <a-input v-model:value="searchForm.brand" allow-clear style="width: 160px" @press-enter="onSearch" />
        </a-form-item>
        <a-form-item :label="t('common.keyword')">
          <a-input
            v-model:value="searchForm.keyword"
            :placeholder="t('rawOrder.keywordPlaceholder')"
            allow-clear
            style="width: 200px"
            @press-enter="onSearch"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" data-test="raw-order-search" @click="onSearch">{{ t('common.search') }}</a-button>
            <a-button @click="onReset">{{ t('common.reset') }}</a-button>
            <a-button v-perm="'raw-order:import'" data-test="raw-order-import" @click="importOpen = true">
              {{ t('rawOrder.importBtn') }}
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card :bordered="false">
      <BasicTable ref="tableRef" :columns="columns" :fetcher="apiRawOrderPage" :params="query">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="STATUS_COLORS[record.status as RawOrderStatus] ?? 'default'">
              {{ t(`rawOrder.statusText.${record.status}`) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'price'">{{ money(record.price) }}</template>
          <template v-else-if="column.key === 'cod'">{{ money(record.cod) }}</template>
          <template v-else-if="column.key === 'freight'">{{ money(record.freight) }}</template>
          <template v-else-if="column.key === 'balance'">{{ money(record.balance) }}</template>
        </template>
      </BasicTable>
    </a-card>

    <RawOrderImportModal v-model:open="importOpen" @imported="onImported" />
  </div>
</template>
```

- [ ] **Step 3: Run the new unit tests**

Run: `pnpm vitest run tests/unit/raw-order-page.spec.ts tests/unit/raw-order-import-modal.spec.ts`
Expected: PASS (4 tests). If the page spec fails on the `v-perm` directive being unknown, add `directives: { perm: {} }` to the mount `global` options in `raw-order-page.spec.ts` (check how other specs using `v-perm` pages handle it first, e.g. supplier-product specs).

- [ ] **Step 4: Run the full frontend suite + build**

Run: `pnpm vitest run && pnpm build`
Expected: all tests pass, build succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/views/order/raw tests/unit/raw-order-page.spec.ts tests/unit/raw-order-import-modal.spec.ts
git commit -m "feat(order): raw order list page and CSV import modal

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 10: End-to-end smoke verification

**Files:** none

- [ ] **Step 1: Rebuild and restart the backend jar**

The backend runs as a jar — code changes require rebuild + restart or new endpoints 500 (see memory note). In `backend/`:

```bash
mvn clean package -DskipTests
# 然后按现有方式重启 jar（java -jar target/*.jar，带 local profile）
```

- [ ] **Step 2: Start frontend dev server and verify manually**

In `frontend/`: `pnpm dev`. Log in as superadmin and verify:
1. Sidebar shows 订单管理 → 原始订单.
2. Import a small CSV (use the modal's Download CSV template link, edit a few rows including one bad status) → summary shows correct total/success/failed and the error table lists the bad row.
3. List shows imported rows; date-range / status / brand / keyword filters work; status renders as colored tag with display text.

Expected: all three checks pass. If the menu doesn't appear, confirm Flyway ran V15 against the DB the jar uses.

- [ ] **Step 3: Final full test runs**

```bash
cd "D:\GHANA\claude\admin.zokomart.africa\backend" && mvn test
cd "D:\GHANA\claude\admin.zokomart.africa\frontend" && pnpm vitest run && pnpm build
```

Expected: everything green. Then hand off to superpowers:finishing-a-development-branch (both repos have a `feat/raw-order-import` branch to merge/PR).
