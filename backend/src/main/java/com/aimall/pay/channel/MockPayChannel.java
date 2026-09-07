package com.aimall.pay.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 本地模拟支付渠道（<b>默认实现，零依赖可跑通</b>）。
 *
 * <h2>★ 它是"模拟"的，但签名是真的</h2>
 * 唯一模拟的地方是：<b>第三方不存在</b>（没有真的支付宝服务器）。
 * 除此之外全是生产级写法：
 * <ul>
 *   <li><b>HMAC-SHA256 签名</b>：真实算法，密钥来自配置。回调报文被篡改一个字符就验签失败；</li>
 *   <li><b>参数排序拼接</b>：按 key 字典序拼接后签名（各家支付网关的通用做法）；</li>
 *   <li><b>回调与查单</b>：接口形态与真实渠道一致。</li>
 * </ul>
 * 所以换成真实渠道时，<b>业务代码（验签/幂等/状态机）一行都不用改</b>——
 * 这正是抽象的价值，也是面试时能讲清楚的点。
 *
 * <h2>签名算法说明（面试可展开）</h2>
 * 真实支付宝用 <b>RSA2（非对称）</b>：我方用应用私钥签名，支付宝用应用公钥验签；
 * 我方收到支付宝回调时，用<b>支付宝公钥</b>验签。
 * 这里用 <b>HMAC-SHA256（对称）</b>，因为模拟渠道的收发双方都是我们自己，
 * 对称密钥更合适；换成 RSA2 只需替换签名/验签两个私有方法。
 */
@Slf4j
@Component
public class MockPayChannel implements PayChannel {

    public static final String CODE = "MOCK";

    /** 回调报文里的签名字段名 */
    public static final String SIGN_PARAM = "sign";

    @Value("${aimall.pay.mock.secret:aimall-mock-secret}")
    private String secret;

    @Value("${aimall.pay.mock.cashier-url:/mock-cashier}")
    private String cashierUrl;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public CashierInfo create(String paymentNo, BigDecimal amount, String subject) {
        // 真实渠道：这里要调支付宝 SDK 拿到支付页面 URL
        // 模拟渠道：拼一个本地"收银台"地址，前端据此弹出模拟收银台
        String params = "paymentNo=" + paymentNo + "&amount=" + amount.toPlainString();
        return new CashierInfo(cashierUrl + "?" + params, CODE, params);
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        String sign = params.get(SIGN_PARAM);
        if (sign == null || sign.isBlank()) {
            log.warn("回调缺少签名字段");
            return false;
        }
        String expected = sign(params);
        // 注意：用 constant-time 比较，防时序攻击（安全编码习惯）
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public QueryResult query(String paymentNo) {
        // 模拟渠道没有"第三方"可查：返回 UNKNOWN，由调用方以本地状态为准。
        // 真实渠道这里要发起 HTTPS 查单请求（见 AlipaySandboxChannel 的说明）。
        return new QueryResult(QueryResult.UNKNOWN, null);
    }

    /**
     * 生成签名：参数按 key 字典序排序 → 拼成 k=v&k=v → HMAC-SHA256。
     *
     * <p>为什么必须排序：Map 的遍历顺序不稳定（HashMap），
     * 不排序的话同样的参数可能拼出不同的串 → 签名时对时错。
     * 各家支付网关文档都会强调"按 ASCII 码从小到大排序"，原因就在这。</p>
     */
    public String sign(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<>(params);
        List<String> parts = new ArrayList<>();
        sorted.forEach((k, v) -> {
            // 签名字段本身不参与签名；空值不参与
            if (!SIGN_PARAM.equals(k) && v != null && !v.isBlank()) {
                parts.add(k + "=" + v);
            }
        });
        String plain = String.join("&", parts);
        return hmacSha256(plain, secret);
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("签名计算失败", e);
        }
    }
}
