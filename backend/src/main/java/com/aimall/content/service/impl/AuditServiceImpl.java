package com.aimall.content.service.impl;

import com.aimall.ai.rag.RagIndexService;
import com.aimall.common.tx.AfterCommitExecutor;
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
 *     └─ ③ AI 调用失败 ────► 记 ERROR 流水，笔记停留 AUDITING，不误杀内容（由补偿任务重试）
 * </pre>
 *
 * <h2>★ 提交时机：为什么必须 afterCommit（2026-09-20 修复的真实缺陷）</h2>
 * 早期版本在这里直接 {@code rabbit.convertAndSend}，而调用方 {@code NoteServiceImpl.create()}
 * 带 {@code @Transactional} —— <b>消息在事务提交前就发出去了</b>。消费端用另一条连接回查，
 * 读不到未提交的行，于是判定"笔记不存在"直接 ACK 丢弃，笔记卡在 AUDITING。
 * 日志实据：10 次发布有 9 次被丢弃，只有第一次（消费端建连接慢了 65ms）侥幸成功。
 *
 * <p>现在统一走 {@link AfterCommitExecutor}：消息在 commit 之后才投递，
 * 消费端一定能查到数据。</p>
 *
 * <h2>★ 幂等键：版本号必须真的递增（2026-09-20 修复的真实缺陷）</h2>
 * 幂等键是 {@code t_audit_record} 的 {@code uk(biz_type, biz_id, biz_version, status)}。
 * 它要拦住的是<b>同一次审核请求的 MQ 重复投递</b>，而不是"这篇内容一生只能审一次"。
 *
 * <p>早期版本把 {@code biz_version} 硬编码为 1，且项目没有编辑接口去递增，
 * 于是幂等键退化成常量，后果是：</p>
 * <ul>
 *   <li>被驳回的笔记「重新送审」永远失败——第一次 REJECT 已占键，
 *       重提时 INSERT IGNORE 返回 0 → 提前 return → 状态永停 AUDITING；</li>
 *   <li>AI 首次报错写入的 ERROR 流水同样占键，永久堵死后续补审。</li>
 * </ul>
 * <p>现在版本号来自 {@code t_note.audit_version}（发布=1，每次重新送审 +1），
 * 并随消息传递；唯一键纳入 {@code status}，使 ERROR 不再占用终态幂等位（见 V5 迁移）。</p>
 *
 * <h2>两个设计说明（排查启动问题时的复盘，面试可讲）</h2>
 * <ol>
 *   <li><b>不注入 NoteService</b>：审核只依赖 NoteMapper 回查数据。
 *       若注入 NoteService 会形成 AuditServiceImpl ↔ NoteServiceImpl 的构造器循环依赖，
 *       Spring Boot 3 默认禁止（allow-circular-references=false）直接启动失败。</li>
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
     *
     * <p><b>关键：投递动作注册到「当前事务提交后」执行</b>，不在事务内直发。
     * 否则消费端可能比事务提交先跑，回查不到笔记而丢弃消息。</p>
     */
    @Override
    public void submit(Note note) {
        Long noteId = note.getId();
        int version = versionOf(note);

        // ★ 事务提交后再投递：交给全项目唯一的 afterCommit 出口
        AfterCommitExecutor.run("审核消息 noteId=" + noteId, () -> doSubmit(noteId, version));
    }

    /**
     * 真正的投递动作（已处于事务提交后）：优先 MQ，MQ 不可用降级线程池。
     * 该方法只负责"发出去"，不做任何与事务相关的事。
     */
    private void doSubmit(Long noteId, int version) {
        AuditMessage msg = new AuditMessage(noteId, version);
        RabbitTemplate rabbit = rabbitTemplateProvider.getIfAvailable();
        if (rabbit != null) {
            try {
                rabbit.convertAndSend(AuditMqConfig.EXCHANGE,
                        AuditMqConfig.AUDIT_ROUTING_KEY, msg);
                log.info("审核消息已入队 noteId={} version={}", noteId, version);
                return;
            } catch (AmqpException e) {
                // MQ 不可用（未启动/断连）：不炸发布接口，降级本地线程池
                log.warn("MQ 不可用，审核降级为本地线程池异步: {}", e.getMessage());
            }
        }
        submitLocal(noteId, version);
    }

    /**
     * 本地降级路径：手动提交到 AsyncConfig 的线程池（有界队列+CallerRuns，不会 OOM）。
     * 不用 @Async 的原因见类注释第 2 条（自调用不走代理，注解失效）。
     */
    private void submitLocal(Long noteId, int version) {
        taskExecutor.execute(() -> {
            try {
                Note note = noteMapper.selectById(noteId);
                if (note == null) {
                    // 早期版本这里没有 else 分支 —— 查不到就静默什么都不做、连日志都没有，
                    // 问题排查时完全看不到痕迹。现在至少留一条 WARN。
                    log.warn("本地异步审核跳过：笔记已不存在 noteId={}", noteId);
                    return;
                }
                auditOnce(note, version);
            } catch (Exception e) {
                log.error("本地异步审核失败 noteId={}: {}", noteId, e.getMessage(), e);
            }
        });
    }

    /**
     * 执行一次审核（MQ 消费者与本地降级路径共用）。
     *
     * <p>幂等保障三层：</p>
     * <ol>
     *   <li>t_audit_record 的 {@code uk(biz_type,biz_id,biz_version,status)}——
     *       终态流水（PASS/REJECT）撞键返回 0 直接跳过。注意 status 也在键里：
     *       ERROR 不算结论，不会占用终态幂等位。</li>
     *   <li>t_note.updateStatus 带 {@code WHERE status='AUDITING'}——重复回写自然失效；</li>
     *   <li>审核失败(ERROR)不改动笔记状态——笔记停在 AUDITING，由补偿任务重试，不误杀内容。</li>
     * </ol>
     *
     * @param version 本次审核的版本号（来自消息 / 降级路径传入），不可硬编码
     */
    @Override
    public void auditOnce(Note note, int version) {
        String input = note.getTitle() + "\n" + note.getContent();

        AuditAiClient.AuditVerdict verdict;
        try {
            verdict = auditAiClient.review(input);
        } catch (Exception e) {
            // AI 挂了：记 ERROR 流水，笔记停留在 AUDITING 等补偿重试——绝不误杀内容。
            // ERROR 与终态结论虽然同版本，但 status 不同，不会占用终态幂等位（V5 迁移的修复），
            // 所以后续补审能正常落库；同一版本重复报错则被唯一键挡住，不会堆积重复错误行。
            log.error("AI 审核调用失败 noteId={} version={}: {}", note.getId(), version, e.getMessage());
            // 仅在笔记仍处于 AUDITING 时留痕：若它已被别的判定流转掉，
            // 这条迟到的失败对业务已无意义，写进去只会污染审核轨迹
            //（selectLatestByBiz 可能取到这条 ERROR，把"审核失败"误当成审核结论展示）。
            if (Note.STATUS_AUDITING.equals(note.getStatus())) {
                saveRecord(note.getId(), version, AuditRecord.STATUS_ERROR,
                        Map.of("error", String.valueOf(e.getMessage())));
            }
            return;
        }

        // 幂等第一道防线：INSERT IGNORE 撞 uk(biz,biz_id,biz_version,status) 返回 0
        // → 该版本、该结论已经落过流水，属于重复投递，直接返回
        int rows = saveRecord(note.getId(), version,
                verdict.pass() ? AuditRecord.STATUS_PASS : AuditRecord.STATUS_REJECT,
                Map.of("pass", verdict.pass(),
                        "reason", verdict.reason(),
                        "categories", verdict.categories()));
        if (rows == 0) {
            log.info("重复审核消息（幂等跳过）noteId={} version={}", note.getId(), version);
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
        log.info("审核完成 noteId={} version={} pass={} status→{}",
                note.getId(), version, verdict.pass(), newStatus);
    }

    @Override
    public AuditRecord latestRecord(String bizType, Long bizId) {
        // 取"最新一次审核的流水"：版本号大的优先，同版本按写入顺序取最后一条
        // （同一版本最多两条：一条 ERROR + 一条终态结论，终态才是要展示的）
        return auditRecordMapper.selectLatestByBiz(bizType, bizId);
    }

    /** 版本号兜底：理论上调用方都会设置，null 时按第 1 次审核处理 */
    private int versionOf(Note note) {
        return note.getAuditVersion() == null ? 1 : note.getAuditVersion();
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
