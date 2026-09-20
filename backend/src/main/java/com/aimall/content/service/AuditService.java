package com.aimall.content.service;

import com.aimall.content.bean.AuditRecord;
import com.aimall.content.bean.Note;

/**
 * 内容审核服务 —— V2 的「AI + MQ」交汇点。
 *
 * <h2>两条路径</h2>
 * <pre>
 *   发布笔记
 *     ├─ MQ 可用：发送审核消息 ──► 消费者异步审核 ──► 回写笔记状态（用户零等待）
 *     └─ MQ 不可用（未启动/断连）：降级为「线程池异步审核」——体验一致，只是少了削峰/重试能力
 * </pre>
 *
 * <h2>为什么审核结果不能只信 AI 一次调用</h2>
 * AI 判定是概率性的：网络抖动、模型抽风、超时都可能发生。
 * 所以结果落两条：<b>审核流水</b>（t_audit_record 留痕，可追溯/复核/幂等）+
 * <b>笔记状态</b>（带状态机条件更新，重复消费不会把"已发布"改回"驳回"）。
 */
public interface AuditService {

    /**
     * 提交审核：优先走 MQ，不可用时降级为本地线程池异步执行。
     * <b>无论哪条路径，本方法都应快速返回</b>（发布接口不等待 AI）。
     *
     * <p><b>投递时机</b>：消息<b>不在事务内直发</b>，而是注册到「当前事务提交后」执行
     * （见 {@code AfterCommitExecutor}）。否则消费端可能先于事务提交回查数据库，
     * 读不到刚插入的笔记而丢弃消息。</p>
     */
    void submit(Note note);

    /**
     * 执行一次 AI 审核（MQ 消费者与降级路径共同调用）。
     *
     * <p>幂等保障三层：</p>
     * <ol>
     *   <li>t_audit_record 的 {@code uk(biz_type,biz_id,biz_version,status)}——INSERT IGNORE 撞键返回 0 直接跳过；
     *       键里含 status，因此 ERROR（审核没得出结论）不会占用终态幂等位；</li>
     *   <li>t_note.updateStatus 带 WHERE status='AUDITING'——重复回写自然失效；</li>
     *   <li>审核失败(ERROR)不改动笔记状态——笔记停在 AUDITING，由补偿任务重试，人工兜底。</li>
     * </ol>
     *
     * @param version 本次审核的版本号（来自 {@code t_note.audit_version}，随消息传递）。
     *                <b>不可硬编码</b>：它决定了「MQ 重复投递」与「用户改完后的新一次审核」
     *                能否被幂等键正确区分。
     */
    void auditOnce(Note note, int version);

    /**
     * 查询某次审核的流水（详情页展示"为什么被驳回"用）。
     */
    AuditRecord latestRecord(String bizType, Long bizId);
}
