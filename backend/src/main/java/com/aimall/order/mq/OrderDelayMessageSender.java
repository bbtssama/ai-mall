package com.aimall.order.mq;

import com.aimall.common.tx.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 订单延迟消息发送器 —— 「30 分钟未支付自动取消」消息的唯一出口。
 *
 * <h2>为什么抽成组件（P0-3 修复的产物）</h2>
 * 早期只有普通下单（OrderServiceImpl）发这条消息；后来限量发售（Drop）也建订单，
 * 却<b>忘了发</b>——抢购单永不超时取消，限量库存被死单永久占用。
 * 根因是"建单必发延迟消息"这个约束散落在各处靠人记。抽成唯一出口后：
 * 任何建单路径（普通下单/秒杀下单/将来的拼团）都调 {@link #sendAfterCommit}，
 * 忘了发消息 = 忘了调这个方法，一眼可见。
 *
 * <h2>两个可靠性要点</h2>
 * <ul>
 *   <li><b>afterCommit</b>：事务提交后才发。事务内直发的问题是——事务回滚但消息已出
 *       （MQ 不参与 DB 事务），消费者 30 分钟后查到一个"幽灵订单"。
 *       消费端有 null/状态防御所以后果轻，但这仍是反模式。</li>
 *   <li><b>MQ 不可用降级</b>：超时取消属"尽力而为"功能，发送失败只记日志不炸主流程；
 *       漏掉的单由定时补偿任务兜底（见 scheduled 补偿任务）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayMessageSender {

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    /**
     * 在<b>当前事务提交后</b>投递延迟取消消息。
     * 必须在事务内调用（无事务时退化为立即发送并记 WARN）。
     *
     * <p>实现委托给 {@link AfterCommitExecutor}：事务边界处理、无事务退化、
     * commit 后异常吞咽这套逻辑全项目只应有一份，避免各处各写一遍又写漏。</p>
     */
    public void sendAfterCommit(Long orderId) {
        AfterCommitExecutor.run("订单延迟取消消息 orderId=" + orderId, () -> sendNow(orderId));
    }

    /** 直接投递（MQ 不可用时静默降级：不投递，由补偿任务兜底） */
    public void sendNow(Long orderId) {
        RabbitTemplate rabbit = rabbitTemplateProvider.getIfAvailable();
        if (rabbit == null) {
            return;
        }
        try {
            rabbit.convertAndSend(OrderDelayMqConfig.DELAY_QUEUE, orderId);
        } catch (AmqpException e) {
            log.warn("延迟消息投递失败（不影响业务，补偿任务兜底）orderId={}: {}", orderId, e.getMessage());
        }
    }
}
