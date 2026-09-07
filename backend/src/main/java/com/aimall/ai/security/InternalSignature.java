package com.aimall.ai.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 服务内部调用签名（mall-app 侧工具类）。
 *
 * <p>与 ai-service 的 InternalAuthFilter 是<b>同一套协议</b>：
 * 客户端用本类生成三件套请求头，服务端用 Filter 校验。
 * 两边都按"client + ts + secret"排序拼接后 HMAC-SHA256——
 * 与 V3 支付渠道的签名是同一算法家族（项目内第三处复用 HMAC 签名）。</p>
 *
 * <p>为什么抽成工具类而不是写死在 Config：签名逻辑要能被测试覆盖，
 * 也要能被未来的其它内部调用（如订单服务回调）复用。</p>
 */
public final class InternalSignature {

    public static final String HEADER_CLIENT = "X-Internal-Client";
    public static final String HEADER_TS = "X-Internal-Ts";
    public static final String HEADER_SIGN = "X-Internal-Sign";

    private InternalSignature() {
    }

    /** 生成签名：与服务端 InternalAuthFilter.sign(client, ts) 完全一致的算法 */
    public static String sign(String client, String ts, String secret) {
        String plain = "client=" + client + "&ts=" + ts;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("内部调用签名失败", e);
        }
    }
}
