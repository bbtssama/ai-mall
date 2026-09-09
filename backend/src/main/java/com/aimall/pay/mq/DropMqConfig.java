package com.aimall.pay.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限量发售 MQ 拓扑 —— 「Redis 预减 + MQ 异步下单削峰」的消息侧。
 *
 * <pre>
 *   submitBuy（同步链路：Lua 预减成功）
 *      │ routingKey = drop.order
 *      ▼
 *   [aimall.drop.exchange] ──► [aimall.drop.order.queue] ──► DropOrderConsumer
 *                                    │                        └─ 事务建单（CAS+uk 幂等）
 *                                    └─ 死信：重试耗尽/未知异常 ──► [aimall.drop.order.dlq]（人工兜底）
 * </pre>
 *
 * <h2>★ 为什么秒杀下单要过 MQ（削峰的本质）</h2>
 * 限量发售的瞬时流量模型是"开抢瞬间 1 万 QPS，之后归零"。DB 的行锁在热点 SKU 上
 * 只能串行处理约几百 TPS——同步建单等于让这 1 万个请求<b>排着队抢同一把行锁</b>，
 * 连接池先被打爆。MQ 把"瞬时洪峰"摊平成消费者能承受的平稳速率：
 * <ul>
 *   <li>同步链路只做内存操作（Lua 预减）+ 一次 MQ 投递，毫秒级返回"已受理"；</li>
 *   <li>DB 只承受消费端的匀速写入，行锁不再被万级请求争抢；</li>
 *   <li>Redis 预减保证放行的消息数 ≤ 库存量，队列里没有"注定失败"的垃圾请求。</li>
 * </ul>
 *
 * <h2>可靠性三件套在本链路的落地</h2>
 * <ul>
 *   <li><b>不丢（发送端）</b>：MQ 投递失败立即回补 Redis 预减并告知用户重试，
 *       不允许"扣了名额但消息没出去"的白占坑；</li>
 *   <li><b>不丢（存储）</b>：队列/消息均 durable；</li>
 *   <li><b>不乱（消费端）</b>：drop_record 的 uk(activity_id,user_id) 天然幂等，
 *       重复投递撞唯一索引 → 幂等跳过。</li>
 * </ul>
 */
@Configuration
public class DropMqConfig {

    public static final String EXCHANGE = "aimall.drop.exchange";
    /** 抢购建单队列（消费者：DropOrderConsumer） */
    public static final String ORDER_QUEUE = "aimall.drop.order.queue";
    public static final String ORDER_ROUTING_KEY = "drop.order";
    /** 死信队列：消费端未知异常（DB 抖动等）重试耗尽后进入，人工兜底 */
    public static final String DLQ = "aimall.drop.order.dlq";
    public static final String DLQ_ROUTING_KEY = "drop.order.dlq";

    @Bean
    public DirectExchange dropExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue dropOrderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue dropDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding dropOrderBinding() {
        return BindingBuilder.bind(dropOrderQueue())
                .to(dropExchange())
                .with(ORDER_ROUTING_KEY);
    }

    @Bean
    public Binding dropDlqBinding() {
        return BindingBuilder.bind(dropDlq())
                .to(dropExchange())
                .with(DLQ_ROUTING_KEY);
    }
}
