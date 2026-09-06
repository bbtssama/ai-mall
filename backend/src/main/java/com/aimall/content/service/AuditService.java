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
     */
    void submit(Note note);

    /**
     * 执行一次 AI 审核（MQ 消费者与降级路径共同调用）。
     *
     * <p>幂等保障三层：</p>
     * <ol>
     *   <li>t_audit_record 的 uk(biz_type,biz_id,biz_version)——INSERT IGNORE 撞键返回 0 直接跳过；</li>
     *   <li>t_note.updateStatus 带 WHERE status='AUDITING'——重复回写自然失效；</li>
     *   <li>审核失败(ERROR)不改动笔记状态——笔记停在 AUDITING，人工兜底。</li>
     * </ol>
     */
    void auditOnce(Note note);

    /**
     * 查询某次审核的流水（详情页展示"为什么被驳回"用）。
     */
    AuditRecord latestRecord(String bizType, Long bizId);
}
