# ZokoMart Admin 后端 —— 分阶段落地实施计划

> **For agentic workers:** 可配合 superpowers:subagent-driven-development 或 superpowers:executing-plans
> 按任务逐条执行。所有步骤用 `- [ ]` 复选框跟踪。

**Goal:** 按 PRD（见 `./PRD.md`）从零搭建 ZokoMart Admin 后端，落地进销存全链路（RBAC → 基础数据 → 供应商产品 → 采购链 → 库存 → 销售/物流）。

**Architecture:** SpringBoot 单体，分层（controller / service / mapper / entity·dto·vo），统一 `Result` + 全局异常，Sa-Token+Redis 鉴权，MyBatis-Plus 数据访问，Flyway 管理表结构。库存增减一律经 `inventory_stock`(乐观锁) + `inventory_transaction`(流水) 双写。

**Tech Stack:** Java 21 · SpringBoot 3.5.15 · Maven · MySQL 8 · MyBatis-Plus 3.5.7 · Redis · Sa-Token 1.39 · Flyway · Knife4j · Lombok · JUnit5 + Spring Boot Test + Testcontainers(可选)。

---

## 如何使用本计划

- **阶段(Phase)= 可独立验收的里程碑**。每个阶段末尾有「阶段验收」，通过后再进入下一阶段。
- **TDD 重点放在业务逻辑层**（service）尤其是采购审批生单、库存扣减/回补、结算等非平凡逻辑；
  对纯样板 CRUD，给出一个**完整模板**（品牌模块）并列明哪些模块照此复制。
- **每个任务结束即 commit**；commit message 用 `feat:/test:/chore:` 前缀。
- 表结构变更一律新增 Flyway 迁移脚本（`src/main/resources/db/migration/Vx__xxx.sql`），**不改历史脚本**。
- 包根：`africa.zokomart.admin`。下文路径省略 `src/main/java/africa/zokomart/admin/` 前缀时会显式说明。

---

## 文件结构总览（落地后）

```
backend/
├── pom.xml
├── .gitignore
├── src/main/java/africa/zokomart/admin/
│   ├── AdminApplication.java
│   ├── common/
│   │   ├── result/{Result,ResultCode,PageResult}.java
│   │   ├── exception/{BusinessException,GlobalExceptionHandler}.java
│   │   └── base/{BaseEntity,MyMetaObjectHandler}.java
│   ├── config/{MybatisPlusConfig,RedisConfig,SaTokenConfig,CorsConfig,Knife4jConfig}.java
│   └── module/
│       ├── system/   (user/role/menu + auth + StpInterfaceImpl)
│       ├── basedata/ (brand/supplier/category/logisticsProvider)
│       ├── product/  (spu/sku)
│       ├── supplierproduct/
│       ├── purchase/ (plan/order/actualOrder)
│       ├── inventory/(stock/transaction)
│       └── sales/    (salesOrder + logistics 动作)
└── src/main/resources/
    ├── application.yml / application-dev.yml
    ├── db/migration/V1..Vn__*.sql
    └── mapper/*.xml (复杂 SQL)
```

每个 `module/<x>/` 内统一 `controller / service(+impl) / mapper / entity / dto / vo`。

---

## Phase 0 — 工程脚手架与基础设施

**目标产物：** 可 `mvn spring-boot:run` 启动、`/api/ping` 返回统一 `Result` 的空工程；通用层（Result/异常/审计/配置）就位。

### Task 0.1: 初始化 git 与忽略规则

- [ ] **Step 1: 初始化仓库并关联远程**
```bash
cd backend
git init -b main
git remote add origin https://github.com/txdyc/admin.zokomart.africa.git
```
- [ ] **Step 2: 写 `.gitignore`**

`backend/.gitignore`:
```gitignore
target/
*.class
.idea/
*.iml
.vscode/
# 本地密钥 profile，严禁入库
src/main/resources/application-local.yml
*.log
```
- [ ] **Step 3: Commit**
```bash
git add .gitignore && git commit -m "chore: init repo and gitignore"
```

### Task 0.2: pom.xml

- [ ] **Step 1: 写 `backend/pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.15</version>
    <relativePath/>
  </parent>
  <groupId>africa.zokomart</groupId>
  <artifactId>admin</artifactId>
  <version>1.0.0</version>
  <name>zokomart-admin</name>
  <properties>
    <java.version>21</java.version>
    <mybatis-plus.version>3.5.7</mybatis-plus.version>
    <sa-token.version>1.39.0</sa-token.version>
    <knife4j.version>4.5.0</knife4j.version>
  </properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
    <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-spring-boot3-starter</artifactId><version>${mybatis-plus.version}</version></dependency>
    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>cn.dev33</groupId><artifactId>sa-token-spring-boot3-starter</artifactId><version>${sa-token.version}</version></dependency>
    <dependency><groupId>cn.dev33</groupId><artifactId>sa-token-redis-jackson</artifactId><version>${sa-token.version}</version></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
    <dependency><groupId>com.github.xiaoymin</groupId><artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId><version>${knife4j.version}</version></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <configuration><excludes><exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude></excludes></configuration>
      </plugin>
    </plugins>
  </build>
</project>
```
- [ ] **Step 2: 验证依赖可解析** — Run: `mvn -q dependency:resolve` → Expected: BUILD SUCCESS。
- [ ] **Step 3: Commit** — `git add pom.xml && git commit -m "chore: maven pom with core deps"`

### Task 0.3: 配置文件

- [ ] **Step 1: `src/main/resources/application.yml`**
```yaml
server:
  port: 8080
  servlet:
    context-path: /
spring:
  application:
    name: zokomart-admin
  profiles:
    active: dev
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  mapper-locations: classpath*:mapper/**/*.xml
sa-token:
  token-name: Authorization
  timeout: 86400
  is-concurrent: true
  is-share: false
  token-style: uuid
```
- [ ] **Step 2: `application-dev.yml`（占位，密钥走 application-local.yml）**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/zokomart_admin?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
```
- [ ] **Step 3: 建库** — Run: `mysql -u root -e "CREATE DATABASE IF NOT EXISTS zokomart_admin DEFAULT CHARSET utf8mb4;"`
- [ ] **Step 4: Commit** — `git commit -am "chore: application config (dev profile)"`

### Task 0.4: 通用返回 Result / ResultCode / PageResult

- [ ] **Step 1: `common/result/ResultCode.java`**
```java
package africa.zokomart.admin.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(0, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    BUSINESS_ERROR(500, "business error"),
    // 业务码（按域分段，便于前端识别）
    INSUFFICIENT_STOCK(40001, "库存不足"),
    BELOW_MIN_PURCHASE_QTY(40002, "低于最小采购量"),
    INVALID_STATUS_TRANSITION(40003, "非法的状态流转");

    private final int code;
    private final String msg;
    ResultCode(int code, String msg) { this.code = code; this.msg = msg; }
}
```
- [ ] **Step 2: `common/result/Result.java`**
```java
package africa.zokomart.admin.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.msg = ResultCode.SUCCESS.getMsg();
        r.data = data;
        return r;
    }
    public static <T> Result<T> ok() { return ok(null); }
    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code; r.msg = msg; return r;
    }
    public static <T> Result<T> fail(ResultCode rc) { return fail(rc.getCode(), rc.getMsg()); }
}
```
- [ ] **Step 3: `common/result/PageResult.java`**
```java
package africa.zokomart.admin.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> p = new PageResult<>();
        p.records = page.getRecords();
        p.total = page.getTotal();
        p.current = page.getCurrent();
        p.size = page.getSize();
        return p;
    }
}
```
- [ ] **Step 4: Commit** — `git add . && git commit -m "feat: unified Result/PageResult/ResultCode"`

### Task 0.5: 业务异常 + 全局异常处理

- [ ] **Step 1: `common/exception/BusinessException.java`**
```java
package africa.zokomart.admin.common.exception;

import africa.zokomart.admin.common.result.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String msg) { super(msg); this.code = code; }
    public BusinessException(ResultCode rc) { super(rc.getMsg()); this.code = rc.getCode(); }
    public BusinessException(ResultCode rc, String msg) { super(msg); this.code = rc.getCode(); }
}
```
- [ ] **Step 2: `common/exception/GlobalExceptionHandler.java`**
```java
package africa.zokomart.admin.common.exception;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.common.result.ResultCode;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(Exception e) {
        String msg = e instanceof MethodArgumentNotValidException ex
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : ((BindException) e).getFieldError().getDefaultMessage();
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(NotLoginException e) { return Result.fail(ResultCode.UNAUTHORIZED); }

    @ExceptionHandler(NotPermissionException.class)
    public Result<Void> handleNoPerm(NotPermissionException e) { return Result.fail(ResultCode.FORBIDDEN); }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        return Result.fail(ResultCode.BUSINESS_ERROR.getCode(), "系统异常");
    }
}
```
- [ ] **Step 3: Commit** — `git commit -am "feat: BusinessException + global handler"`

### Task 0.6: BaseEntity + 自动填充

- [ ] **Step 1: `common/base/BaseEntity.java`**
```java
package africa.zokomart.admin.common.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
}
```
- [ ] **Step 2: `common/base/MyMetaObjectHandler.java`**
```java
package africa.zokomart.admin.common.base;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    private Long currentUserId() {
        try { return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : 0L; }
        catch (Exception e) { return 0L; }
    }
    @Override public void insertFill(MetaObject m) {
        this.strictInsertFill(m, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(m, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(m, "createBy", Long.class, currentUserId());
        this.strictInsertFill(m, "updateBy", Long.class, currentUserId());
    }
    @Override public void updateFill(MetaObject m) {
        this.strictUpdateFill(m, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(m, "updateBy", Long.class, currentUserId());
    }
}
```
- [ ] **Step 3: Commit** — `git commit -am "feat: BaseEntity + meta object handler"`

### Task 0.7: 配置类（MP/Redis/Cors/Knife4j）

- [ ] **Step 1: `config/MybatisPlusConfig.java`**
```java
package africa.zokomart.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("africa.zokomart.admin.module.**.mapper")
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor i = new MybatisPlusInterceptor();
        i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        i.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return i;
    }
}
```
- [ ] **Step 2: `config/CorsConfig.java`**
```java
package africa.zokomart.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration c = new CorsConfiguration();
        c.addAllowedOriginPattern("*");
        c.addAllowedHeader("*");
        c.addAllowedMethod("*");
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return new CorsFilter(s);
    }
}
```
- [ ] **Step 3: `config/SaTokenConfig.java`（先放行全部，Phase 1 收紧）**
```java
package africa.zokomart.admin.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/ping",
                        "/doc.html", "/webjars/**", "/v3/api-docs/**");
    }
}
```
- [ ] **Step 4: `config/Knife4jConfig.java`** — 提供 `OpenAPI` Bean（标题 "ZokoMart Admin API"）。最小实现，可后置完善。
- [ ] **Step 5: Commit** — `git add . && git commit -m "feat: MP/Cors/SaToken/Knife4j config"`

### Task 0.8: 启动类 + ping 自检

- [ ] **Step 1: `AdminApplication.java`**
```java
package africa.zokomart.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdminApplication {
    public static void main(String[] args) { SpringApplication.run(AdminApplication.class, args); }
}
```
- [ ] **Step 2: `module/system/controller/PingController.java`**
```java
package africa.zokomart.admin.module.system.controller;

import africa.zokomart.admin.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PingController {
    @GetMapping("/ping")
    public Result<String> ping() { return Result.ok("pong"); }
}
```
- [ ] **Step 3: 写冒烟测试 `src/test/java/africa/zokomart/admin/PingTest.java`**
```java
package africa.zokomart.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PingTest {
    @Autowired MockMvc mvc;
    @Test void ping_returns_pong() throws Exception {
        mvc.perform(get("/api/ping"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data").value("pong"));
    }
}
```
- [ ] **Step 4: 跑测试** — Run: `mvn test -Dtest=PingTest` → Expected: PASS（需本地 MySQL/Redis 可用；否则用 dev profile 跳过 Flyway 或起容器）。
- [ ] **Step 5: Commit** — `git commit -am "feat: app entrypoint + ping smoke test"`

### 阶段验收 0
- `mvn spring-boot:run` 启动成功；`GET /api/ping` 返回 `{"code":0,"msg":"success","data":"pong"}`。
- `mvn test` 绿。Flyway 连接成功（空库）。

---

## Phase 1 — RBAC 与登录鉴权（需求 #1）

**目标产物：** 超管登录拿 token；`/api/**` 受 Sa-Token 保护；用户/角色/菜单 CRUD；权限码经 `StpInterface` 生效。

### Task 1.1: 表结构迁移 V1（sys_*）

- [ ] **Step 1: `db/migration/V1__system_rbac.sql`** — 建 `sys_user / sys_role / sys_menu / sys_user_role / sys_role_menu`，字段见 PRD §4.2，均含 `create_time/update_time/create_by/update_by/deleted/version`。
- [ ] **Step 2: 内置超管种子** — 同脚本 `INSERT` 一条 `sys_user`：`username='superadmin'`, `is_super=1`, `password`=BCrypt("Admin@123") 的密文（用预生成密文写入）。
- [ ] **Step 3: 启动校验迁移** — Run: `mvn spring-boot:run` → Flyway 应用 V1，日志显示 "Migrating ... V1"。
- [ ] **Step 4: Commit** — `git add . && git commit -m "feat(db): V1 system rbac schema + super admin seed"`

### Task 1.2: 实体 + Mapper（sys_*）

- [ ] **Step 1:** 在 `module/system/entity/` 建 `SysUser/SysRole/SysMenu/SysUserRole/SysRoleMenu`（继承 `BaseEntity`，`@TableName` 对应表）。
- [ ] **Step 2:** 在 `module/system/mapper/` 建对应 `*Mapper extends BaseMapper<T>`。
- [ ] **Step 3: Commit** — `git commit -am "feat(system): entities + mappers"`

### Task 1.3: StpInterface（权限聚合 + 超管短路）

- [ ] **Step 1: 写失败测试 `system/StpInterfaceImplTest.java`** — 给一个普通用户配 1 角色 2 权限码，断言 `getPermissionList` 返回这 2 个；给超管断言返回包含通配 `*`。
```java
// 关键断言
assertThat(impl.getPermissionList(normalUserId, "login")).containsExactlyInAnyOrder("brand:list","brand:create");
assertThat(impl.getPermissionList(superAdminId, "login")).contains("*");
```
- [ ] **Step 2: 跑测试确认失败** — Run: `mvn test -Dtest=StpInterfaceImplTest` → Expected: 编译失败/未实现。
- [ ] **Step 3: 实现 `system/auth/StpInterfaceImpl.java`**
```java
package africa.zokomart.admin.module.system.auth;

import cn.dev33.satoken.stp.StpInterface;
import africa.zokomart.admin.module.system.service.PermissionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {
    private final PermissionQueryService permissionQueryService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        long userId = Long.parseLong(loginId.toString());
        if (permissionQueryService.isSuperAdmin(userId)) return List.of("*");
        return permissionQueryService.listPermCodesByUserId(userId);
    }
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return permissionQueryService.listRoleCodesByUserId(Long.parseLong(loginId.toString()));
    }
}
```
- [ ] **Step 4: 实现 `PermissionQueryService`**（`isSuperAdmin`、`listPermCodesByUserId`：经 user→role→menu 三表查 `perm_code`、`listRoleCodesByUserId`）。配 Sa-Token 通配匹配：`sa-token.check-same-token` 无关；通配 `*` 由 `SaInterceptor` 的 `checkPermission` 默认支持需用 `StpUtil.hasPermission` 自定义——简化：超管在校验处用 `if hasRole/super then pass`。**实现说明**：`StpInterfaceImpl` 返回 `*` 后，用 Sa-Token 的 `SaManager.getConfig()` 无法自动通配；故在权限校验注解层用自定义 `@SaCheckPermission` 不够——改为在 `SaInterceptor` 内：先判断超管放行，否则 `StpUtil.checkPermission(code)`。见 Task 1.5。
- [ ] **Step 5: 跑测试** — Expected: PASS。
- [ ] **Step 6: Commit** — `git commit -am "feat(system): StpInterface perm aggregation + super admin"`

### Task 1.4: 登录/登出/用户信息（AuthController）

- [ ] **Step 1: 写测试 `system/AuthControllerTest.java`** — 用种子超管 `POST /api/auth/login {username,password}` 期望 `code=0` 且返回 `token`；带 token `GET /api/auth/user-info` 返回 `username/permissions/menus`；错误密码返回 `code=401/业务码`。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现 `LoginDTO`（username/password,@NotBlank）、`LoginVO`（token/userInfo）、`AuthService`：**
```java
public LoginVO login(LoginDTO dto) {
    SysUser u = userMapper.selectByUsername(dto.getUsername());
    if (u == null || u.getStatus() == 0 || !BCrypt.checkpw(dto.getPassword(), u.getPassword()))
        throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
    StpUtil.login(u.getId());
    return new LoginVO(StpUtil.getTokenValue(), buildUserInfo(u));
}
```
（BCrypt 用 `org.springframework.security.crypto.bcrypt.BCrypt` 或引 `spring-security-crypto`，在 pom 增该依赖。）
- [ ] **Step 4: 实现 `AuthController`**（`/api/auth/login` 免登、`/logout`、`/user-info`）。
- [ ] **Step 5: 跑测试** — Expected: PASS。
- [ ] **Step 6: Commit** — `git commit -am "feat(system): auth login/logout/user-info"`

### Task 1.5: 超管放行 + 权限校验拦截

- [ ] **Step 1: 更新 `SaTokenConfig`**：拦截器内登录校验后追加权限校验钩子；在校验工具里判断超管短路。
```java
registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/login","/api/ping","/doc.html","/webjars/**","/v3/api-docs/**");
```
对按钮级权限，用方法注解 `@SaCheckPermission("brand:create")`；并注册全局：超管 `getPermissionList` 返回 `*`，Sa-Token 对 `*` 默认放行所有 `checkPermission`（Sa-Token 支持通配 `*`）。**验证**：写测试 `普通用户无 brand:create 时 403`、`超管可访问`。
- [ ] **Step 2: 跑测试** — Expected: PASS。
- [ ] **Step 3: Commit** — `git commit -am "feat(system): permission interceptor + super admin bypass"`

### Task 1.6: 用户/角色/菜单 CRUD

- [ ] **Step 1:** 实现 `SysUserController/Service`（建用户 BCrypt 加密、分配角色、重置密码、启停；超管不可删/停）。
- [ ] **Step 2:** 实现 `SysRoleController/Service`（角色 CRUD + 绑定菜单）。
- [ ] **Step 3:** 实现 `SysMenuController/Service`（菜单树 CRUD；`GET /api/system/menus/tree`）。
- [ ] **Step 4:** 各加注解 `@SaCheckPermission("system:user:create")` 等。
- [ ] **Step 5: 写测试** 覆盖：建用户→该用户登录→无权限接口 403；超管给其角色后→200。
- [ ] **Step 6: Commit** — `git commit -am "feat(system): user/role/menu CRUD"`

### 阶段验收 1
- 超管可登录并访问任意接口；新建普通用户默认无权限，赋角色后按权限码放行。
- 密码 BCrypt；token 不出现在日志。`mvn test` 绿。

---

## Phase 2 — 基础数据（需求 #2/#3/#4/#7）

**目标产物：** 品牌、供应商、分类(树)、物流服务商 CRUD。本阶段确立**样板 CRUD 模板**，后续模块照此复制。

### Task 2.1: 迁移 V2（基础数据表）

- [ ] **Step 1: `db/migration/V2__base_data.sql`** — 建 `brand / supplier / category / logistics_provider`（字段见 PRD §4.3）。`brand.name`、`supplier.name` 唯一索引。
- [ ] **Step 2: 启动应用迁移；Commit** — `git commit -am "feat(db): V2 base data schema"`

### Task 2.2: 品牌模块（**完整样板，后续复制**）

**Files:** `module/basedata/entity/Brand.java`、`mapper/BrandMapper.java`、`dto/BrandSaveDTO.java`、`dto/BrandQueryDTO.java`、`vo/BrandVO.java`、`service/BrandService.java(+impl)`、`controller/BrandController.java`、`test/.../BrandControllerTest.java`。

- [ ] **Step 1: 写测试 `BrandControllerTest`** — 超管 token 下：创建品牌→列表含之→改名→删除→列表不含。重复名报 `code!=0`。
```java
@Test void crud_flow() throws Exception {
  String body = "{\"name\":\"Midea\",\"sort\":1,\"status\":1}";
  mvc.perform(post("/api/brands").header("Authorization", token).contentType(JSON).content(body))
     .andExpect(jsonPath("$.code").value(0));
  mvc.perform(get("/api/brands?keyword=Midea").header("Authorization", token))
     .andExpect(jsonPath("$.data.records[0].name").value("Midea"));
}
```
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现 Entity/Mapper/DTO/VO**（`BrandSaveDTO.name @NotBlank`）。
- [ ] **Step 4: 实现 `BrandServiceImpl`**（分页查询用 MP `Page` + `LambdaQueryWrapper`；唯一名校验；删除前校验被 `supplier_product` 引用→`BusinessException`，本阶段引用表未建则跳过该校验，Phase 4 回填）。
- [ ] **Step 5: 实现 `BrandController`**（`GET /api/brands`、`POST`、`PUT/{id}`、`DELETE/{id}`，权限码 `brand:*`）。
- [ ] **Step 6: 跑测试** — Expected: PASS。
- [ ] **Step 7: Commit** — `git commit -am "feat(basedata): brand CRUD (template module)"`

### Task 2.3: 供应商模块（复制 2.2 模板）
- [ ] 按 Brand 模板实现 `supplier` CRUD（字段 contact_person/contact_phone/address），权限 `supplier:*`，含同名唯一校验与一条 `SupplierControllerTest` CRUD 流程测试。**Commit** `feat(basedata): supplier CRUD`。

### Task 2.4: 分类模块（树）
- [ ] **Step 1:** 实体含 `parentId`；新增 `GET /api/categories/tree` 返回嵌套树（service 递归/一次查全量内存组树）。
- [ ] **Step 2:** 删除前校验有无子节点→有则 `BusinessException`。
- [ ] **Step 3:** 测试：建父→建子→tree 结构断言→删父被拒。
- [ ] **Step 4: Commit** — `feat(basedata): category tree CRUD`。

### Task 2.5: 物流服务商模块（复制 2.2 模板）
- [ ] 按模板实现 `logistics_provider` CRUD，权限 `logisticsProvider:*`，含 CRUD 测试。**Commit** `feat(basedata): logistics provider CRUD`。

### 阶段验收 2
- 四个基础数据模块 CRUD 全通；分类返回树；唯一约束生效。`mvn test` 绿。

---

## Phase 3 — 平台商品目录 SPU/SKU（需求 #5，解耦）

**目标产物：** SPU/SKU 基础 CRUD，不与流转联动。

### Task 3.1: 迁移 V3 + 模块
- [ ] **Step 1: `V3__product_catalog.sql`** — `product_spu / product_sku`（`sku_code` 唯一）。
- [ ] **Step 2:** 按模板实现 `product` 模块：SPU CRUD + 其下 SKU CRUD（`GET /api/product-spus/{id}/skus`）。权限 `product:spu:*`、`product:sku:*`。
- [ ] **Step 3: 测试** — 建 SPU→挂 2 个 SKU→按 SPU 查 SKU 列表=2。
- [ ] **Step 4: Commit** — `feat(product): spu/sku catalog CRUD`。

### 阶段验收 3
- SPU/SKU 可维护；与采购/库存/销售无耦合。

---

## Phase 4 — 供应商产品（需求 #6，核心操作单元）

**目标产物：** 供应商产品 CRUD + 联动筛选支撑接口；回填 Phase 2 的引用校验。

### Task 4.1: 迁移 V4 + 实体
- [ ] **Step 1: `V4__supplier_product.sql`** — 字段见 PRD §4.5，唯一 `(supplier_id, product_code)`；`min_purchase_qty` 默认 1；`retail_price`、`wholesale_price` `DECIMAL(12,2)`；`sku_id` 可空。
- [ ] **Step 2:** Entity/Mapper/DTO/VO；`SupplierProductSaveDTO` 校验：`@Min(1) minPurchaseQty`、价格 `@DecimalMin("0")`。
- [ ] **Step 3: Commit** — `feat(db,supplierproduct): V4 schema + entity`。

### Task 4.2: CRUD + 筛选
- [ ] **Step 1: 测试** — 创建供应商产品（带 MOQ=5）；`GET /api/supplier-products?supplierId=&brandId=&categoryId=&keyword=` 命中；重复 `(supplier,product_code)` 报错。
- [ ] **Step 2:** 实现 `SupplierProductController/Service`，权限 `supplierProduct:*`。
- [ ] **Step 3:** 实现联动筛选接口：`GET /api/suppliers/{id}/brands`、`GET /api/suppliers/{id}/categories`（基于该供应商已有产品 distinct）。
- [ ] **Step 4: Commit** — `feat(supplierproduct): CRUD + cascading filters`。

### Task 4.3: 回填基础数据删除校验
- [ ] **Step 1:** 在 `BrandService.delete/SupplierService.delete/CategoryService.delete` 增「被 `supplier_product` 引用则拒绝」校验。
- [ ] **Step 2: 测试** — 被引用的品牌删除返回 `code!=0`。
- [ ] **Step 3: Commit** — `feat(basedata): forbid delete when referenced by supplier_product`。

### 阶段验收 4
- 供应商产品可维护；联动筛选可用；MOQ/唯一/引用校验生效。

---

## Phase 5 — 采购链（需求 #8/#9/#10/#11）

**目标产物：** 采购计划→审批生单→付款→实际采购单→入库（库存↑）。本阶段是核心业务逻辑，**重 TDD**。库存表在此建立（入库需要）。

### Task 5.1: 迁移 V5（采购 + 库存表）
- [ ] **Step 1: `V5__purchase_and_inventory.sql`** — 建 `purchase_plan / purchase_plan_item / purchase_order / purchase_order_item / actual_purchase_order / actual_purchase_order_item / inventory_stock / inventory_transaction`（字段见 PRD §4.6/§4.7；`inventory_stock.supplier_product_id` 唯一 + `version`）。
- [ ] **Step 2: Commit** — `feat(db): V5 purchase + inventory schema`。

### Task 5.2: 采购计划创建/编辑/提交（MOQ 校验）
- [ ] **Step 1: 测试 `PurchasePlanServiceTest`**
```java
@Test void create_rejects_qty_below_moq() {
  // supplierProduct MOQ=5；明细 qty=3
  assertThatThrownBy(() -> planService.create(planWithItemQty(3, /*moq*/5)))
     .isInstanceOf(BusinessException.class)
     .extracting("code").isEqualTo(ResultCode.BELOW_MIN_PURCHASE_QTY.getCode());
}
@Test void create_ok_persists_snapshot_and_total() {
  Long id = planService.create(planWithItemQty(5,5)); // wholesale=100
  PurchasePlan p = planService.getDetail(id);
  assertThat(p.getStatus()).isEqualTo("DRAFT");
  assertThat(p.getTotalAmount()).isEqualByComparingTo("500.00");
}
```
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现** `PurchasePlanService.create/update/submit`：明细逐条校验 `qty>=MOQ`（qty=0 跳过该条；至少一条 qty>0）；保存产品名/编码/批发价/MOQ 快照；汇总 `total_qty/total_amount`；`submit` 仅 `DRAFT/REJECTED→PENDING`；编辑仅 `DRAFT/REJECTED`。权限 `purchase:plan:*`。
- [ ] **Step 4: 跑测试** — PASS。**Commit** `feat(purchase): plan create/edit/submit with MOQ validation`。

### Task 5.3: 审批生单（事务）
- [ ] **Step 1: 测试**
```java
@Test void approve_generates_one_order_per_supplier() {
  // 计划含 供应商A 2条、供应商B 1条
  approveService.approve(planId, approverId);
  List<PurchaseOrder> orders = orderMapper.selectByPlanId(planId);
  assertThat(orders).hasSize(2);               // 按供应商拆分
  assertThat(planService.getDetail(planId).getStatus()).isEqualTo("APPROVED");
}
@Test void reject_sets_status_and_reason() {
  approveService.reject(planId, approverId, "价格过高");
  assertThat(planService.getDetail(planId).getStatus()).isEqualTo("REJECTED");
}
```
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现 `PurchaseApproveService`**（`@Transactional`）：`approve` 校验 `PENDING`→按 `supplier_id` 分组生成 `purchase_order`+items（状态 `PENDING_PAYMENT`，明细 `payment_status=UNSET`，生成 `order_no`）→计划置 `APPROVED`；`reject`→`REJECTED`+原因。权限 `purchase:plan:approve`。非 `PENDING` 抛 `INVALID_STATUS_TRANSITION`。
- [ ] **Step 4: 跑测试** — PASS。**Commit** `feat(purchase): approval generates per-supplier orders (tx)`。

### Task 5.4: 付款标记 → 生成实际采购单（事务）
- [ ] **Step 1: 测试**
```java
@Test void confirm_builds_actual_order_from_paid_items_only() {
  paymentService.mark(orderId, List.of(item1Id), "PAID");
  paymentService.mark(orderId, List.of(item2Id), "UNPAID");
  Long actualId = paymentService.confirm(orderId);
  ActualPurchaseOrder a = actualService.getDetail(actualId);
  assertThat(a.getItems()).extracting("purchaseOrderItemId").containsExactly(item1Id);
  assertThat(orderService.get(orderId).getStatus()).isEqualTo("CONFIRMED");
}
@Test void confirm_requires_at_least_one_paid() {
  assertThatThrownBy(() -> paymentService.confirm(orderId)).isInstanceOf(BusinessException.class);
}
```
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现 `PurchaseOrderPaymentService`**：`mark(orderId, itemIds, status)` 更新明细 `payment_status` 与订单 `paid_amount`；`confirm(orderId)`（`@Transactional`）取 `PAID` 明细→建 `actual_purchase_order`(状态 `PENDING_INBOUND`)+items(`inbound_status=PENDING`)→订单置 `CONFIRMED`；无 PAID 明细抛异常。权限 `purchase:order:pay|confirm`。
- [ ] **Step 4: 跑测试** — PASS。**Commit** `feat(purchase): payment marking + actual order generation`。

### Task 5.5: 入库 → 库存增加（事务 + 幂等 + 流水）
- [ ] **Step 1: 测试**
```java
@Test void inbound_increments_stock_and_writes_transaction() {
  inboundService.inbound(actualOrderId, List.of(actualItemId)); // qty=5
  assertThat(stockService.getQty(supplierProductId)).isEqualTo(5);
  assertThat(txMapper.selectBySupplierProduct(supplierProductId))
     .anySatisfy(t -> assertThat(t.getType()).isEqualTo("PURCHASE_IN"));
}
@Test void inbound_is_idempotent_for_done_items() {
  inboundService.inbound(actualOrderId, List.of(actualItemId));
  inboundService.inbound(actualOrderId, List.of(actualItemId)); // 再次
  assertThat(stockService.getQty(supplierProductId)).isEqualTo(5); // 不翻倍
}
```
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现 `InboundService.inbound`**（`@Transactional`）：对每条目标明细，若 `inbound_status==DONE` 跳过；否则 `upsert inventory_stock`（无则建，有则 `quantity+=qty`，乐观锁失败重试≤3）+ 写 `inventory_transaction(PURCHASE_IN, ref=ACTUAL_PURCHASE_ORDER)` + 明细置 `DONE`/`inbound_time`；全部 DONE→实际采购单 `INBOUND_DONE`。权限 `inventory:inbound`。
```java
private void increase(Long spId, int qty, String refNo, Long refId) {
    for (int attempt = 0; attempt < 3; attempt++) {
        InventoryStock s = stockMapper.selectBySupplierProduct(spId);
        int before = s == null ? 0 : s.getQuantity();
        // ...建或乐观锁更新；成功则 break
        // 写流水 before/after
    }
}
```
- [ ] **Step 4: 跑测试** — PASS。**Commit** `feat(purchase): inbound increments stock with ledger (idempotent)`。

### 阶段验收 5
- 端到端：建计划→审批→按供应商生单→标付款→生成实际采购单→入库→库存增加且有流水。
- 状态机非法流转被拒；入库幂等。`mvn test` 绿。

---

## Phase 6 — 库存管理（需求 #12）

**目标产物：** 库存列表（联动筛选）+ 手工调整（带流水）。

### Task 6.1: 库存查询与手工调整
- [ ] **Step 1: 测试** — `GET /api/inventory/stocks?supplierId=&brandId=&categoryId=` 分页返回；`PUT /api/inventory/stocks/{supplierProductId}` 改量后查询一致，且生成 `MANUAL_ADJUST` 流水含 before/after。
- [ ] **Step 2:** 实现 `InventoryStockController/Service`：列表 join 供应商/品牌/分类名（VO）；手工调整经乐观锁 + 写 `inventory_transaction(MANUAL_ADJUST, ref=MANUAL)`；数量不可为负。权限 `inventory:list|edit`。
- [ ] **Step 3: Commit** — `feat(inventory): stock list + manual adjust with ledger`。

### 阶段验收 6
- 仓管可按联动条件查库存、手工校正且留痕。

---

## Phase 7 — 销售与物流（需求 #13/#14/#15）

**目标产物：** 销售下单（扣库存、客户信息）→物流派送→签收/拒收(回补)→完成结算。核心逻辑**重 TDD**。

### Task 7.1: 迁移 V6（销售表）
- [ ] **Step 1: `V6__sales.sql`** — `sales_order / sales_order_item`（字段见 PRD §4.8）。
- [ ] **Step 2: Commit** — `feat(db): V7 sales schema`。

### Task 7.2: 销售下单（扣库存 + 防超卖）
- [ ] **Step 1: 测试**
```java
@Test void create_deducts_stock_and_requires_customer() {
  // 库存=10；下单 qty=3，单价默认带出 retail=200
  Long id = salesService.create(orderDto(qty=3, customer="Kofi/024.../Accra"));
  assertThat(stockService.getQty(spId)).isEqualTo(7);
  SalesOrder o = salesService.getDetail(id);
  assertThat(o.getStatus()).isEqualTo("PENDING_DISPATCH");
  assertThat(o.getTotalAmount()).isEqualByComparingTo("600.00");
}
@Test void create_rejects_when_stock_insufficient() {
  assertThatThrownBy(() -> salesService.create(orderDto(qty=999, ...)))
     .extracting("code").isEqualTo(ResultCode.INSUFFICIENT_STOCK.getCode());
}
```
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现 `SalesOrderService.create`**（`@Transactional`）：客户三项 `@NotBlank`；每条明细单价默认取 `supplier_product.retail_price`（允许 DTO 覆盖）；逐条校验 `stock>=qty`，不足抛 `INSUFFICIENT_STOCK`；乐观锁扣减 + 写 `inventory_transaction(SALES_OUT)`；订单 `PENDING_DISPATCH`、`completed=0`、汇总金额。权限 `sales:order:create`。
- [ ] **Step 4: 跑测试** — PASS。**Commit** `feat(sales): create order deducts stock (no oversell)`。

### Task 7.3: 销售列表（本人 + 完成筛选 #15）
- [ ] **Step 1: 测试** — 销售员A 仅见自己订单；`completed=false` 返回未完成，`completed=true` 返回已完成。
- [ ] **Step 2:** 实现 `GET /api/sales-orders?completed=&...`：非全局查看权限者强制 `salesperson_id=当前用户`。权限 `sales:order:list`。
- [ ] **Step 3: Commit** — `feat(sales): order list with own-scope + completed filter`。

### Task 7.4: 物流派送 + 状态流转
- [ ] **Step 1: 测试** — `dispatch` 选服务商+派送费→`DISPATCHING`；非法流转（如 `PENDING_DISPATCH→SIGNED` 跳过派送）被拒 `INVALID_STATUS_TRANSITION`。
- [ ] **Step 2:** 实现 `POST /api/sales-orders/{id}/dispatch`、`PUT /api/sales-orders/{id}/status`，用状态机校验合法迁移（见 PRD §5.13）。权限 `logistics:dispatch|status`。
- [ ] **Step 3: Commit** — `feat(logistics): dispatch + status transitions`。

### Task 7.5: 拒收回补 + 完成结算
- [ ] **Step 1: 测试**
```java
@Test void reject_returns_stock_and_settlement_excludes_rejected() {
  // 明细 qty=3 单价200，库存下单后=7
  logisticsService.markReject(orderId, item, /*rejectQty*/1); // 在 SIGNED 下
  assertThat(stockService.getQty(spId)).isEqualTo(8);          // 回补 1
  logisticsService.complete(orderId);
  SalesOrder o = salesService.getDetail(orderId);
  assertThat(o.getActualAmount()).isEqualByComparingTo("400.00"); // 200*(3-1)
  assertThat(o.getCompleted()).isEqualTo(1);
}
@Test void reject_only_allowed_in_signed_states() {
  assertThatThrownBy(() -> logisticsService.markReject(dispatchingOrderId, item, 1))
     .isInstanceOf(BusinessException.class);
}
```
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现** `markReject`（仅 `SIGNED/SIGNED_PAID`；`@Transactional`：更新 `reject_qty` + 库存 `+=rejectQty` + `inventory_transaction(REJECT_RETURN)`）；`complete`（可从 `SIGNED/SIGNED_PAID`；计算 `actual_amount=Σ unit_price*(qty-reject_qty)`；`REJECTED` 全拒签则 0；`completed=1`、`complete_time`；完成后只读）。权限 `logistics:reject|complete`。
- [ ] **Step 4: 跑测试** — PASS。**Commit** `feat(logistics): reject restock + completion settlement`。

### 阶段验收 7
- 端到端：下单扣库存→派送→签收→拒收回补→完成算实收。状态机/防超卖/结算口径全部通过。`mvn test` 绿。

---

## Phase 8 — 收尾与加固

### Task 8.1: 权限种子数据
- [ ] **Step 1: `V7__menu_perm_seed.sql`** — 插入附录 B 全部 `perm_code` 菜单项 + 推荐角色模板（采购员/审批主管/仓库管理员/销售员/物流专员）与其菜单绑定。
- [ ] **Step 2: 测试** — 用「采购员」角色用户可访问 `purchase:plan:create`、不可访问 `inventory:edit`。
- [ ] **Step 3: Commit** — `feat(db): V8 menu/permission/role seed`。

### Task 8.2: 接口文档 + 端到端冒烟
- [ ] **Step 1:** 完善 Knife4j 注解；`/doc.html` 可浏览所有接口。
- [ ] **Step 2:** 写一个 `EndToEndSmokeTest`：登录→建基础数据→供应商产品→采购全链→入库→销售→物流完成，断言库存与实收金额最终值。
- [ ] **Step 3: Commit** — `test: end-to-end smoke + api docs`。

### Task 8.3: README + 首推
- [ ] **Step 1:** 写 `backend/README.md`（启动、profile、迁移、测试说明）。
- [ ] **Step 2: 推送** — `git push -u origin main`。

### 阶段验收 8
- 全链路冒烟绿；`/doc.html` 可用；权限种子可直接组角色；代码已推远程。

---

## 自查（Spec 覆盖核对）

| PRD 需求 | 落地位置 |
|----------|----------|
| #1 登录/RBAC | Phase 1 |
| #2 品牌 | Phase 2 (Task 2.2) |
| #3 供应商 | Phase 2 (Task 2.3) |
| #4 分类 | Phase 2 (Task 2.4) |
| #5 SPU/SKU | Phase 3 |
| #6 供应商产品 | Phase 4 |
| #7 物流服务商 | Phase 2 (Task 2.5) |
| #8 采购计划 | Phase 5 (Task 5.2) |
| #9 审批生单 | Phase 5 (Task 5.3) |
| #10 付款/实际采购单 | Phase 5 (Task 5.4) |
| #11 入库 | Phase 5 (Task 5.5) |
| #12 库存 | Phase 6 |
| #13 销售下单 | Phase 7 (Task 7.2) |
| #14 物流/结算 | Phase 7 (Task 7.4/7.5) |
| #15 完成情况筛选 | Phase 7 (Task 7.3) |

**约定**：实体/DTO/VO 命名遵循 `XxxEntity` 不加后缀（PO 直接 `Brand`）、入参 `XxxSaveDTO/XxxQueryDTO`、出参 `XxxVO`；service 接口 + impl 分离；所有跨表写操作 `@Transactional`；库存增减必经 `inventory_stock`+`inventory_transaction` 双写。
