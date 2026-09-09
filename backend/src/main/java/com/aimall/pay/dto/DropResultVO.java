package com.aimall.pay.dto;

import lombok.Data;

/**
 * 抢购结果（前端轮询接口的响应）。
 *
 * <p>状态机：QUEUED（已受理，建单中）→ SUCCESS（拿到订单）/ FAILED（建单失败，可重试）；
 * NONE 表示当前用户在该活动下没有进行中的抢购。</p>
 */
@Data
public class DropResultVO {

    /** QUEUED / SUCCESS / FAILED / NONE */
    private String status;

    /** 仅 SUCCESS 时有值：建好的订单 id（前端跳订单页用） */
    private Long orderId;

    public static DropResultVO success(Long orderId) {
        DropResultVO vo = new DropResultVO();
        vo.setStatus("SUCCESS");
        vo.setOrderId(orderId);
        return vo;
    }

    public static DropResultVO queued() {
        DropResultVO vo = new DropResultVO();
        vo.setStatus("QUEUED");
        return vo;
    }

    public static DropResultVO failed() {
        DropResultVO vo = new DropResultVO();
        vo.setStatus("FAILED");
        return vo;
    }

    public static DropResultVO none() {
        DropResultVO vo = new DropResultVO();
        vo.setStatus("NONE");
        return vo;
    }
}
