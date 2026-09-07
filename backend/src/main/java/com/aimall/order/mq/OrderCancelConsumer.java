package com.aimall.order.mq;

import com.aimall.order.bean.Order;
import com.aimall.order.mapper.OrderItemMapper;
import com.aimall.order.mapper.OrderMapper;
import com.aimall.goods.mapper.ProductSkuMapper;
import com.aimall.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单超时未支付 → 自动取消（延迟消息消费端）。
 *
 * <h2>★ 幂等是这里的生命线</h2>
 * 消息可能重复投递，而且"用户手动取消"与"超时自动取消"会<b>竞争同一个订单</b>：
 * <pre>
 *   线程A：用户点取消    ─┐
 *   线程B：超时消息到达  ─┴─► 都要把 PENDING_PAY 改成 CANCELLED
 * </pre>
 * 若不做防护：两次都成功 → 库存被回补两次 → 库存虚增（真实资损）。
 *
 * <p><b>解法：状态机 CAS</b>——{@code updateStatus WHERE status='PENDING_PAY'}，
 * 谁先改到谁生效，另一个得到 0 行直接返回。这与扣库存的"行锁 CAS"是同一套思想。</p>
 *
 * <h2>为什么回补库存也要在"取消成功"之后</h2>
 * 只有真正把状态改成 CANCELLED 的那个线程才回补库存——
 * 顺序错了就会出现"状态没变但库存加了"的不一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelConsumer {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSkuMapper skuMapper;
    private final PaymentService paymentService;

    @RabbitListener(queues = OrderDelayMqConfig.CANCEL_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void onCancelMessage(Long orderId) {
        try {
            cancelIfStillPending(orderId);
        } catch (Exception e) {
            // 吞异常不重入队：超时取消失败可由补偿任务/人工兜底，
            // 无限重试只会打爆日志（与审核消费端同样的取舍）
            log.error("超时取消订单失败（已记日志，不重试）orderId={} : {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 仅当订单仍是待支付时才取消 —— 幂等 + 防与支付回调竞争。
     *
     * <p>★ 注意这里<b>刻意不加</b> @Transactional：本方法只被同类的
     * onCancelMessage（已带事务）自调用——自调用不走代理，注解是摆设
     * （与 @Async 自调用失效同源，V2 修过一次）。事务由外层监听方法统一开启。</p>
     *
     * @return 是否真的执行了取消
     */
    public boolean cancelIfStillPending(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在，跳过超时取消 orderId={}", orderId);
            return false;
        }
        if (!Order.STATUS_PENDING_PAY.equals(order.getStatus())) {
            // 已支付/已取消：什么都不做（这是最常见的正常分支）
            log.info("订单已非待支付，跳过超时取消 orderId={} status={}",
                    orderId, order.getStatus());
            return false;
        }

        // 状态机 CAS：并发下只有一个线程能改成功
        int rows = orderMapper.updateStatus(orderId,
                Order.STATUS_PENDING_PAY, Order.STATUS_CANCELLED, LocalDateTime.now());
        if (rows == 0) {
            log.info("订单状态已被其他线程变更，取消跳过 orderId={}", orderId);
            return false;
        }

        // 回补库存（下单时扣了多少就加回多少）
        orderItemMapper.selectByOrderId(orderId)
                .forEach(oi -> skuMapper.addStock(oi.getSkuId(), oi.getQuantity()));

        // 关闭支付单（避免用户之后还能对着已取消的订单付款）
        paymentService.closeIfPaying(orderId);

        log.info("订单超时未支付已自动取消并回补库存 orderId={}", orderId);
        return true;
    }
}
