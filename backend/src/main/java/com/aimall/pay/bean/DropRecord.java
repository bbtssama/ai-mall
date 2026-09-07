package com.aimall.pay.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 限量发售成功记录（t_drop_record）—— uk(activity_id, user_id) 保证一人一单。
 */
@Data
public class DropRecord {

    private Long id;
    private Long activityId;
    private Long userId;
    private Long orderId;
    private Integer quantity;
    private LocalDateTime createdAt;
}
