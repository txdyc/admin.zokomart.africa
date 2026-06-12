-- ===========================================================================
-- V1: 系统管理 / RBAC 表结构
-- 用户—角色—菜单(权限) 多对多。所有主键由应用层 (MyBatis-Plus ASSIGN_ID) 生成。
-- 业务表统一含审计字段 + 逻辑删除 deleted + 乐观锁 version。
-- ===========================================================================

CREATE TABLE sys_user (
    id          BIGINT       NOT NULL COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    nickname    VARCHAR(64)           DEFAULT NULL COMMENT '显示名',
    phone       VARCHAR(32)           DEFAULT NULL COMMENT '手机号',
    email       VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    is_super    TINYINT      NOT NULL DEFAULT 0 COMMENT '0否 1超级管理员',
    remark      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    create_by   BIGINT                DEFAULT NULL,
    update_by   BIGINT                DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户';

CREATE TABLE sys_role (
    id          BIGINT       NOT NULL COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '角色名',
    code        VARCHAR(64)  NOT NULL COMMENT '角色编码',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    remark      VARCHAR(255)          DEFAULT NULL,
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    create_by   BIGINT                DEFAULT NULL,
    update_by   BIGINT                DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

CREATE TABLE sys_menu (
    id          BIGINT       NOT NULL COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点，0为根',
    name        VARCHAR(64)  NOT NULL COMMENT '名称',
    type        TINYINT      NOT NULL COMMENT '1目录 2菜单 3按钮',
    perm_code   VARCHAR(128)          DEFAULT NULL COMMENT '权限码(按钮级)',
    route_path  VARCHAR(255)          DEFAULT NULL COMMENT '前端路由',
    component   VARCHAR(255)          DEFAULT NULL COMMENT '前端组件',
    icon        VARCHAR(64)           DEFAULT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    visible     TINYINT      NOT NULL DEFAULT 1 COMMENT '0隐藏 1显示',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    create_by   BIGINT                DEFAULT NULL,
    update_by   BIGINT                DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_menu_parent (parent_id),
    KEY idx_menu_perm (perm_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单/权限';

CREATE TABLE sys_user_role (
    id          BIGINT NOT NULL COMMENT '主键',
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    create_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_ur_user (user_id),
    KEY idx_ur_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色';

CREATE TABLE sys_role_menu (
    id          BIGINT NOT NULL COMMENT '主键',
    role_id     BIGINT NOT NULL,
    menu_id     BIGINT NOT NULL,
    create_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_rm_role (role_id),
    KEY idx_rm_menu (menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-菜单';
