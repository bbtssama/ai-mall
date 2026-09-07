package com.aimall.pay.dto;

/**
 * 回调处理结果。
 *
 * @param success   是否处理成功（决定是否给第三方回 success）
 * @param paymentNo 支付单号
 * @param message   返回给第三方的报文内容
 */
public record CallbackResult(boolean success, String paymentNo, String message) {
}
