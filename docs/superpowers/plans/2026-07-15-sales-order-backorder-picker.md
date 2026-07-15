# Sales-order Backorder Picker + V18 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the "Add Sales Order" picker search all supplier products (in-stock first) and backorder out-of-stock items, and grant Sales Support the basedata read permissions needed to filter supplier products.

**Architecture:** New sales-module read endpoint pages over `supplier_product LEFT JOIN inventory_stock` ordered in-stock-first (XML mapper, MP `IPage`). `InventoryStockService.changeStock` gets an `allowNegative` overload used only by the sales-out path so orders can be placed for out-of-stock products (stock goes negative). Frontend swaps the picker fetcher, drops the qty cap, and shows a non-blocking "backorder" tag. A `V18` migration grants role 904 `supplier:list`/`brand:list`/`category:list` as nav-invisible buttons.

**Tech Stack:** Spring Boot 3.5 + MyBatis-Plus + Flyway (MySQL); Sa-Token RBAC; Vue3 + Ant Design Vue.

**Spec reference:** `docs/superpowers/specs/2026-07-15-sales-order-backorder-picker-design.md`

**Build/runtime notes:**
- Backend needs JDK 21: prefix Maven with `export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" &&`.
- Backend runs as a built jar; after backend changes or a new migration, **rebuild + restart** before manual/HTTP verification.
- Tests run Flyway against the dev DB `zokomart_admin` (MySQL + Redis must be up).

---

## Task 1: Backend — `allowNegative` overload on `changeStock` (TDD)

Let the sales-out movement go negative without weakening other callers.

**Files:**
- Modify: `backend/src/main/java/africa/zokomart/admin/module/inventory/service/InventoryStockService.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/inventory/service/impl/InventoryStockServiceImpl.java`
- Test: `backend/src/test/java/africa/zokomart/admin/inventory/StockBackorderServiceTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/africa/zokomart/admin/inventory/StockBackorderServiceTest.java`:

```java
package africa.zokomart.admin.inventory;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.module.inventory.constant.InventoryConst;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** changeStock 的 allowNegative：销售出库可为负（缺货欠货）；其它调用仍禁止负库存。 */
@SpringBootTest
class StockBackorderServiceTest {

    @Autowired
    InventoryStockService stockService;
    @Autowired
    SupplierProductMapper supplierProductMapper;

    private Long newProduct(String tag) {
        SupplierProduct sp = new SupplierProduct();
        sp.setName("BO_" + tag);
        sp.setProductCode("BOC_" + tag);
        sp.setWholesalePrice(new BigDecimal("100"));
        sp.setRetailPrice(new BigDecimal("200"));
        sp.setMinPurchaseQty(1);
        sp.setStatus(1);
        supplierProductMapper.insert(sp);
        return sp.getId();
    }

    @Test
    void allowNegative_true_lets_sales_out_go_below_zero() {
        long ts = System.nanoTime();
        Long spId = newProduct("neg" + ts);
        // no stock row yet -> sell 3 with allowNegative -> quantity = -3
        stockService.changeStock(spId, -3, InventoryConst.TYPE_SALES_OUT,
                InventoryConst.REF_SALES_ORDER, 999L, "SO-" + ts, "backorder", true);
        assertThat(stockService.getQty(spId)).isEqualTo(-3);
    }

    @Test
    void allowNegative_false_still_rejects_going_negative() {
        long ts = System.nanoTime();
        Long spId = newProduct("blk" + ts);
        assertThatThrownBy(() -> stockService.changeStock(spId, -1, InventoryConst.TYPE_MANUAL_ADJUST,
                InventoryConst.REF_MANUAL, null, null, "manual", false))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn -q -Dtest=StockBackorderServiceTest test`
Expected: FAIL to compile — the 8-arg `changeStock(..., boolean)` overload does not exist yet.

- [ ] **Step 3: Add the overload to the interface**

In `backend/src/main/java/africa/zokomart/admin/module/inventory/service/InventoryStockService.java`, keep the existing 7-arg `changeStock` and add the 8-arg overload right after it:

```java
    void changeStock(Long supplierProductId, int qtyChange, String type,
                     String refType, Long refId, String refNo, String remark);

    /** 同上，allowNegative=true 时允许库存为负（销售缺货欠货场景）。 */
    void changeStock(Long supplierProductId, int qtyChange, String type,
                     String refType, Long refId, String refNo, String remark, boolean allowNegative);
```

- [ ] **Step 4: Implement the overload in the impl**

In `backend/src/main/java/africa/zokomart/admin/module/inventory/service/impl/InventoryStockServiceImpl.java`, change the existing `changeStock(...)` method signature to accept `allowNegative`, add a delegating 7-arg method, and gate the two `after < 0` throws on `!allowNegative`. Replace the method header and both guards:

Add a delegating overload immediately above the current method:

```java
    @Override
    public void changeStock(Long supplierProductId, int qtyChange, String type,
                            String refType, Long refId, String refNo, String remark) {
        changeStock(supplierProductId, qtyChange, type, refType, refId, refNo, remark, false);
    }
```

Change the existing method signature from:

```java
    public void changeStock(Long supplierProductId, int qtyChange, String type,
                            String refType, Long refId, String refNo, String remark) {
```

to:

```java
    @Override
    public void changeStock(Long supplierProductId, int qtyChange, String type,
                            String refType, Long refId, String refNo, String remark, boolean allowNegative) {
```

Then change both guards inside that method from:

```java
                if (after < 0) {
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "库存不足");
                }
```

to:

```java
                if (after < 0 && !allowNegative) {
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "库存不足");
                }
```

(There are two such guards — the `stock == null` branch and the `else` branch; update both.)

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn -q -Dtest=StockBackorderServiceTest test`
Expected: PASS (both tests).

- [ ] **Step 6: Commit**

```bash
cd backend
git add src/main/java/africa/zokomart/admin/module/inventory/service/InventoryStockService.java \
        src/main/java/africa/zokomart/admin/module/inventory/service/impl/InventoryStockServiceImpl.java \
        src/test/java/africa/zokomart/admin/inventory/StockBackorderServiceTest.java
git commit -m "feat(inventory): changeStock allowNegative overload for backorder"
```

---

## Task 2: Backend — allow backorder in sales-order creation (TDD)

Remove the pre-check and use `allowNegative=true` for the sales-out decrement.

**Files:**
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/service/impl/SalesOrderServiceImpl.java`
- Test: `backend/src/test/java/africa/zokomart/admin/sales/SalesOrderBackorderApiTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/africa/zokomart/admin/sales/SalesOrderBackorderApiTest.java`:

```java
package africa.zokomart.admin.sales;

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

/** 缺货可下单（backorder）：无库存记录的产品下单成功，库存转负。 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderBackorderApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String login(String u, String p) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}")).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    @Test
    void out_of_stock_product_can_be_backordered() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();
        long supplierId = postForId("/api/suppliers", "{\"name\":\"BOSup_" + ts + "\",\"status\":1}", su);
        // NOTE: no inbound -> product has no inventory_stock row (qty 0)
        long spId = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"BOProd_" + ts
                        + "\",\"productCode\":\"BOC_" + ts + "\",\"wholesalePrice\":100,\"retailPrice\":200,"
                        + "\"minPurchaseQty\":1,\"status\":1}", su);

        // order qty 2 with zero stock -> succeeds (backorder)
        long orderId = postForId("/api/sales-orders",
                "{\"customerName\":\"Kofi\",\"customerPhone\":\"024\",\"customerAddress\":\"Accra\",\"items\":[{\"supplierProductId\":"
                        + spId + ",\"qty\":2}]}", su);
        assert orderId > 0;

        // stock now -2
        mvc.perform(get("/api/inventory/stocks").header("Authorization", su).param("keyword", "BOC_" + ts))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].quantity").value(-2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn -q -Dtest=SalesOrderBackorderApiTest test`
Expected: FAIL — order creation returns `INSUFFICIENT_STOCK` (code != 0) because of the current pre-check, so `postForId` assertion on `$.code == 0` fails.

- [ ] **Step 3: Remove the pre-check and pass allowNegative=true**

In `backend/src/main/java/africa/zokomart/admin/module/sales/service/impl/SalesOrderServiceImpl.java`, inside `create(...)`:

Delete the stock pre-check block (the qty loop's insufficient-stock guard):

```java
            int qty = in.getQty();
            // 预校验库存（实际扣减仍由乐观锁兜底防超卖）
            if (stockService.getQty(sp.getId()) < qty) {
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK,
                        "产品[" + sp.getProductCode() + "] 库存不足");
            }
```

so it becomes just:

```java
            int qty = in.getQty();
```

Then change the sales-out decrement call from:

```java
            stockService.changeStock(item.getSupplierProductId(), -item.getQty(),
                    InventoryConst.TYPE_SALES_OUT, InventoryConst.REF_SALES_ORDER,
                    order.getId(), order.getOrderNo(), "销售出库");
```

to (append `true`):

```java
            stockService.changeStock(item.getSupplierProductId(), -item.getQty(),
                    InventoryConst.TYPE_SALES_OUT, InventoryConst.REF_SALES_ORDER,
                    order.getId(), order.getOrderNo(), "销售出库", true);
```

Leave imports as-is (`BusinessException`/`ResultCode` may now be unused for this path but are still used elsewhere in the file — if the compiler flags an unused import, remove only the now-unused one).

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn -q -Dtest=SalesOrderBackorderApiTest test`
Expected: PASS.

- [ ] **Step 5: Confirm existing sales flows still pass**

Run: `cd backend && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn -q -Dtest=SalesOrderApiTest,SalesFlowServiceTest,SalesOrderDetailIsolationTest test`
Expected: PASS (in-stock happy path unchanged).

- [ ] **Step 6: Commit**

```bash
cd backend
git add src/main/java/africa/zokomart/admin/module/sales/service/impl/SalesOrderServiceImpl.java \
        src/test/java/africa/zokomart/admin/sales/SalesOrderBackorderApiTest.java
git commit -m "feat(sales): allow backorder on order creation (stock may go negative)"
```

---

## Task 3: Backend — `orderable-products` endpoint (TDD)

Product-driven listing, in-stock first, for the picker.

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/vo/OrderableProductVO.java`
- Create: `backend/src/main/java/africa/zokomart/admin/module/sales/mapper/OrderableProductMapper.java`
- Create: `backend/src/main/resources/mapper/OrderableProductMapper.xml`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/service/SalesOrderService.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/service/impl/SalesOrderServiceImpl.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/sales/controller/SalesOrderController.java`
- Test: `backend/src/test/java/africa/zokomart/admin/sales/OrderableProductsApiTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/africa/zokomart/admin/sales/OrderableProductsApiTest.java`:

```java
package africa.zokomart.admin.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** orderable-products：返回全部在售产品（含无库存），有货优先，keyword 命中。 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderableProductsApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String login(String u, String p) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}")).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }
    private long postForId(String url, String body, String t) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data").asLong();
    }

    @Test
    void lists_all_products_instock_first_with_keyword() throws Exception {
        String su = login("superadmin", "Admin@123");
        long ts = System.nanoTime();
        long supplierId = postForId("/api/suppliers", "{\"name\":\"OPSup_" + ts + "\",\"status\":1}", su);
        String kw = "OPKW" + ts;
        // out-of-stock product (no inbound)
        long noStock = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"" + kw + "_zero\",\"productCode\":\"" + kw
                        + "Z\",\"wholesalePrice\":100,\"retailPrice\":200,\"minPurchaseQty\":1,\"status\":1}", su);
        // in-stock product (inbound 5)
        long inStock = postForId("/api/supplier-products",
                "{\"supplierId\":" + supplierId + ",\"name\":\"" + kw + "_five\",\"productCode\":\"" + kw
                        + "F\",\"wholesalePrice\":100,\"retailPrice\":300,\"minPurchaseQty\":1,\"status\":1}", su);
        mvc.perform(put("/api/inventory/stocks/" + inStock).header("Authorization", su)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(jsonPath("$.code").value(0));

        MvcResult r = mvc.perform(get("/api/sales-orders/orderable-products").header("Authorization", su)
                        .param("keyword", kw).param("size", "50"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode recs = om.readTree(r.getResponse().getContentAsString()).at("/data/records");
        // both products present
        assertThat(recs).hasSize(2);
        // in-stock first
        assertThat(recs.get(0).get("supplierProductId").asLong()).isEqualTo(inStock);
        assertThat(recs.get(0).get("quantity").asInt()).isEqualTo(5);
        assertThat(recs.get(0).get("retailPrice").asDouble()).isEqualTo(300.0);
        assertThat(recs.get(1).get("supplierProductId").asLong()).isEqualTo(noStock);
        assertThat(recs.get(1).get("quantity").asInt()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn -q -Dtest=OrderableProductsApiTest test`
Expected: FAIL — endpoint `GET /api/sales-orders/orderable-products` returns 404/error (not implemented).

- [ ] **Step 3: Create the VO**

Create `backend/src/main/java/africa/zokomart/admin/module/sales/vo/OrderableProductVO.java`:

```java
package africa.zokomart.admin.module.sales.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderableProductVO {
    private Long supplierProductId;
    private String productName;
    private String productCode;
    private String supplierName;
    private Integer quantity;      // COALESCE(inventory_stock.quantity, 0)
    private BigDecimal retailPrice;
}
```

- [ ] **Step 4: Create the mapper interface**

Create `backend/src/main/java/africa/zokomart/admin/module/sales/mapper/OrderableProductMapper.java`:

```java
package africa.zokomart.admin.module.sales.mapper;

import africa.zokomart.admin.module.sales.vo.OrderableProductVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 可下单产品：supplier_product LEFT JOIN inventory_stock，有货优先。 */
@Mapper
public interface OrderableProductMapper {

    IPage<OrderableProductVO> pageOrderable(Page<OrderableProductVO> page,
                                            @Param("supplierId") Long supplierId,
                                            @Param("brandId") Long brandId,
                                            @Param("categoryId") Long categoryId,
                                            @Param("kw") String kw);
}
```

- [ ] **Step 5: Create the mapper XML**

Create `backend/src/main/resources/mapper/OrderableProductMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="africa.zokomart.admin.module.sales.mapper.OrderableProductMapper">

    <select id="pageOrderable" resultType="africa.zokomart.admin.module.sales.vo.OrderableProductVO">
        SELECT
            sp.id                        AS supplier_product_id,
            sp.name                      AS product_name,
            sp.product_code              AS product_code,
            sup.name                     AS supplier_name,
            COALESCE(st.quantity, 0)     AS quantity,
            sp.retail_price              AS retail_price
        FROM supplier_product sp
        LEFT JOIN inventory_stock st ON st.supplier_product_id = sp.id AND st.deleted = 0
        LEFT JOIN supplier sup ON sup.id = sp.supplier_id AND sup.deleted = 0
        WHERE sp.deleted = 0 AND sp.status = 1
        <if test="supplierId != null">AND sp.supplier_id = #{supplierId}</if>
        <if test="brandId != null">AND sp.brand_id = #{brandId}</if>
        <if test="categoryId != null">AND sp.category_id = #{categoryId}</if>
        <if test="kw != null and kw != ''">
            AND (sp.name LIKE CONCAT('%', #{kw}, '%')
                 OR sp.product_code LIKE CONCAT('%', #{kw}, '%'))
        </if>
        ORDER BY (COALESCE(st.quantity, 0) > 0) DESC, sp.name ASC, sp.id ASC
    </select>
</mapper>
```

- [ ] **Step 6: Add the service method**

In `backend/src/main/java/africa/zokomart/admin/module/sales/service/SalesOrderService.java`, add:

```java
    /** 可下单产品分页：全部在售产品（含无库存），有货优先。 */
    PageResult<africa.zokomart.admin.module.sales.vo.OrderableProductVO> orderableProducts(
            Long supplierId, Long brandId, Long categoryId, String keyword, long current, long size);
```

In `backend/src/main/java/africa/zokomart/admin/module/sales/service/impl/SalesOrderServiceImpl.java`, add the `OrderableProductMapper` as an injected field (this class uses `@RequiredArgsConstructor`, so add a `private final` field) and implement the method:

```java
    private final africa.zokomart.admin.module.sales.mapper.OrderableProductMapper orderableProductMapper;
```

```java
    @Override
    public PageResult<africa.zokomart.admin.module.sales.vo.OrderableProductVO> orderableProducts(
            Long supplierId, Long brandId, Long categoryId, String keyword, long current, long size) {
        Page<africa.zokomart.admin.module.sales.vo.OrderableProductVO> page = new Page<>(current, size);
        return PageResult.of(orderableProductMapper.pageOrderable(page, supplierId, brandId, categoryId, keyword));
    }
```

(`Page` and `PageResult` are already imported in this file.)

- [ ] **Step 7: Add the controller endpoint**

In `backend/src/main/java/africa/zokomart/admin/module/sales/controller/SalesOrderController.java`, add the import and endpoint:

```java
import africa.zokomart.admin.module.sales.vo.OrderableProductVO;
```

```java
    @GetMapping("/orderable-products")
    @SaCheckPermission("sales:order:create")
    public Result<PageResult<OrderableProductVO>> orderableProducts(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(salesOrderService.orderableProducts(supplierId, brandId, categoryId, keyword, current, size));
    }
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd backend && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn -q -Dtest=OrderableProductsApiTest test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
cd backend
git add src/main/java/africa/zokomart/admin/module/sales/vo/OrderableProductVO.java \
        src/main/java/africa/zokomart/admin/module/sales/mapper/OrderableProductMapper.java \
        src/main/resources/mapper/OrderableProductMapper.xml \
        src/main/java/africa/zokomart/admin/module/sales/service/SalesOrderService.java \
        src/main/java/africa/zokomart/admin/module/sales/service/impl/SalesOrderServiceImpl.java \
        src/main/java/africa/zokomart/admin/module/sales/controller/SalesOrderController.java \
        src/test/java/africa/zokomart/admin/sales/OrderableProductsApiTest.java
git commit -m "feat(sales): orderable-products endpoint (all products, in-stock first)"
```

---

## Task 4: Backend — V18 migration (Sales Support basedata read)

The migration file already exists at
`backend/src/main/resources/db/migration/V18__sales_support_basedata_read.sql`
(grants role 904 `2018 supplier:list`, `2014 brand:list`, `2022 category:list`). This
task applies and verifies it.

**Files:**
- Verify (already created): `backend/src/main/resources/db/migration/V18__sales_support_basedata_read.sql`

- [ ] **Step 1: Confirm the migration content**

Run: `cd backend && cat src/main/resources/db/migration/V18__sales_support_basedata_read.sql`
Expected: it `DELETE`s role_menu rows for `role_id=904 AND menu_id IN (2014,2018,2022)` then `INSERT`s them via `SELECT 904*100000+m.id ...`.

- [ ] **Step 2: Run the full backend suite (Flyway applies V18 to the test/dev DB)**

Run: `cd backend && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn -q test`
Expected: PASS; Flyway logs show 18 migrations validated / schema at 18.

- [ ] **Step 3: Verify role 904 now holds the three read perms**

Rebuild + restart the jar, then check the running user-info for a 销售支持 user (or query the DB). Using the app or an HTTP client, confirm a role-904 user's `permissions` now include `supplier:list`, `brand:list`, `category:list`, and that their left nav is still the four groups (平台目录/库存管理/销售管理/物流管理) — no 基础数据 group.

Expected: the three codes present; nav unchanged.

- [ ] **Step 4: Commit**

```bash
cd backend
git add src/main/resources/db/migration/V18__sales_support_basedata_read.sql
git commit -m "feat(rbac): V18 grant 销售支持 supplier/brand/category read for product search"
```

---

## Task 5: Frontend — picker searches all products, backorder-aware

**Files:**
- Modify: `frontend/src/types/sales.d.ts`
- Modify: `frontend/src/api/sales/order.ts`
- Modify: `frontend/src/views/sales/order/index.vue`
- Modify: `frontend/src/locales/lang/zh-CN.ts` and `frontend/src/locales/lang/en-US.ts` (backorder label)

- [ ] **Step 1: Add the VO type and query type**

In `frontend/src/types/sales.d.ts`, add:

```ts
export interface OrderableProductVO {
  supplierProductId: Id;
  productName: string;
  productCode: string;
  supplierName: string;
  quantity: number;
  retailPrice: number | null;
}

export interface OrderableProductQuery {
  supplierId?: Id;
  brandId?: Id;
  categoryId?: Id;
  keyword?: string;
  current?: number;
  size?: number;
}
```

(`Id` is already imported at the top of this file.)

- [ ] **Step 2: Add the api function**

In `frontend/src/api/sales/order.ts`, add the import and function:

```ts
import type {
  SalesOrderVO,
  SalesOrderCreateDTO,
  SalesOrderQuery,
  SalesOrderLabelVO,
  OrderableProductVO,
  OrderableProductQuery,
} from '@/types/sales';
```

```ts
export const apiOrderableProductsPage = (q: OrderableProductQuery) =>
  http.get<PageResult<OrderableProductVO>>('/sales-orders/orderable-products', q);
```

- [ ] **Step 3: Add the backorder i18n label**

In `frontend/src/locales/lang/zh-CN.ts`, under the `sales.order` object, add:

```ts
    backorder: '缺货可订',
```

In `frontend/src/locales/lang/en-US.ts`, under the same `sales.order` object, add:

```ts
    backorder: 'Backorder',
```

(Match the existing indentation/structure of the `sales.order` block in each file.)

Also update the now-inaccurate picker title `selectProducts` (it currently says the
source is stock). In `zh-CN.ts` change its value to `'选择商品'`; in `en-US.ts` set the
same key's value to `'Select products'`. (Leave the key name unchanged.)

- [ ] **Step 4: Rewire the picker in the create drawer**

In `frontend/src/views/sales/order/index.vue`:

Change the import on line 9 from:

```ts
import { apiStockPage } from '@/api/inventory/stock';
```

to:

```ts
import { apiOrderableProductsPage } from '@/api/sales/order';
```

Merge it into the existing `@/api/sales/order` import instead if you prefer a single import line; either is fine as long as `apiStockPage` is no longer imported.

Change the type import on line 12 from:

```ts
import type { InventoryStockVO, InventoryStockQuery } from '@/types/inventory';
```

to:

```ts
import type { OrderableProductVO, OrderableProductQuery } from '@/types/sales';
```

Update the `CartRow` handling and the two `InventoryStockVO`/`InventoryStockQuery` usages:

- `stockFilter` / `stockQuery` types: change `ref<InventoryStockQuery>({})` to `ref<OrderableProductQuery>({})` and the `onStockFilterChange` param type to `OrderableProductQuery`.
- `setQty` signature: change `row: InventoryStockVO` to `row: OrderableProductVO`, and set `unitPrice` from `row.retailPrice ?? 0` **without** calling `apiSupplierProductGet` (remove that call and its now-unused import on line 10 if nothing else uses it). New body:

```ts
async function setQty(row: OrderableProductVO, val: number | null) {
  const key = String(row.supplierProductId);
  const qty = val ?? 0;
  if (cart[key]) {
    cart[key].qty = qty;
    cart[key].stockQty = row.quantity;
    return;
  }
  if (qty <= 0) return;
  cart[key] = {
    supplierProductId: row.supplierProductId,
    productName: row.productName,
    productCode: row.productCode,
    stockQty: row.quantity,
    qty,
    unitPrice: row.retailPrice ?? 0,
  };
}
```

- `rowInvalid`: change from flagging `qty > stockQty` to only flag `qty < 1`:

```ts
// 仅数量 <1 视为非法；超过库存允许（缺货可订，后端 backorder）
const rowInvalid = (r: CartRow) => r.qty < 1;
```

- Picker fetcher: in the template, change `:fetcher="apiStockPage"` to `:fetcher="apiOrderableProductsPage"`.
- Qty input in the picker: remove `:max="record.quantity"` so out-of-stock/under-stock qty can be entered. Keep `:min="0"` and `:precision="0"`, and change the `setQty` cast to `OrderableProductVO`:

```vue
              <a-input-number
                :value="getQty(record.supplierProductId)"
                :min="0"
                :precision="0"
                style="width: 110px"
                @change="(v: any) => setQty(record as OrderableProductVO, v)"
              />
```

- Add a non-blocking backorder tag in the picker's product cell. In the picker `BasicTable`'s `#bodyCell`, add a branch for the `quantity` column showing a tag when out of stock:

```vue
            <template v-else-if="column.key === 'quantity'">
              {{ record.quantity }}
              <a-tag v-if="record.quantity <= 0" color="orange">{{ t('sales.order.backorder') }}</a-tag>
            </template>
```

- In the cart table, show the backorder tag when `qty > stockQty` (keep the existing red styling off, since it's allowed):

```vue
            <template v-if="column.key === 'qty'">
              {{ record.qty }}
              <a-tag v-if="record.qty > record.stockQty" color="orange">{{ t('sales.order.backorder') }}</a-tag>
            </template>
```

(Replace the old `rowInvalid`-based red span for the cart `qty` cell with the above.)

- [ ] **Step 5: Typecheck / build**

Run: `cd frontend && pnpm build`
Expected: build succeeds with no type errors (all `InventoryStockVO`/`apiStockPage`/`apiSupplierProductGet` references in this file are gone).

- [ ] **Step 6: Manual verification (backend running with V18, dev server up)**

Log in as a 销售支持 user, open `/sales/order` → "Add Sales Order":
- The picker lists products with **no** stock (backorder tag) after the in-stock ones.
- The `CascadeFilter` supplier dropdown loads (V18 fix) and filters the list.
- Enter a qty on an out-of-stock product, fill customer info, submit → order created; the product's inventory shows negative on `/inventory/stock`.

Expected: all hold.

- [ ] **Step 7: Commit**

```bash
cd frontend
git add src/types/sales.d.ts src/api/sales/order.ts src/views/sales/order/index.vue \
        src/locales/lang/zh-CN.ts src/locales/lang/en-US.ts
git commit -m "feat(sales): order picker searches all products, in-stock first, backorder-aware"
```

---

## Task 6: Finish the branch

- [ ] **Step 1: Full backend suite**

Run: `cd backend && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn -q test`
Expected: PASS (all suites green, Flyway at V18).

- [ ] **Step 2: Frontend build**

Run: `cd frontend && pnpm build`
Expected: build succeeds.

- [ ] **Step 3: Open PRs per repo**

Use the finishing-a-development-branch skill to open one PR in `backend` (endpoint +
backorder + V18 migration + tests) and one in `frontend` (picker). Cross-link; note in
each body they must be released together (the frontend picker depends on the new
endpoint, and Sales Support search depends on V18).

---

## Notes on coverage vs. spec

- Spec §1 (orderable-products endpoint) → Task 3. Spec §2 (backorder / changeStock) →
  Tasks 1–2. Spec §3 (frontend picker) → Task 5. Bundled V18 → Task 4.
- Negative-stock display: covered implicitly — the backorder test (Task 2) asserts the
  `-2` quantity is returned by the existing `/inventory/stocks` endpoint, proving the UI
  will show it.
- The `allowNegative=false` invariant for non-sales callers is locked by Task 1's second
  test (manual adjust still rejects negative).
