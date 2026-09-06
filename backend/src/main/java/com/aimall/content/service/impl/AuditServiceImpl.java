package com.aimall.content.service.impl;

import com.aimall.ai.rag.RagIndexService;
import com.aimall.content.bean.AuditRecord;
import com.aimall.content.bean.KnowledgeDoc;
import com.aimall.content.bean.Note;
import com.aimall.content.mapper.AuditRecordMapper;
import com.aimall.content.mapper.NoteMapper;
import com.aimall.content.mq.AuditMessage;
import com.aimall.content.mq.AuditMqConfig;
import com.aimall.content.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审核服务实现 —— 「发布秒回 + 异步审核」的编排中心。
 *
 * <h2>三重降级链（本项目"可插拔"原则的又一实践）</h2>
 * <pre>
 *   submit(笔记)
 *     ├─ ① RabbitMQ 可用 ──► 发消息（note.audit）──► 消费者 auditOnce()
 *     ├─ ② MQ 不可用 ──────► 手动提交 AsyncConfig 线程池执行 auditOnce()（体验一致，无削峰/重试）
 *     └─ ③ AI 调用失败 ────► 记 ERROR 流水，笔记停留 AUDITING，不误杀内容
 * </pre>
 *
 * <h2>两个设计说明（排查启动问题时的复盘，面试可讲）</h2>
 * <ol>
 *   <li><b>不注入 NoteService</b>：审核只依赖 NoteMapper 回查数据。
 *       若注入 NoteService 会形成 AuditServiceImpl ↔ NoteServiceImpl 的构造器循环依赖，
 *       Spring Boot 3 默认禁止（allow-circular-references=false）直接启动失败。
 *       且本类根本用不到 NoteService 的业务方法——那是当初多余的依赖，已删除。</li>
 *   <li><b>降级路径手动提交线程池而非 @Async</b>：@Async 靠 AOP 代理实现，
 *       <b>类内自调用（this.auditAsync()）不经过代理 → 注解失效变同步执行</b>
 *       （与 @Transactional 自调用失效同源）。手动 executor.execute() 语义明确无此坑。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final NoteMapper noteMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final ObjectMapper objectMapper;

    /** AI 审核执行器（只依赖"文本→判定"这个纯函数式接口，测试时可 mock） */
    private final AuditAiClient auditAiClient;

    /** RAG 索引：审核通过的笔记要进知识库（UGC 语料） */
    private final RagIndexService ragIndexService;

    /** 本地降级路径用的线程池（AsyncConfig 的 taskExecutor，bean 名唯一，按类型注入无歧义） */
    private final ThreadPoolTaskExecutor taskExecutor;

    /**
     * 提交审核：优先 MQ，降级本地线程池。
     */
    @Override
    public void submit(Note note) {
        AuditMessage msg = new AuditMessage(note.getId(), 1);
        RabbitTemplate rabbit = rabbitTemplateProvider.getIfAvailable();
        if (rabbit != null) {
            try {
                rabbit.convertAndSend(AuditMqConfig.EXCHANGE,
                        AuditMqConfig.AUDIT_ROUTING_KEY, msg);
                log.info("审核消息已入队 noteId={}", note.getId());
                return;
            } catch (AmqpException e) {
                // MQ 不可用（未启动/断连）：不炸发布接口，降级本地线程池
                log.warn("MQ 不可用，审核降级为本地线程池异步: {}", e.getMessage());
            }
        }
        submitLocal(note.getId());
    }

    /**
     * 本地降级路径：手动提交到 AsyncConfig 的线程池（有界队列+CallerRuns，不会 OOM）。
     * 不用 @Async 的原因见类注释第 2 条（自调用不走代理，注解失效）。
     */
    private void submitLocal(Long noteId) {
        taskExecutor.execute(() -> {
            try {
                Note note = noteMapper.selectById(noteId);
                if (note != null) {
                    auditOnce(note);
                }
            } catch (Exception e) {
                log.error("本地异步审核失败 noteId={}: {}", noteId, e.getMessage(), e);
            }
        });
    }

    /**
     * 执行一次审核（MQ 消费者与本地降级共用）。
     * 幂等三层防线见 AuditService 接口注释。
     */
    @Override
    public void auditOnce(Note note) {
        String input = note.getTitle() + "\n" + note.getContent();

        AuditAiClient.AuditVerdict verdict;
        try {
            verdict = auditAiClient.review(input);
        } catch (Exception e) {
            // AI 挂了：记 ERROR 流水，笔记停留在 AUDITING 等人工——绝不误杀内容
            log.error("AI 审核调用失败 noteId={}: {}", note.getId(), e.getMessage());
            saveRecord(note.getId(), 1, AuditRecord.STATUS_ERROR,
                    Map.of("error", String.valueOf(e.getMessage())));
            return;
        }

        // 幂等第一道防线：INSERT IGNORE 撞 uk(biz,biz,version) 返回 0 → 已处理过，直接返回
        int rows = saveRecord(note.getId(), 1,
                verdict.pass() ? AuditRecord.STATUS_PASS : AuditRecord.STATUS_REJECT,
                Map.of("pass", verdict.pass(),
                        "reason", verdict.reason(),
                        "categories", verdict.categories()));
        if (rows == 0) {
            log.info("重复审核消息（幂等跳过）noteId={}", note.getId());
            return;
        }

        // 幂等第二道防线：状态机条件更新（WHERE status='AUDITING'）
        // 重试/重投时笔记可能已流转，返回 0 行属正常
        String newStatus = verdict.pass() ? Note.STATUS_PUBLISHED : Note.STATUS_REJECTED;
        int updated = noteMapper.updateStatus(note.getId(),
                Note.STATUS_AUDITING, newStatus, verdict.reason());

        if (updated > 0 && verdict.pass()) {
            // 审核通过 → 入 RAG 索引（UGC 语料），失败不影响发布（索引可补建）
            try {
                ragIndexService.index(KnowledgeDoc.SOURCE_NOTE,
                        note.getId(), note.getTitle(), note.getContent());
            } catch (Exception e) {
                log.warn("笔记入 RAG 索引失败（不影响发布）noteId={}: {}", note.getId(), e.getMessage());
            }
        }
        log.info("审核完成 noteId={} pass={} status→{}",
                note.getId(), verdict.pass(), newStatus);
    }

    @Override
    public AuditRecord latestRecord(String bizType, Long bizId) {
        return auditRecordMapper.selectByBiz(bizType, bizId, 1);
    }

    private int saveRecord(Long noteId, int version, String status, Map<String, Object> result) {
        AuditRecord r = new AuditRecord();
        r.setBizType(AuditRecord.BIZ_NOTE);
        r.setBizId(noteId);
        r.setBizVersion(version);
        r.setStatus(status);
        try {
            r.setAiResultJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            r.setAiResultJson("{\"error\":\"serialize-failed\"}");
        }
        return auditRecordMapper.insertIgnore(r);
    }
}
