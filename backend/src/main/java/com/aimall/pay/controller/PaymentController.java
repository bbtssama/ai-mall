package com.aimall.pay.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.R;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.pay.bean.Payment;
import com.aimall.pay.channel.MockPayChannel;
import com.aimall.pay.dto.CallbackResult;
import com.aimall.pay.dto.PayCreateVO;
import com.aimall.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付接口。
 *
 * <pre>
 *   POST /api/v1/payments                     发起支付（登录）
 *   GET  /api/v1/payments/{paymentNo}          查支付单（登录）
 *   GET  /api/v1/payments/order/{orderId}      按订单查支付单（登录）
 *   POST /api/v1/payments/{paymentNo}/sync     主动查单对账（登录）
 *   POST /api/v1/payments/mock-pay/{paymentNo} 模拟用户在第三方完成支付（仅 MOCK 渠道）
 *   POST /api/v1/payments/callback/{channel}   ★ 第三方异步回调（公网、不登录、只验签）
 * </pre>
 *
 * <h2>★ 回调端点为什么不加登录校验</h2>
 * 第三方支付平台（支付宝服务器）不可能持有我们用户的 token。
 * 回调接口的"身份"由<b>验签</b>保证（不是 token），这是设计上的根本差异：
 * 业务接口靠"你是谁"（认证），回调接口靠"消息是谁发的、有没有被改"（完整性）。
 *
 * <h2>返回值为什么必须是特定字符串</h2>
 * 支付宝要求收到 "success" 才停止重发；返回其他内容或抛异常，它会持续重试。
 * 所以回调方法<b>绝不抛异常</b>，一律返回明确报文。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final MockPayChannel mockPayChannel;

    @Value("${aimall.pay.default-channel:MOCK}")
    private String defaultChannel;

    /** 发起支付：返回收银台信息 */
    @PostMapping
    public R<PayCreateVO> create(@RequestBody Map<String, Object> body) {
        Long orderId = body.get("orderId") == null ? null
                : Long.valueOf(String.valueOf(body.get("orderId")));
        if (orderId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "orderId 不能为空");
        }
        String channel = body.get("channel") == null ? defaultChannel : String.valueOf(body.get("channel"));
        return R.ok(paymentService.create(orderId, channel));
    }

    @GetMapping("/{paymentNo}")
    public R<Payment> detail(@PathVariable String paymentNo) {
        return R.ok(paymentService.detail(paymentNo));
    }

    @GetMapping("/order/{orderId}")
    public R<Payment> byOrder(@PathVariable Long orderId) {
        return R.ok(paymentService.byOrder(orderId));
    }

    /**
     * 主动查单对账：前端在订单详情页轮询，兜住"回调丢失"的情况。
     */
    @PostMapping("/{paymentNo}/sync")
    public R<Boolean> sync(@PathVariable String paymentNo) {
        return R.ok(paymentService.sync(paymentNo));
    }

    /**
     * 【仅 MOCK 渠道】模拟"用户在第三方完成支付"。
     *
     * <p>真实场景下这一步发生在支付宝页面；模拟渠道没有第三方，
     * 所以由这个端点代为触发——但<b>它不直接改状态</b>，而是构造带签名的回调参数
     * 去回调 {@code /callback/MOCK}，走与真实渠道完全相同的验签/幂等/状态机路径。
     * 这样"模拟"只模拟了"第三方不存在"，工程链路是真的。</p>
     */
    @PostMapping("/mock-pay/{paymentNo}")
    public R<String> mockPay(@PathVariable String paymentNo) {
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        Payment p = paymentService.detail(paymentNo);
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "支付单不存在");
        }
        // 归属校验（评审修正）：不能替别人支付——与订单/笔记详情同一条防越权规则
        if (!p.getUserId().equals(cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "支付单不存在");
        }
        if (!MockPayChannel.CODE.equalsIgnoreCase(p.getChannel())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅 MOCK 渠道支持模拟支付");
        }

        // 构造第三方风格的回调参数（字典序无所谓，签名方法内部会排序）
        Map<String, String> params = new LinkedHashMap<>();
        params.put("payment_no", p.getPaymentNo());
        params.put("order_no", p.getOrderNo());
        params.put("trade_no", "MOCK" + System.currentTimeMillis());
        params.put("amount", p.getAmount().toPlainString());
        params.put("status", "PAID");
        params.put(MockPayChannel.SIGN_PARAM, mockPayChannel.sign(params));

        CallbackResult r = paymentService.handleCallback(MockPayChannel.CODE, params);
        return r.success() ? R.ok("模拟支付成功") : R.fail("模拟支付处理失败：" + r.message());
    }

    /**
     * ★ 第三方异步回调（公网可访问、无 token、靠验签）。
     *
     * <p>注意：这里<b>永不抛异常</b>——抛异常会让第三方认为通知失败并持续重发。</p>
     */
    @PostMapping("/callback/{channel}")
    public String callback(@PathVariable String channel,
                           @RequestParam Map<String, String> params) {
        try {
            CallbackResult r = paymentService.handleCallback(channel, params);
            if (!r.success()) {
                log.warn("支付回调处理失败 channel={} paymentNo={} msg={}",
                        channel, r.paymentNo(), r.message());
                // 返回非 success：让第三方稍后重发（业务性的失败可以重试）
                return r.message();
            }
            return r.message();   // "success"
        } catch (Exception e) {
            // 系统异常同样不能抛：记录日志并让第三方重试
            log.error("支付回调处理异常 channel={} : {}", channel, e.getMessage(), e);
            return "system-error";
        }
    }
}
