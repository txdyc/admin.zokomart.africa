# 客户管理（客户列表）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增「客户管理」顶级菜单，只读展示由 `sales_order` 聚合出的客户列表（按手机号去重）。

**Architecture:** 后端新增 `module/customer`：一个 XML mapper 对 `sales_order` 做 `GROUP BY customer_phone` 聚合（订单数/累计实收/最近下单时间，姓名/地址取最近一单），经 service 分页返回；V12 迁移加菜单+权限 `customer:list`。前端按后端菜单驱动新增 `/customer/list` 只读列表页。

**Tech Stack:** SpringBoot 3.5 + MyBatis-Plus（XML mapper + 分页插件）+ Sa-Token；Vue3 + Ant Design Vue + Vitest。

**仓库根目录：** 后端 `D:\GHANA\claude\admin.zokomart.africa\backend`，前端 `D:\GHANA\claude\admin.zokomart.africa\frontend`。Maven 用 JDK 21：`JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" ...`。前端 `pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" <script>`。用绝对路径，勿 `cd`。本机 MySQL(`zokomart_admin`)/Redis 已运行；超管 `superadmin / Admin@123`。

**分支：** 两仓 `feat/customer-list`（后端分支已建）。

**已核对的 id（来自当前 sys_menu）：** 顶级目录 max id=1007 → 新目录 **1008**；菜单 max id=1115 → 新菜单 **1116**；种子按钮到 2063 → 新按钮 **2064**。路由 `/customer`（目录）、`/customer/list`（页面），组件 `customer/index`（解析为 `src/views/customer/index.vue`）。

---

## File Structure

### 后端 `backend/`
- Create `.../module/customer/vo/CustomerVO.java`
- Create `.../module/customer/dto/CustomerQuery.java`
- Create `.../module/customer/mapper/CustomerMapper.java`
- Create `src/main/resources/mapper/CustomerMapper.xml`
- Create `.../module/customer/service/CustomerService.java` + `impl/CustomerServiceImpl.java`
- Create `.../module/customer/controller/CustomerController.java`
- Create `src/main/resources/db/migration/V12__customer_menu.sql`
- Create `src/test/java/africa/zokomart/admin/customer/CustomerApiTest.java`

### 前端 `frontend/`
- Create `src/types/customer.d.ts`
- Create `src/api/customer.ts`
- Create `src/views/customer/index.vue`
- Create `tests/unit/customer-page.spec.ts`

---

## 后端任务

### Task CB1: VO / Query / Mapper / XML（数据层）

**Files:** Create CustomerVO, CustomerQuery, CustomerMapper, CustomerMapper.xml

- [ ] **Step 1: CustomerVO**

`src/main/java/africa/zokomart/admin/module/customer/vo/CustomerVO.java`
```java
package africa.zokomart.admin.module.customer.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客户列表项：由 sales_order 按手机号聚合得出。 */
@Data
public class CustomerVO {
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private Long orderCount;
    private BigDecimal totalAmount;
    private LocalDateTime lastOrderTime;
}
```

- [ ] **Step 2: CustomerQuery**

`src/main/java/africa/zokomart/admin/module/customer/dto/CustomerQuery.java`
```java
package africa.zokomart.admin.module.customer.dto;

import lombok.Data;

@Data
public class CustomerQuery {
    private String keyword;
    private long current = 1;
    private long size = 10;
}
```

- [ ] **Step 3: CustomerMapper**

`src/main/java/africa/zokomart/admin/module/customer/mapper/CustomerMapper.java`
```java
package africa.zokomart.admin.module.customer.mapper;

import africa.zokomart.admin.module.customer.vo.CustomerVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 客户聚合查询（无实体，纯 XML 聚合）。 */
@Mapper
public interface CustomerMapper {

    IPage<CustomerVO> pageCustomers(Page<CustomerVO> page, @Param("kw") String kw);
}
```

- [ ] **Step 4: CustomerMapper.xml**

`src/main/resources/mapper/CustomerMapper.xml`（注意 `<>` 在 XML 中转义为 `&lt;&gt;`；全局已开启 `map-underscore-to-camel-case`，故下划线别名自动映射到驼峰 VO 字段）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="africa.zokomart.admin.module.customer.mapper.CustomerMapper">

    <select id="pageCustomers" resultType="africa.zokomart.admin.module.customer.vo.CustomerVO">
        SELECT
            a.customer_phone AS customer_phone,
            (SELECT s2.customer_name FROM sales_order s2
             WHERE s2.customer_phone = a.customer_phone AND s2.deleted = 0
             ORDER BY s2.create_time DESC, s2.id DESC LIMIT 1) AS customer_name,
            (SELECT s2.customer_address FROM sales_order s2
             WHERE s2.customer_phone = a.customer_phone AND s2.deleted = 0
             ORDER BY s2.create_time DESC, s2.id DESC LIMIT 1) AS customer_address,
            a.order_count AS order_count,
            a.total_amount AS total_amount,
            a.last_order_time AS last_order_time
        FROM (
            SELECT customer_phone,
                   COUNT(*)            AS order_count,
                   SUM(actual_amount)  AS total_amount,
                   MAX(create_time)    AS last_order_time
            FROM sales_order
            WHERE deleted = 0 AND customer_phone IS NOT NULL AND customer_phone &lt;&gt; ''
            <if test="kw != null and kw != ''">
                AND (customer_name LIKE CONCAT('%', #{kw}, '%')
                     OR customer_phone LIKE CONCAT('%', #{kw}, '%'))
            </if>
            GROUP BY customer_phone
        ) a
        ORDER BY a.last_order_time DESC
    </select>
</mapper>
```

- [ ] **Step 5: 编译**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/customer/vo/CustomerVO.java src/main/java/africa/zokomart/admin/module/customer/dto/CustomerQuery.java src/main/java/africa/zokomart/admin/module/customer/mapper/CustomerMapper.java src/main/resources/mapper/CustomerMapper.xml
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(customer): VO/query/mapper + aggregate XML over sales_order

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task CB2: Service / Controller / V12 迁移

**Files:** Create CustomerService(+impl), CustomerController, V12 migration

- [ ] **Step 1: CustomerService 接口**

`src/main/java/africa/zokomart/admin/module/customer/service/CustomerService.java`
```java
package africa.zokomart.admin.module.customer.service;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.customer.vo.CustomerVO;

public interface CustomerService {

    PageResult<CustomerVO> page(String keyword, long current, long size);
}
```

- [ ] **Step 2: CustomerServiceImpl**

`src/main/java/africa/zokomart/admin/module/customer/service/impl/CustomerServiceImpl.java`
```java
package africa.zokomart.admin.module.customer.service.impl;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.module.customer.mapper.CustomerMapper;
import africa.zokomart.admin.module.customer.service.CustomerService;
import africa.zokomart.admin.module.customer.vo.CustomerVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;

    @Override
    public PageResult<CustomerVO> page(String keyword, long current, long size) {
        Page<CustomerVO> page = new Page<>(current, size);
        IPage<CustomerVO> result = customerMapper.pageCustomers(
                page, StringUtils.hasText(keyword) ? keyword.trim() : null);
        return PageResult.of(result);
    }
}
```

- [ ] **Step 3: CustomerController**

`src/main/java/africa/zokomart/admin/module/customer/controller/CustomerController.java`
```java
package africa.zokomart.admin.module.customer.controller;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.customer.service.CustomerService;
import africa.zokomart.admin.module.customer.vo.CustomerVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "客户管理")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/api/customers")
    @SaCheckPermission("customer:list")
    public Result<PageResult<CustomerVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(customerService.page(keyword, current, size));
    }
}
```

- [ ] **Step 4: V12 迁移（菜单 + 权限）**

`src/main/resources/db/migration/V12__customer_menu.sql`
```sql
-- ===========================================================================
-- V12: 客户管理（顶级菜单）。目录 1008 / 页面 1116 / 按钮 customer:list (2064)。
--      superadmin 通配自动可见；其它角色后续在角色管理里授权。
-- ===========================================================================
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(1008, 0,    '客户管理', 1, NULL,            '/customer',      NULL,             'ant-design:team-outlined', 8, 1, 1, NOW(), 0, 0),
(1116, 1008, '客户列表', 2, NULL,            '/customer/list', 'customer/index', NULL,                       1, 1, 1, NOW(), 0, 0),
(2064, 1116, '查询客户', 3, 'customer:list', NULL,             NULL,             NULL,                       1, 1, 1, NOW(), 0, 0);
```

- [ ] **Step 5: 编译**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/main/java/africa/zokomart/admin/module/customer/service src/main/java/africa/zokomart/admin/module/customer/controller src/main/resources/db/migration/V12__customer_menu.sql
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "feat(customer): list service + endpoint + V12 menu/perm

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task CB3: 集成测试（TDD）

**Files:** Test `src/test/java/africa/zokomart/admin/customer/CustomerApiTest.java`

> 直接用 JdbcTemplate 插入 sales_order 行（避开销售下单+库存的重流程），专注验证客户聚合：按手机号去重、订单数/累计实收/最近时间、姓名地址取最近一单、keyword 过滤；测试结束清理。

- [ ] **Step 1: 写测试**

```java
package africa.zokomart.admin.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 客户列表聚合集成测试：直插 sales_order，验证按手机号去重的聚合与 keyword 过滤。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    JdbcTemplate jdbc;

    private final long base = System.currentTimeMillis();
    private final String phoneA = "024TESTA" + (base % 100000);
    private final String phoneB = "024TESTB" + (base % 100000);

    private void insertOrder(long id, String no, String name, String phone, String addr,
                             String actualAmount, String createTime) {
        jdbc.update("INSERT INTO sales_order (id, order_no, customer_name, customer_phone, customer_address, "
                        + "actual_amount, total_amount, create_time, deleted, version) "
                        + "VALUES (?,?,?,?,?,?,?,?,0,0)",
                id, no, name, phone, addr, actualAmount, actualAmount, createTime);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM sales_order WHERE customer_phone IN (?,?)", phoneA, phoneB);
    }

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}")).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    @Test
    void aggregates_customers_by_phone() throws Exception {
        String t = token();
        // phoneA: 2 单（最近一单姓名/地址 = Ama K./Tema），phoneB: 1 单
        insertOrder(base + 1, "SOT" + base + "1", "Ama",    phoneA, "Osu",  "100.00", "2026-06-01 10:00:00");
        insertOrder(base + 3, "SOT" + base + "3", "Ama K.", phoneA, "Tema", "250.00", "2026-06-03 10:00:00");
        insertOrder(base + 2, "SOT" + base + "2", "Kofi",   phoneB, "Accra","80.00",  "2026-06-02 10:00:00");

        // 查 phoneA：去重为 1 个客户，订单数 2、累计 350、姓名/地址取最近一单
        MvcResult r = mvc.perform(get("/api/customers").header("Authorization", t).param("keyword", phoneA))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].customerPhone").value(phoneA))
                .andExpect(jsonPath("$.data.records[0].orderCount").value(2))
                .andExpect(jsonPath("$.data.records[0].customerName").value("Ama K."))
                .andExpect(jsonPath("$.data.records[0].customerAddress").value("Tema"))
                .andReturn();
        double total = om.readTree(r.getResponse().getContentAsString()).at("/data/records/0/totalAmount").asDouble();
        org.junit.jupiter.api.Assertions.assertEquals(350.0, total, 0.001);

        // keyword=Kofi 命中 phoneB 这一个客户
        mvc.perform(get("/api/customers").header("Authorization", t).param("keyword", "Kofi"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].customerPhone").value(phoneB))
                .andExpect(jsonPath("$.data.records[0].orderCount").value(1));
    }

    @Test
    void requires_login() throws Exception {
        mvc.perform(get("/api/customers")).andExpect(jsonPath("$.code").value(401));
    }
}
```

- [ ] **Step 2: 运行（首跑会应用 V12 迁移）**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" -Dtest=CustomerApiTest test`
Expected: BUILD SUCCESS，2 用例通过。若聚合断言失败，核对 XML 列别名与 `map-underscore-to-camel-case` 是否生效；若为真实 SQL 错误，定位修复，勿改测试掩盖。

- [ ] **Step 3: 全量回归**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" mvn -q -f "D:/GHANA/claude/admin.zokomart.africa/backend/pom.xml" test`
Expected: BUILD SUCCESS（在既有 60 基础上 +2 = 62）。

- [ ] **Step 4: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" add src/test/java/africa/zokomart/admin/customer/CustomerApiTest.java
git -C "D:/GHANA/claude/admin.zokomart.africa/backend" commit -m "test(customer): customer list aggregation api

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 前端任务

### Task CF0: 建分支

- [ ] **Step 1:**

Run（前端）：
```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -- src/types/components.d.ts 2>/dev/null; true
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout main
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -b feat/customer-list
```
Expected: `Switched to a new branch 'feat/customer-list'`。

---

### Task CF1: 类型 + API

**Files:** Create `src/types/customer.d.ts`, `src/api/customer.ts`

- [ ] **Step 1: 类型**

`src/types/customer.d.ts`
```typescript
import type { Id } from './api';

export interface CustomerVO {
  customerName: string;
  customerPhone: string;
  customerAddress: string | null;
  orderCount: number;
  totalAmount: number | null;
  lastOrderTime: string | null;
}

export interface CustomerQuery {
  keyword?: string;
  current?: number;
  size?: number;
}
```
（`Id` 未直接使用也保留导入约定；若 lint 报未用，删掉该 import 行。）

- [ ] **Step 2: API**

`src/api/customer.ts`
```typescript
import { http } from '@/utils/request';
import type { PageResult } from '@/types/api';
import type { CustomerVO, CustomerQuery } from '@/types/customer';

export const apiCustomerPage = (q: CustomerQuery) =>
  http.get<PageResult<CustomerVO>>('/customers', q);
```

- [ ] **Step 3: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add src/types/customer.d.ts src/api/customer.ts
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "feat(customer): types + list api

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task CF2: 客户列表页

**Files:** Create `src/views/customer/index.vue`

- [ ] **Step 1: 创建页面**（搜索 + BasicTable 只读列表；BasicTable 用法对齐既有供应商产品页）

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import BasicTable from '@/components/BasicTable.vue';
import { apiCustomerPage } from '@/api/customer';

const money = (n: number | null | undefined) => (n ?? 0).toFixed(2);

const tableRef = ref<InstanceType<typeof BasicTable>>();
const searchForm = reactive<{ keyword?: string }>({});
const query = ref<Record<string, any>>({});
const onSearch = () => (query.value = { ...searchForm });
const onReset = () => {
  searchForm.keyword = undefined;
  query.value = {};
};

const columns: TableColumnsType = [
  { title: '客户姓名', dataIndex: 'customerName', key: 'customerName' },
  { title: '电话', dataIndex: 'customerPhone', key: 'customerPhone', width: 150 },
  { title: '地址', dataIndex: 'customerAddress', key: 'customerAddress' },
  { title: '订单数', dataIndex: 'orderCount', key: 'orderCount', width: 90 },
  { title: '累计金额 (GHS)', dataIndex: 'totalAmount', key: 'totalAmount', width: 140 },
  { title: '最近下单时间', dataIndex: 'lastOrderTime', key: 'lastOrderTime', width: 180 },
];

defineExpose({ searchForm, query, onSearch, onReset });
</script>

<template>
  <div>
    <a-card :bordered="false" class="mb-3">
      <a-form layout="inline">
        <a-form-item label="关键字">
          <a-input
            v-model:value="searchForm.keyword"
            placeholder="客户姓名 / 电话"
            allow-clear
            style="width: 220px"
            @press-enter="onSearch"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" data-test="customer-search" @click="onSearch">查询</a-button>
            <a-button @click="onReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card :bordered="false">
      <BasicTable ref="tableRef" :columns="columns" :fetcher="apiCustomerPage" :params="query">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'totalAmount'">{{ money(record.totalAmount) }}</template>
          <template v-else-if="column.key === 'customerAddress'">{{ record.customerAddress ?? '—' }}</template>
        </template>
      </BasicTable>
    </a-card>
  </div>
</template>
```

- [ ] **Step 2: 构建（含 vue-tsc 类型检查）**

Run: `pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" build`
Expected: vue-tsc 通过、vite 构建成功。若 `BasicTable` 的 `fetcher` 类型不匹配，核对既有 `src/views/product/supplier-product/index.vue` 的同款用法对齐。

- [ ] **Step 3: Commit**

```bash
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" checkout -- src/types/components.d.ts 2>/dev/null; true
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add src/views/customer/index.vue
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "feat(customer): read-only customer list page

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task CF3: 组件测试 + 全量验证

**Files:** Test `tests/unit/customer-page.spec.ts`

- [ ] **Step 1: 写测试**（验证查询/重置把 keyword 写入 query —— 页面过滤逻辑）

```typescript
import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import CustomerPage from '@/views/customer/index.vue';

vi.mock('@/api/customer', () => ({
  apiCustomerPage: vi.fn().mockResolvedValue({ records: [], total: 0, current: 1, size: 10 }),
}));

const stubs = {
  'a-card': true, 'a-form': true, 'a-form-item': true, 'a-input': true,
  'a-button': true, 'a-space': true, BasicTable: true,
};

describe('CustomerPage', () => {
  it('search copies keyword into query, reset clears it', () => {
    const wrapper = mount(CustomerPage, { global: { stubs } });
    wrapper.vm.searchForm.keyword = 'Ama';
    wrapper.vm.onSearch();
    expect(wrapper.vm.query.keyword).toBe('Ama');
    wrapper.vm.onReset();
    expect(wrapper.vm.query.keyword).toBeUndefined();
  });
});
```

- [ ] **Step 2: 运行单测**

Run: `pnpm --dir "D:/GHANA/claude/admin.zokomart.africa/frontend" exec vitest run tests/unit/customer-page.spec.ts`
Expected: 1 用例通过。

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
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" add tests/unit/customer-page.spec.ts
git -C "D:/GHANA/claude/admin.zokomart.africa/frontend" commit -m "test(customer): customer list page filter logic

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 收尾

- [ ] **联调**：后端重建+重启（jar 跑旧码，参见 [[backend-runtime-rebuild]]）→ V12 生效后，superadmin 登录可见「客户管理」菜单；当前 sales_order 为空（已清库），列表为空属正常，下过销售订单后即有数据。
- [ ] **完成分支**：用 superpowers:finishing-a-development-branch（两仓 `feat/customer-list`）。

---

## Self-Review 结论（计划编写者自查）

- **Spec 覆盖**：sales_order 聚合(CB1 XML)、按 phone 去重 + 姓名/地址取最近一单 + 订单数/累计/最近时间(CB1)、keyword 过滤(CB1)、电话为空不计入(XML WHERE)、`customer:list` 权限 + 顶级菜单 V12(CB2)、只读列表页 + 顶级菜单驱动(CF2)、测试(CB3/CF3) 均覆盖。
- **占位符**：无 TODO/TBD；每步含完整代码与命令；菜单 id（1008/1116/2064）已按当前 sys_menu max id 选定。
- **类型一致**：后端 `CustomerVO`(customerName/customerPhone/customerAddress/orderCount/totalAmount/lastOrderTime) ↔ 前端 `CustomerVO` 字段一致；端点 `/api/customers` ↔ 前端 `/customers`（baseURL 含 /api）一致；XML resultType 列别名（下划线）经全局驼峰映射对上 VO；菜单 component `customer/index` ↔ 视图 `src/views/customer/index.vue`（前端 `resolveComp` 规则 `/src/views/${component}.vue`）。
