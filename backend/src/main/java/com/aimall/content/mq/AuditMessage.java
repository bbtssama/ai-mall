package com.aimall.content.mq;

/**
 * 审核消息体（笔记 id + 版本号）。
 *
 * <p>★ 只带 id 不带内容：消息是"通知"不是"数据载体"。
 * 消费时按 id 回查最新内容——即使消息在队列里躺了很久、笔记被编辑过，
 * 审核的也永远是最新版本。且消息体小、不占 Broker 内存。</p>
 *
 * @param noteId     笔记 id
 * @param bizVersion 业务版本号（编辑后 +1，配合 t_audit_record 唯一键做幂等）
 */
public record AuditMessage(Long noteId, int bizVersion) {
}
