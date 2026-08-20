-- =====================================================================
-- AI 种草商城 V1 数据库初始化脚本
-- 面向 MySQL 8.x，库名 ai_mall
-- 说明：V1 为单体+无 Redis 阶段；t_cart 暂用 MySQL 存储，
--       V3 引入 Redis 后迁移为 Hash(user_id -> sku_id -> count)。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS ai_mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai_mall;

-- ---------------------------------------------------------------------
-- 用户域
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    nickname      VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    avatar        VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
    role          TINYINT      NOT NULL DEFAULT 0 COMMENT '身份：0普通用户 1商家',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT ='用户表';

-- ---------------------------------------------------------------------
-- 电商域
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_product;
CREATE TABLE t_product (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    spu_name    VARCHAR(150) NOT NULL COMMENT '商品名称(SPU)',
    sub_title   VARCHAR(255) DEFAULT NULL COMMENT '副标题/卖点',
    category_id BIGINT       DEFAULT NULL COMMENT '分类 id（V1 简化，不建分类表）',
    main_img    VARCHAR(255) DEFAULT NULL COMMENT '主图 URL',
    detail      TEXT         COMMENT '图文详情/描述（V1 AI 问答的预置知识来源）',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1上架 0下架',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='商品表(SPU)';

DROP TABLE IF EXISTS t_product_sku;
CREATE TABLE t_product_sku (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id BIGINT        NOT NULL COMMENT '所属商品 id',
    sku_name   VARCHAR(100)  NOT NULL COMMENT '规格名，如 曜石黑 32G',
    price      DECIMAL(10,2) NOT NULL COMMENT '售价',
    stock      INT           NOT NULL DEFAULT 0 COMMENT '库存',
    sales      INT           NOT NULL DEFAULT 0 COMMENT '销量',
    version    INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（V3 秒杀/防超卖使用）',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_product (product_id)
) ENGINE = InnoDB COMMENT ='商品 SKU 表';

-- V1 购物车：MySQL 存储；V3 迁 Redis Hash(user_id -> sku_id -> count)
DROP TABLE IF EXISTS t_cart;
CREATE TABLE t_cart (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT   NOT NULL COMMENT '用户 id',
    sku_id     BIGINT   NOT NULL COMMENT 'SKU id',
    quantity   INT      NOT NULL DEFAULT 1 COMMENT '数量',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_sku (user_id, sku_id)
) ENGINE = InnoDB COMMENT ='购物车表(V1 MySQL 版)';

DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no         VARCHAR(32)   NOT NULL COMMENT '订单号(业务唯一)',
    user_id          BIGINT        NOT NULL COMMENT '用户 id',
    total_amount     DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING_PAY' COMMENT '状态机：PENDING_PAY待支付 PAID已支付 SHIPPED已发货 COMPLETED已完成 CANCELLED已取消',
    receiver_name    VARCHAR(50)   DEFAULT NULL COMMENT '收货人',
    receiver_phone   VARCHAR(20)   DEFAULT NULL COMMENT '收货电话',
    receiver_address VARCHAR(255)  DEFAULT NULL COMMENT '收货地址',
    pay_type         VARCHAR(20)   DEFAULT NULL COMMENT '支付方式（V2 沙箱支付后启用：ALIPAY/WECHAT）',
    pay_time         DATETIME      DEFAULT NULL COMMENT '支付时间',
    cancel_time      DATETIME      DEFAULT NULL COMMENT '取消时间',
    version          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB COMMENT ='订单主表';

DROP TABLE IF EXISTS t_order_item;
CREATE TABLE t_order_item (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id     BIGINT        NOT NULL COMMENT '订单 id',
    sku_id       BIGINT        NOT NULL COMMENT 'SKU id',
    product_name VARCHAR(150)  NOT NULL COMMENT '商品名快照',
    sku_name     VARCHAR(100)  NOT NULL COMMENT '规格名快照',
    price        DECIMAL(10,2) NOT NULL COMMENT '成交单价快照',
    quantity     INT           NOT NULL COMMENT '数量',
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='订单明细表(商品信息冗余快照)';

-- ---------------------------------------------------------------------
-- AI 域
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_conversation;
CREATE TABLE t_conversation (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL COMMENT '用户 id',
    biz_type   VARCHAR(20) NOT NULL DEFAULT 'CHAT' COMMENT '会话类型：CHAT通用 CHAT_GOODS商品问答（V2 起扩展 SHOPPING导购）',
    title      VARCHAR(100) DEFAULT NULL COMMENT '会话标题',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='AI 会话表';

DROP TABLE IF EXISTS t_message;
CREATE TABLE t_message (
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id BIGINT      NOT NULL COMMENT '会话 id',
    role            VARCHAR(20) NOT NULL COMMENT '角色：user / assistant',
    content         TEXT        COMMENT '消息内容',
    extra_json      TEXT        COMMENT '扩展信息（V2 起：引用来源/商品卡片）',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_conversation (conversation_id)
) ENGINE = InnoDB COMMENT ='AI 会话消息表';

-- =====================================================================
-- 种子数据：演示商品（贴合种草场景，供商城页与 AI 问答使用）
-- =====================================================================

INSERT INTO t_product (id, spu_name, sub_title, category_id, main_img, detail, status) VALUES
(1, 'AirSound Pro 真无线降噪耳机', '主动降噪 + 36小时续航 + 蓝牙5.3', 101,
 'https://picsum.photos/seed/airpro/480/480',
 'AirSound Pro 采用 11mm 复合振膜动圈单元，支持蓝牙 5.3 与 AAC/LDAC 编码。主动降噪深度可达 42dB，通透模式一键切换。单次续航 8 小时，配合充电仓总续航 36 小时，支持无线充电与 IPX5 防水。支持双设备同时连接，通话采用 4 麦克风 AI 降噪算法。', 1),
(2, '闪充宝 65W 氮化镓充电器', '小体积大功率，兼容手机/笔记本', 102,
 'https://picsum.photos/seed/gallium/480/480',
 '闪充宝 65W 采用氮化镓(GaN) 技术，体积比传统 65W 充电器小 40%。双 USB-C + 单 USB-A 三口设计，支持 PD3.0 / QC4+ / PPS 协议，可同时为手机、平板与笔记本供电。内置 8 重安全防护（过压/过流/过温/短路等），搭配 1.5m 编制充电线。', 1),
(3, '云朵亲肤保湿唇釉', '水感质地不拔干，显白豆沙色', 103,
 'https://picsum.photos/seed/lipstick/480/480',
 '云朵唇釉采用微乳化水感配方，含水量 45%，上唇 3 秒成膜不拔干。主打显白豆沙色系，持妆 6 小时不脱色，含维生素 E 与角鲨烷养护成分。管身采用磨砂玻璃设计，附送迷你卸妆湿巾。', 1),
(4, '轻氧 智能手环 6', '1.62寸AMOLED屏，血氧心率监测，14天续航', 104,
 'https://picsum.photos/seed/band6/480/480',
 '轻氧智能手环 6 配备 1.62 英寸 AMOLED 高清屏，支持心率、血氧、睡眠与 100+ 运动模式监测。5ATM 防水，典型使用续航 14 天。支持消息提醒、久坐提醒、女性健康管理，与 iOS / Android 双平台 App 联动。', 1);

INSERT INTO t_product_sku (id, product_id, sku_name, price, stock, sales, version) VALUES
(1, 1, '曜石黑', 399.00, 1000, 356, 0),
(2, 1, '奶白色', 399.00, 800, 289, 0),
(3, 2, '单头版', 89.00, 500, 1200, 0),
(4, 2, '三口套装版', 129.00, 300, 680, 0),
(5, 3, '豆沙色', 69.00, 1500, 2330, 0),
(6, 3, '蜜桃色', 69.00, 1200, 1890, 0),
(7, 4, '标准版', 199.00, 900, 760, 0),
(8, 4, '表带礼盒版', 249.00, 400, 320, 0);