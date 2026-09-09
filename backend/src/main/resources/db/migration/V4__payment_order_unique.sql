-- =====================================================================
-- V4__payment_order_unique.sql —— 支付单"一订单一活单"约束
--
-- P2 修复背景：PaymentService.create 的"查复用→插入"非原子，
-- 并发双击可产生两张 PAYING 单。解法 = DB 层兜底：order_id 唯一索引。
--
-- 由此带来的模型变化（业务上讲得通）：
--   一个订单的支付尝试<b>只占一行</b>：PAYING → PAID/CLOSED 后，
--   再次发起支付不再是插新行，而是"复用该行重开"（换 payment_no、
--   重置金额/渠道/过期时间）。PAID 行永不可重开（条件更新守卫）。
-- =====================================================================

-- 先清历史可能的重复行（保留每个订单最新一条），否则唯一索引建不起来
DELETE p1 FROM t_payment p1
    INNER JOIN t_payment p2
    ON p1.order_id = p2.order_id
   AND p1.id < p2.id;

ALTER TABLE t_payment
    ADD UNIQUE KEY uk_order_id (order_id);
