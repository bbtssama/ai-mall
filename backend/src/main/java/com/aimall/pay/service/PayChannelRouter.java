package com.aimall.pay.service;

import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.pay.channel.MockPayChannel;
import com.aimall.pay.channel.PayChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 支付渠道路由 —— 按 channel 码挑选实现。
 *
 * <p>为什么用工厂方法而不是 @ConditionalOnProperty：与 StorageService 同样的取舍——
 * 所有实现摆在眼前，"配了用哪个、没配兜底谁、新增渠道改哪里"一目了然，
 * 且能在一个方法里集中处理"渠道不存在"的降级策略。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayChannelRouter {

    private final List<PayChannel> channels;

    /**
     * 取渠道实现；渠道码未匹配时<b>降级为 MOCK</b>。
     *
     * <p>为什么不直接抛错：支付是交易主链路，配置写错一个字母就让整个下单不可用
     * 代价太大；降级到可跑通的模拟渠道，至少功能不中断，同时在日志里明确告警。</p>
     */
    public PayChannel resolve(String channel) {
        PayChannel matched = channels.stream()
                .filter(c -> c.code().equalsIgnoreCase(channel))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            return matched;
        }
        log.warn("未找到支付渠道 {}，降级为 MOCK（请检查 aimall.pay.channel 配置或渠道实现是否注册）", channel);
        return channels.stream()
                .filter(c -> MockPayChannel.CODE.equals(c.code()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.SERVER_ERROR, "无可用支付渠道"));
    }

    /** 从回调参数里取我方支付单号（兼容下划线与驼峰两种键名） */
    public String paymentNoOf(Map<String, String> params) {
        String v = params.get("payment_no");
        return v != null ? v : params.get("paymentNo");
    }

    /** 从回调参数里取第三方交易号 */
    public String tradeNoOf(Map<String, String> params) {
        String v = params.get("trade_no");
        return v != null ? v : params.get("tradeNo");
    }
}
