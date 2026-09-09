package com.aimall.order.mq;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单延迟消息拓扑：<b>TTL + 死信队列（DLX）</b>实现"30 分钟未支付自动取消"。
 *
 * <pre>
 *   下单成功
 *      │ 发送到 delay 队列（无消费者！）
 *      ▼
 *   [order.delay.queue]  队列设 x-message-ttl=30min，x-dead-letter-* 指向业务交换机
 *      │  消息在队列里"躺"30 分钟后过期
 *      │  Broker 自动把它投递到死信交换机（按 dlx-routing-key 重新路由）
 *      ▼
 *   [order.exchange] ──► [order.cancel.queue] ──► OrderCancelConsumer
 *                                                  └─ 幂等：仅待支付才取消 + 回补库存
 * </pre>
 *
 * <h2>★ 为什么不用 rabbitmq-delayed-message-exchange 插件</h2>
 * 官方延迟插件很好用，但它<b>要求 Broker 安装插件</b>——生产环境不一定装得了，
 * 本地 docker-compose 起的原生 RabbitMQ 也没有。TTL+DLX 是<b>零插件</b>的标准方案。
 *
 * <h2>TTL+DLX 的注意事项（面试加分）</h2>
 * <ul>
 *   <li><b>队列级 TTL vs 消息级 TTL</b>：消息级 TTL（每条不同过期时间）在
 *       RabbitMQ 里有"队头阻塞"问题——队首消息没过期，后面的即使过期也不会被投递。
 *       本项目所有订单过期时间一致（30 分钟），所以用<b>队列级 TTL</b>，
 *       既避开这个坑，也少一个 per-message TTL 参数的开销。
 *       （若将来要做"不同等级用户不同超时"，应改用延迟插件或定时任务。）</li>
 *   <li><b>消息不丢</b>：delay 队列与 cancel 队列都声明为 durable，
 *       Broker 重启后消息仍在（过期时间从入队起算，不会重置）。</li>
 * </ul>
 */
@Configuration
public class OrderDelayMqConfig {

    public static final String ORDER_EXCHANGE = "aimall.order.exchange";
    /** 延迟队列（无消费者，靠 TTL 过期后转投死信） */
    public static final String DELAY_QUEUE = "aimall.order.delay.queue";
    /** 真正被消费的"取消订单"队列 */
    public static final String CANCEL_QUEUE = "aimall.order.cancel.queue";
    public static final String CANCEL_ROUTING_KEY = "order.cancel";
    /** 死信路由键：delay 队列过期后按此 key 重新路由到本交换机 */
    public static final String DLX_ROUTING_KEY = "order.cancel.dlx";
    /** 取消队列的消费失败死信队列（P1：取消链路此前无 DLX，失败=消息消失） */
    public static final String CANCEL_DLQ = "aimall.order.cancel.dlq";
    public static final String CANCEL_DLQ_ROUTING_KEY = "order.cancel.fail";

    /** 订单支付超时时间（毫秒）：30 分钟 */
    public static final long ORDER_TTL_MILLIS = 30 * 60 * 1000L;

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    /**
     * 延迟队列：设置 TTL + 死信交换机。
     *
     * <p>关键参数：</p>
     * <ul>
     *   <li>{@code x-message-ttl}：消息存活时间，过期即"死亡"</li>
     *   <li>{@code x-dead-letter-exchange}：死亡后投递到哪个交换机</li>
     *   <li>{@code x-dead-letter-routing-key}：重新路由用的 key（决定最终进哪个队列）</li>
     * </ul>
     */
    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", ORDER_TTL_MILLIS);
        args.put("x-dead-letter-exchange", ORDER_EXCHANGE);
        args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);
        return QueueBuilder.durable(DELAY_QUEUE).withArguments(args).build();
    }

    /**
     * 取消队列也挂死信（P1 修复）：消费端抛出的未知异常经容器 reject 后
     * （default-requeue-rejected=false）转入 DLQ 人工兜底，而不是凭空消失。
     *
     * <p>⚠️ 注意：RabbitMQ 不允许"参数不同的同名队列"重复声明——
     * 已有旧版（无 DLX 参数）队列的环境需先删除旧队列再启动：
     * {@code rabbitmqadmin delete queue name=aimall.order.cancel.queue}。</p>
     */
    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(CANCEL_QUEUE)
                .deadLetterExchange(ORDER_EXCHANGE)
                .deadLetterRoutingKey(CANCEL_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderCancelDlq() {
        return QueueBuilder.durable(CANCEL_DLQ).build();
    }

    /** 取消队列绑定：正常 routing key */
    @Bean
    public Binding cancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderExchange())
                .with(CANCEL_ROUTING_KEY);
    }

    /** 死信路由绑定：delay 队列过期后按 DLX_ROUTING_KEY 投递，也要能路由到取消队列 */
    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderExchange())
                .with(DLX_ROUTING_KEY);
    }

    /** 取消队列的消费失败死信绑定 */
    @Bean
    public Binding cancelDlqBinding() {
        return BindingBuilder.bind(orderCancelDlq())
                .to(orderExchange())
                .with(CANCEL_DLQ_ROUTING_KEY);
    }
}
