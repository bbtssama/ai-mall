package com.aimall.content.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容审核流水（t_audit_record）
 *
 * <h2>为什么单独建表而不是只在 t_note 上打个状态</h2>
 * <ol>
 *   <li><b>可追溯</b>：AI 判"驳回"得有依据。原始返回存 ai_result_json，
 *       用户申诉时能把当时的判定理由翻出来。</li>
 *   <li><b>可复核</b>：留出 reviewer/reviewed_at 字段给人工复核（V3）。</li>
 *   <li><b>幂等</b>：uk(biz_type, biz_id, biz_version) 保证同一版本只审一次——
 *       MQ 重复投递、消费端重试都不会重复处理（消息可靠性的"不重"一半靠它）。</li>
 * </ol>
 *
 * <p>AI 直写业务表有什么问题？AI 判定是<b>概率性</b>的，可能出错、可能被重试。
 * 留痕 = 出问题时能回滚、能复盘、能统计误判率。</p>
 */
@Data
public class AuditRecord {

    public static final String BIZ_NOTE = "NOTE";

    /** AI 判定通过 */
    public static final String STATUS_PASS = "PASS";
    /** AI 判定驳回（违规） */
    public static final String STATUS_REJECT = "REJECT";
    /** 审核过程失败（如 AI 服务不可用）——走人工兜底，不能直接删内容 */
    public static final String STATUS_ERROR = "ERROR";

    private Long id;
    private String bizType;
    private Long bizId;
    private String aiResultJson;
    private String status;
    private Integer bizVersion;
    private String reviewer;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
