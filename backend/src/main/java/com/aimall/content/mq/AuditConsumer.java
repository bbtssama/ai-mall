package com.aimall.content.mq;

import com.aimall.content.bean.Note;
import com.aimall.content.mapper.NoteMapper;
import com.aimall.content.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 审核消息消费者 —— MQ 异步审核的执行端。
 *
 * <h2>ACK 策略（消息可靠性的"消费端三件套"）</h2>
 * <ul>
 *   <li><b>手动 ACK 场景</b>：Spring AMQP 默认"方法正常返回即 ACK、抛异常即重入队"，
 *       这会带来"毒消息无限循环"的风险（一条必然失败的消息永远重试）。
 *       本项目的取舍：<b>审核链路不在消费端抛异常重试</b>——失败已作为 ERROR 落流水、
 *       笔记停留 AUDITING 等人工，消息正常 ACK。因为"重试也大概率失败"（AI 服务挂了），
 *       循环重试只会打爆日志。真正的兜底是死信队列 + 定时补偿任务（V3）。</li>
 *   <li><b>幂等</b>：见 AuditServiceImpl.auditOnce 的三层防线。</li>
 *   <li><b>不丢</b>：队列/消息均持久化（AuditMqConfig durable + publisher 端确认见配置）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditConsumer {

    private final NoteMapper noteMapper;
    private final AuditService auditService;

    @RabbitListener(queues = AuditMqConfig.AUDIT_QUEUE)
    public void onAuditMessage(AuditMessage msg,
                               @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        log.info("收到审核消息 noteId={} version={}", msg.noteId(), msg.bizVersion());
        try {
            // 消息只带 id：消费时回查最新内容（消息在队列里期间笔记可能已被编辑）
            Note note = noteMapper.selectById(msg.noteId());
            if (note == null) {
                log.warn("笔记不存在，丢弃消息 noteId={}", msg.noteId());
                return;   // 正常 ACK：不存在的笔记重试一万次也不存在
            }
            // 幂等防线在 auditOnce 内部（版本唯一键 + 状态机条件更新）
            auditService.auditOnce(note);
        } catch (Exception e) {
            // 吞掉异常 = 消息 ACK 不重入队（防毒消息循环）。
            // 失败已有 ERROR 流水兜底，可在管理后台/定时任务补处理。
            log.error("审核消费失败（已记流水，消息 ACK 不重试）noteId={}: {}",
                    msg.noteId(), e.getMessage(), e);
        }
    }
}
