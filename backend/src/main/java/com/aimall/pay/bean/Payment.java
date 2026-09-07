package com.aimall.pay.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单（t_payment）
 *
 * <h2>为什么要独立的支付单，而不是在订单上加个"已支付"标记</h2>
 * <ol>
 *   <li><b>幂等粒度</b>：一次订单可能多次发起支付（第一次支付超时后重新支付），
 *       支付单号才是一次支付的唯一标识，回调按 payment_no 幂等。</li>
 *   <li><b>对账需要</b>：第三方交易号、回调原始报文都要留痕，塞进订单表会让订单表变臃肿。</li>
 *   <li><b>状态解耦</b>：订单有订单状态机（待支付/已支付/已发货/已完成），
 *       支付有自己的状态（待支付/已支付/已关闭）——两者生命周期不同。</li>
 * </ol>
 */
@Data
public class Payment {

    /** 待支付：已创建支付单，等用户付款 */
    public static final String STATUS_PAYING = "PAYING";
    /** 已支付：回调验签通过并完成状态流转 */
    public static final String STATUS_PAID = "PAID";
    /** 已关闭：超时未支付 / 用户取消 / 订单取消 */
    public static final String STATUS_CLOSED = "CLOSED";
    /** 支付失败：第三方明确返回失败 */
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    /** 支付单号：业务唯一，幂等核心 */
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    /** 渠道：MOCK / ALIPAY / WECHAT */
    private String channel;
    private String status;
    /** 第三方交易号（回调带来） */
    private String thirdTradeNo;
    /** 回调原始报文（留痕，便于排查与补单） */
    private String callbackBody;
    private LocalDateTime paidTime;
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
