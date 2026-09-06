package com.aimall.content.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑：内容审核的队列/交换机/绑定声明 + JSON 消息转换器。
 *
 * <pre>
 *   publish(发布笔记)
 *      │ routingKey = note.audit
 *      ▼
 *   [aimall.content.exchange]  ──►  [aimall.note.audit.queue] ──► 消费者调 AI 审核
 *                                        │
 *                                        ├── durable      持久化队列：Broker 重启消息不丢
 *                                        └── dead-letter   审核失败进死信队列（人工兜底）
 * </pre>
 *
 * <h2>★ 为什么用 DirectExchange 而不是 Fanout/Topic</h2>
 * 当前只有一种消息（笔记审核），Direct（精确匹配 routingKey）最简单直白。
 * Topic 留给将来有"note.* / order.#"这类通配需求的场景——<b>按需演进，不预支复杂度</b>。
 *
 * <h2>★ 为什么配 Jackson2JsonMessageConverter（踩坑记录）</h2>
 * Spring AMQP 默认用 SimpleMessageConverter，只支持 String/byte[]/<b>Serializable</b>。
 * 审核消息是 record（record 不自动实现 Serializable）→ 发送时直接抛 MessageConversionException，
 * 且被 submit 的 catch(AmqpException) 静默吞掉 —— <b>表现是"MQ 永远不生效但不报错"</b>，很难排查。
 * 改用 JSON 转换器：消息体可读、跨语言、无 Java 反序列化的安全风险（生产标准做法）。
 * Boot 自动把这个 bean 应用到 RabbitTemplate 和监听容器工厂，收发两侧同时生效。
 *
 * <h2>死信队列（DLX）的作用</h2>
 * 消费端重试 N 次仍失败（如 AI 服务长时间不可用），消息被丢进死信队列而不是丢弃——
 * "不丢消息"是 MQ 可靠性的底线：失败的任务必须能被人工发现和处理。
 */
@Configuration
public class AuditMqConfig {

    /** 业务交换机 */
    public static final String EXCHANGE = "aimall.content.exchange";
    /** 审核队列 */
    public static final String AUDIT_QUEUE = "aimall.note.audit.queue";
    /** 审核路由键 */
    public static final String AUDIT_ROUTING_KEY = "note.audit";
    /** 审核死信队列（重试耗尽后进入，等人工/恢复后处理） */
    public static final String AUDIT_DLQ = "aimall.note.audit.dlq";

    @Bean
    public DirectExchange contentExchange() {
        // durable=true：Broker 重启后交换机定义不丢
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                // 死信路由：消费端 reject/nack 或重试耗尽 → 消息转入 DLQ
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(AUDIT_DLQ)
                .build();
    }

    @Bean
    public Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(AUDIT_DLQ).build();
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue())
                .to(contentExchange())
                .with(AUDIT_ROUTING_KEY);
    }

    @Bean
    public Binding auditDlqBinding() {
        return BindingBuilder.bind(auditDeadLetterQueue())
                .to(contentExchange())
                .with(AUDIT_DLQ);
    }

    /**
     * JSON 消息转换器：收发两侧共用（Boot 自动装配到 RabbitTemplate 与监听容器工厂）。
     *
     * <p>typeMapper 默认只信任 java.util/java.lang 包的反序列化类型，
     * 自定义消息类（com.aimall.**）会被拒——放开为 "*"（内网 Broker、消息体自产自销的场景可接受）。</p>
     */
    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
