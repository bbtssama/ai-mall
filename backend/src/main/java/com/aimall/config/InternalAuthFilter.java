package com.aimall.config;

import com.aimall.ai.security.InternalSignature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * 校验来自 ai-service 的内部调用签名（mall-app 侧）。
 *
 * <p>协议与 ai-service 的 InternalAuthFilter 完全对称：
 * 同样校验 client/ts/sign 三件套 + 5 分钟时间窗（防重放）。
 * <b>两边用同一个 secret、同一个算法、同一套参数顺序</b>——
 * 这是内部签名能成立的前提（与 V3 支付验签"收发双方同算法"同理）。</p>
 */
@Slf4j
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final long MAX_SKEW = 5 * 60 * 1000L;

    @Value("${aimall.internal.secret:aimall-internal-secret}")
    private String secret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String client = request.getHeader(InternalSignature.HEADER_CLIENT);
        String ts = request.getHeader(InternalSignature.HEADER_TS);
        String sign = request.getHeader(InternalSignature.HEADER_SIGN);

        if (!StringUtils.hasText(client) || !StringUtils.hasText(ts) || !StringUtils.hasText(sign)) {
            reject(response, "missing internal auth headers");
            return;
        }
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
        String expected = InternalSignature.sign(client, ts, secret);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8))) {
            log.warn("内部调用验签失败 client={} uri={}", client, request.getRequestURI());
            reject(response, "sign error");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"internal auth failed: " + msg + "\"}");
    }
}
