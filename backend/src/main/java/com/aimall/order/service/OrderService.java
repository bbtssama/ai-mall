package com.aimall.order.service;

import com.aimall.common.page.PageQuery;
import com.aimall.common.page.PageResult;
import com.aimall.order.dto.CreateOrderRequest;
import com.aimall.order.dto.OrderVO;

/**
 * 订单服务：下单 / 我的订单 / 详情 / 取消
 */
public interface OrderService {

    /**
     * 创建订单（事务）：
     * 校验 SKU/上架/库存 → CAS 扣库存（WHERE stock>=?） → 生成订单号 → 落订单+明细 → 清理购物车
     */
    OrderVO create(CreateOrderRequest req);

    /**
     * 签发下单幂等 token（结算页加载时调用，TTL 10 分钟一次性使用）。
     * 双击"提交订单"的第二次会因 token 已被消费而被拒。
     */
    String createIdempotentToken();

    PageResult<OrderVO> pageMyOrders(PageQuery query);

    OrderVO detail(Long id);

    /** 取消待支付订单：状态机 CAS 迁移 + 回补库存 */
    void cancel(Long id);
}