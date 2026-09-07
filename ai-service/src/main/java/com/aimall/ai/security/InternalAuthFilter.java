package com.aimall.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * 服务间内部鉴权（internal auth）—— 与 V3 支付回调验签**同一套 HMAC 思路**。
 *
 * <h2>★ 为什么服务间也要鉴权："内网就安全"是错觉</h2>
 * 常见谬误："服务都在内网/同一台机，裸奔就行"。真实风险：
 * <ul>
 *   <li>容器网络/办公网被横向渗透后，内部接口裸奔 = 任意调用（可直接烧你的 AI 额度）；</li>
 *   <li>端口映射/配置错误把内部端口暴露到公网（本服务 8081 若误开就是免费 AI 接口）。</li>
 * </ul>
 * 所以内部调用也要"证明你是谁 + 报文没被改"——用 HMAC 签名（对称密钥，双方共享），
 * 与 V3 支付的 MockPayChannel.sign 是同一个算法家族（TreeMap 排序 + constant-time 比较）。
 *
 * <h2>签名协议</h2>
 * <pre>
 *   header: X-Internal-Client = mall-app
 *   header: X-Internal-Ts     = 当前毫秒时间戳
 *   header: X-Internal-Sign   = HMAC-SHA256( client + ts + secret )
 * </pre>
 * 时间戳参与签名并校验 5 分钟窗口 —— <b>防重放</b>：攻击者截获一次请求后原样重发，
 * 签名虽然"对"，但时间戳过期就拒绝（这是支付回调之外，签名方案的第二个必备元素）。
 *
 * <p>排除端点：/actuator/**（运维探测）与 /error。</p>
 */
@Slf4j
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_CLIENT = "X-Internal-Client";
    public static final String HEADER_TS = "X-Internal-Ts";
    public static final String HEADER_SIGN = "X-Internal-Sign";

    /** 允许的时钟偏差：5 分钟（防重放窗口） */
    private static final long MAX_SKEW = 5 * 60 * 1000L;

    private final String secret;

    /**
     * 构造器注入密钥（不用 @Component/@Value——
     * ★ 注册由 WebConfig 的 FilterRegistrationBean 显式完成：若类上加 @Component，
     *   Boot 会把它自动注册到 /* 且与 RegistrationBean 同名冲突（BeanDefinitionOverrideException）。
     */
    public InternalAuthFilter(String secret) {
        this.secret = secret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String client = request.getHeader(HEADER_CLIENT);
        String ts = request.getHeader(HEADER_TS);
        String sign = request.getHeader(HEADER_SIGN);

        if (!StringUtils.hasText(client) || !StringUtils.hasText(ts) || !StringUtils.hasText(sign)) {
            reject(response, "missing internal auth headers");
            return;
        }
        // ① 时间戳新鲜度（防重放）
        long timestamp;
        try {
            timestamp = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            reject(response, "invalid timestamp");
            return;
        }
        if (Math.abs(System.currentTimeMillis() - timestamp) > MAX_SKEW) {
            reject(response, "timestamp expired (replay?)");
            return;
        }
        // ② 签名校验（防伪造/篡改）
        String expected = sign(client, ts);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8))) {
            log.warn("内部调用验签失败 client={} uri={}", client, request.getRequestURI());
            reject(response, "sign error");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 签名算法：与支付渠道同构（参数排序+拼接+HMAC） */
    public String sign(String client, String ts) {
        Map<String, String> params = new TreeMap<>();
        params.put("client", client);
        params.put("ts", ts);
        String plain = "client=" + client + "&ts=" + ts;
        return hmacSha256(plain, secret);
    }

    private void reject(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"internal auth failed: " + msg + "\"}");
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
