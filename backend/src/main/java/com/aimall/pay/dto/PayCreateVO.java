package com.aimall.pay.dto;

import lombok.Data;

/**
 * 发起支付的结果：前端据此跳收银台。
 */
@Data
public class PayCreateVO {
    private String paymentNo;
    /** 收银台地址（真实渠道=支付宝页面；模拟渠道=本地模拟页） */
    private String cashierUrl;
    private String channel;
    /** 支付单过期时间（毫秒时间戳，前端做倒计时） */
    private Long expireAt;
}
