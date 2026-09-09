package com.aimall.pay.dto;

import lombok.Data;

/**
 * 抢购提交的响应：只表达"已受理"，订单号要等消费者建完单才有（前端轮询 result 接口）。
 */
@Data
public class DropBuyVO {

    /** 受理状态固定 QUEUED：请求已通过 Redis 预减闸门并进入建单队列 */
    private String status = "QUEUED";

    /** 提示语：异步受理语义（区别于同步下单的直接返回订单号） */
    private String message = "抢购请求已受理，请稍后查看结果";

    public static DropBuyVO queued() {
        return new DropBuyVO();
    }
}
