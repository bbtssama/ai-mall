package com.aimall.pay.mapper;

import com.aimall.pay.bean.DropActivity;
import com.aimall.pay.bean.DropRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 限量发售 Mapper（SQL 见 resources/mapper/DropMapper.xml）
 */
@Mapper
public interface DropMapper {

    DropActivity selectActivity(@Param("id") Long id);

    /** 进行中的活动（start<=now<=end） */
    List<DropActivity> selectOngoing(@Param("now") LocalDateTime now);

    int insertActivity(DropActivity activity);

    /** 更新活动状态（预热/开售/结束），条件更新防并发流转 */
    int updateStatus(@Param("id") Long id,
                     @Param("expectStatus") String expectStatus,
                     @Param("newStatus") String newStatus);

    /** 发售成功记录（uk_activity_user 幂等，重复插入抛 DuplicateKey） */
    int insertRecord(DropRecord record);

    DropRecord selectRecord(@Param("activityId") Long activityId, @Param("userId") Long userId);
}
