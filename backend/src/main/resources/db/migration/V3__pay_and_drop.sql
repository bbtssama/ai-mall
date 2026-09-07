-- =====================================================================
-- V3__pay_and_drop.sql —— 支付 + 限量发售
--
-- 两件事：
--   1. 支付：补齐商业闭环最后一环（下单 → 支付 → 回调 → 幂等 → 状态流转）
--   2. 限量发售（Limited Drop）：替换"秒杀"设计。
--      业务理由：本项目对标得物/潮玩电商，真实场景是"限量发售/抽签"，
--      不是淘宝双十一秒杀。防超卖的技术含量不变，但业务讲得通。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 支付
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_payment (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    payment_no     VARCHAR(32)   NOT NULL COMMENT '支付单号(业务唯一，幂等核心)',
    order_id       BIGINT        NOT NULL COMMENT '订单 id',
    order_no       VARCHAR(32)   NOT NULL COMMENT '订单号(冗余，对账与日志排查用)',
    user_id        BIGINT        NOT NULL COMMENT '支付用户(冗余，回调时无需回查订单即可鉴权)',
    amount         DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    channel        VARCHAR(20)   NOT NULL COMMENT '支付渠道：ALIPAY / WECHAT / MOCK(本地模拟渠道)',
    status         VARCHAR(20)   NOT NULL DEFAULT 'PAYING' COMMENT '状态：PAYING待支付 PAID已支付 CLOSED已关闭 FAILED支付失败',
    third_trade_no VARCHAR(64)   DEFAULT NULL COMMENT '第三方交易号(回调带来，对账用)',
    callback_body  TEXT          COMMENT '回调原始报文(留痕，便于排查与补单)',
    paid_time      DATETIME      DEFAULT NULL COMMENT '支付成功时间',
    expire_time    DATETIME      DEFAULT NULL COMMENT '支付单过期时间(超时关单)',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    -- ★ 幂等核心：payment_no 唯一，重复回调撞唯一索引
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_order (order_id),
    KEY idx_user (user_id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='支付单表(回调幂等依据)';

-- ---------------------------------------------------------------------
-- 限量发售
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_drop_activity (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    title        VARCHAR(150)  NOT NULL COMMENT '活动标题，如「AirSound Pro 星野蓝 限量首发」',
    product_id   BIGINT        NOT NULL COMMENT '商品 id',
    sku_id       BIGINT        NOT NULL COMMENT '参与发售的 SKU',
    drop_price   DECIMAL(10,2) NOT NULL COMMENT '发售价格',
    drop_stock   INT          NOT NULL COMMENT '发售总量',
    per_limit    INT          NOT NULL DEFAULT 1 COMMENT '每人限购数量',
    start_time   DATETIME      NOT NULL COMMENT '开售时间',
    end_time     DATETIME      NOT NULL COMMENT '结束时间',
    status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待开始 ONGOING进行中 ENDED已结束',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sku (sku_id),
    KEY idx_status_time (status, start_time)
) ENGINE = InnoDB COMMENT ='限量发售活动表';

-- 发售成功记录：用于「每人限购」校验（唯一索引天然幂等，防重复下单）
CREATE TABLE IF NOT EXISTS t_drop_record (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT   NOT NULL COMMENT '活动 id',
    user_id     BIGINT   NOT NULL COMMENT '用户 id',
    order_id    BIGINT   NOT NULL COMMENT '对应订单 id',
    quantity    INT      NOT NULL DEFAULT 1 COMMENT '购买数量',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    -- ★ 一人一单：同一活动同一用户只能有一条记录（限购的幂等保障）
    UNIQUE KEY uk_activity_user (activity_id, user_id),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='限量发售成功记录表(限购幂等)';

-- ---------------------------------------------------------------------
-- 售后（V3 只建表，退款流程留 V3.5/企业实践扩展）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_refund (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    refund_no   VARCHAR(32)   NOT NULL COMMENT '退款单号',
    order_id    BIGINT        NOT NULL COMMENT '订单 id',
    payment_no  VARCHAR(32)   DEFAULT NULL COMMENT '原支付单号',
    amount      DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    reason      VARCHAR(255)  DEFAULT NULL COMMENT '退款原因',
    status      VARCHAR(20)   NOT NULL DEFAULT 'APPLYING' COMMENT '状态：APPLYING申请中 SUCCESS已退款 REJECTED已驳回',
    handled_at  DATETIME      DEFAULT NULL COMMENT '处理时间',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='退款表';
