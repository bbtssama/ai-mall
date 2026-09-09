package com.aimall.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitTemplate 生产者侧可靠性回调 —— 让"配置写了"真正等于"功能生效"。
 *
 * <h2>★ 背景（P1 修复）：publisher-confirm 配了但没人消费</h2>
 * application.yml 早已开了 {@code publisher-confirm-type: correlated} 和
 * {@code publisher-returns: true}，但从不注册回调——Broker 的确认/退回
 * 事件到达后<b>被静默丢弃</b>，等于两个开关都是摆设。这是"配置写了没生效"
 * 的典型：开启收集≠使用结果。
 *
 * <h2>两个回调分别管什么（面试易混）</h2>
 * <ul>
 *   <li><b>ConfirmCallback（确认）</b>：消息<b>有没有到 Broker</b>。
 *       nack = Broker 没收到（内部错误/队列满了）——记录 ERROR 并可补偿重发。</li>
 *   <li><b>ReturnsCallback（退回）</b>：消息到了 Broker 但<b>路由不到任何队列</b>
 *       （routing key 写错/队列不存在）。mandatory=true 时 Broker 不静默丢弃，
 *       而是退回给生产者——这是"消息发出去了却谁也没消费"的隐形丢失防线。</li>
 * </ul>
 *
 * <p>为什么不在这里自动重发：本项目消息均为"尽力而为+补偿任务兜底"语义，
 * 自动重发要配幂等与退避，复杂度不划算；先 ERROR 落日志（带 traceId），
 * 由补偿任务周期性收敛。若某条链路升级为"必达"（如资金类），再在
 * ConfirmCallback 里做"nack → 重投/告警"。</p>
 */
@Slf4j
@Configuration
public class MqProducerConfig {

    @Bean
    public RabbitTemplateCustomizer reliableRabbitTemplate() {
        return template -> {
            // mandatory：路由不到队列时退回（配合 publisher-returns: true），而非静默丢
            template.setMandatory(true);

            // Broker 确认：ack=false 即消息未到达 Broker
            template.setConfirmCallback((correlationData, ack, cause) -> {
                if (!ack) {
                    log.error("MQ 消息未到达 Broker（nack）cause={} correlationData={}",
                            cause, correlationData);
                }
            });

            // 路由失败退回：到得了 Broker 但找不到队列
            template.setReturnsCallback(returned ->
                    log.error("MQ 消息路由失败被退回 exchange={} routingKey={} replyText={} message={}",
                            returned.getExchange(), returned.getRoutingKey(),
                            returned.getReplyText(),
                            returned.getMessage() != null
                                    ? new String(returned.getMessage().getBody())
                                    : null));
        };
    }
}
