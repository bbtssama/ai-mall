package com.aimall.content.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.content.bean.AuditRecord;
import org.apache.ibatis.annotations.Param;

/**
 * 审核流水 Mapper（SQL 见 resources/mapper/AuditRecordMapper.xml）
 *
 * <p>{@code insertIgnore} 用 <b>INSERT IGNORE</b>：
 * 撞上 {@code uk(biz_type, biz_id, biz_version, status)} 时静默跳过——
 * 这是消费端幂等的第一道防线：MQ 重复投递时，第二次插入直接被数据库挡住，
 * 返回影响行数 0，Service 据此判断"已处理过"直接返回。</p>
 *
 * <p>★ 唯一键为什么含 {@code status}：ERROR（AI 调用失败，没得出结论）与
 * PASS/REJECT（终态结论）必须落在不同的幂等位上。否则一次基础设施故障
 * 会永久占掉这条内容的重新审核机会（见 V5 迁移注释）。</p>
 */
@Mapper
public interface AuditRecordMapper {

    /** INSERT IGNORE：同一 (业务, id, 版本, 结论) 重复落库返回 0 行 */
    int insertIgnore(AuditRecord record);

    AuditRecord selectByBiz(@Param("bizType") String bizType,
                            @Param("bizId") Long bizId,
                            @Param("bizVersion") Integer bizVersion);

    /**
     * 取某业务"最新一次审核"的流水（详情页展示"为什么被驳回"用）。
     *
     * <p>不能用 {@code selectByBiz(..., 1)} 硬编码版本 1 —— 重新送审后版本会递增，
     * 硬编码会取到过期的旧流水。且同一版本最多两条（一条 ERROR + 一条终态），
     * 终态结论才是要展示的，故按 id 倒序取最后一条。</p>
     */
    AuditRecord selectLatestByBiz(@Param("bizType") String bizType,
                                  @Param("bizId") Long bizId);
}
