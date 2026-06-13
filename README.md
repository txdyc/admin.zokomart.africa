# ZokoMart Admin · Backend

面向非洲（加纳）电商独立站 **ZokoMart** 的自研后台管理系统后端，对外提供 RESTful API，
供基于 Vben Admin 的前端（仓库 `front.admin.zokomart.africa`）调用。

> 本仓库为后端服务；前端为独立仓库。两者各自独立提交、独立部署。

## 技术栈

| 组件 | 选型 |
|------|------|
| 语言 / JDK | Java 21 (LTS) |
| 框架 | Spring Boot 3.5.x |
| 构建 | Maven |
| 数据库 | MySQL（库名 `zokomart_admin`，utf8mb4） |
| ORM | MyBatis-Plus（单表 CRUD 用 MP，复杂查询写 XML） |
| 迁移 | Flyway（`db/migration/V*.sql`） |
| 缓存 / 会话 | Redis |
| 鉴权 | Sa-Token（RBAC，会话存 Redis，按钮级 `perm_code`） |
| 接口文档 | Knife4j / OpenAPI3（`/doc.html`） |
| 基础包名 | `africa.zokomart.admin` |

## 环境要求

- **JDK 21**（本机路径示例 `C:\Program Files\Java\jdk-21.0.11`）。
- **MySQL** 本地 `3306`，需先建库：

  ```sql
  CREATE DATABASE zokomart_admin DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
  ```

  表结构由 Flyway 启动时自动迁移，无需手工建表。
- **Redis** 本地 `6379`。

## 本地配置（密钥不入库）

多环境用 Spring profile：`application.yml` 的 `spring.profiles.active: dev,local`。

- `application.yml` / `application-dev.yml`：通用 + 占位/默认值，已提交。
- `application-local.yml`：**仅放本机密钥/连接**（MySQL、Redis 密码等），
  **已被 `.gitignore` 忽略，切勿提交**。模板：

  ```yaml
  spring:
    datasource:
      username: root
      password: <你的 MySQL 密码>
    data:
      redis:
        host: localhost
        port: 6379
        password: <Redis 密码，无则留空>
  ```

数据库连接串、Sa-Token、MyBatis-Plus 等其余配置见 `application.yml` / `application-dev.yml`。

## 启动 / 构建 / 测试

> 项目锁定 JDK 21；若默认 `JAVA_HOME` 指向其他版本，命令前**内联覆盖**。
> 以下命令在 `backend/` 目录下执行。

```bash
# 内联指定 JDK 21（Windows Git-Bash 示例）
export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11"

mvn clean compile        # 编译
mvn spring-boot:run      # 本地启动（默认端口 8080）
mvn test                 # 运行全部测试（@SpringBootTest，依赖本地 MySQL + Redis）
mvn clean package        # 打包可执行 jar
```

启动后：

- 服务地址：`http://localhost:8080`
- 接口文档：`http://localhost:8080/doc.html`（OpenAPI JSON：`/v3/api-docs`）

## 数据库迁移（Flyway）

迁移脚本位于 `src/main/resources/db/migration/`，**只新增、不改历史**：

| 版本 | 内容 |
|------|------|
| V1 | 系统管理 / RBAC（用户、角色、菜单、关联表） |
| V2 | 基础数据（品牌、供应商、分类、物流服务商） |
| V3 | 平台目录（SPU / SKU） |
| V4 | 供应商产品 |
| V5 | 采购链 + 库存（计划/订单/实际采购单、库存与流水） |
| V6 | 销售与物流 |
| V7 | 菜单/权限码种子 + 5 个推荐角色模板及角色-菜单绑定 |

> 若某次迁移中途失败导致 `flyway_schema_history` 出现失败记录，需先 `flyway repair`
> 清理失败条目，并移除半成品数据，再重新启动。

## 默认账号与权限

- 启动时由 `SuperAdminInitializer` **幂等种入**超级管理员：
  **`superadmin` / `Admin@123`**（`is_super=1`，权限通配 `*`）。
  **上线前必须修改密码。**
- V7 已种入 5 个推荐角色模板（超管可在后台动态增删改）：
  采购员 `BUYER`、审批主管 `APPROVER`、仓库管理员 `WAREHOUSE`、销售员 `SALES`、物流专员 `LOGISTICS`。
  各角色已绑定其职责所需的菜单/按钮（`perm_code`）。

## 工程结构与分层

```
src/main/java/africa/zokomart/admin/
├── AdminApplication.java        # 启动类
├── common/                      # 通用层：result / exception / base / constant
├── config/                      # 配置：MyBatisPlus / Redis / SaToken / Cors / Knife4j
└── module/                      # 业务模块（按领域）
    ├── system/                  # RBAC：用户/角色/菜单/鉴权
    ├── basedata/                # 品牌/供应商/分类/物流服务商
    ├── product/                 # 平台目录 SPU/SKU
    ├── supplierproduct/         # 供应商产品
    ├── purchase/                # 采购计划/订单/实际采购单
    ├── inventory/               # 库存与流水（库存增减唯一入口 changeStock）
    └── sales/                   # 销售下单 + 物流/结算
```

- 分层：`controller`（仅校验/编排）→ `service`(+`impl`)（业务 + 事务）→ `mapper`（数据访问）。
- 每模块 `entity / mapper / dto / vo / service(+impl) / controller`；对外不暴露 entity。
- 统一返回 `Result<T>` / `PageResult<T>`；业务异常抛 `BusinessException`，由 `GlobalExceptionHandler` 统一处理。
- 鉴权用注解 `@SaCheckLogin` / `@SaCheckPermission(...)`；权限码与前端 Vben 按钮级权限对齐。
- 库存增减必经 `InventoryStockService.changeStock`（`inventory_stock` + `inventory_transaction` 双写）。

## 红线

- 密钥（DB/Redis 密码、Sa-Token 盐值、第三方 key）只放 `application-local.yml`，严禁入库。
- 不在日志记录 token 与完整客户 PII。
- 表结构变更只新增 Flyway 迁移，不改历史脚本。
