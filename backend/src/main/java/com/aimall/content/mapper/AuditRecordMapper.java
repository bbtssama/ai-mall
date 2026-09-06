package com.aimall.content.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.content.bean.AuditRecord;
import org.apache.ibatis.annotations.Param;

/**
 * 审核流水 Mapper（SQL 见 resources/mapper/AuditRecordMapper.xml）
 *
 * <p>{@code insert} 用 <b>INSERT IGNORE</b>：
 * 撞上 {@code uk(biz_type, biz_id, biz_version)} 时静默跳过——
 * 这是消费端幂等的第一道防线：MQ 重复投递时，第二次插入直接被数据库挡住，
 * 返回影响行数 0，Service 据此判断"已处理过"直接返回。</p>
 */
@Mapper
public interface AuditRecordMapper {

    /** INSERT IGNORE：重复审核（同一版本）返回 0 行 */
    int insertIgnore(AuditRecord record);

    AuditRecord selectByBiz(@Param("bizType") String bizType,
                            @Param("bizId") Long bizId,
                            @Param("bizVersion") Integer bizVersion);
}
