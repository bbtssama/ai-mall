package com.aimall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 网关全局过滤器：traceId 生成与透传。
 *
 * <h2>★ 这是 V1.5 traceId 设计在微服务下的兑现</h2>
 * V1.5 的 TraceIdFilter 写了"上游传了 traceId 就沿用，否则自己生成——为 V4 微服务链路追踪预留"，
 * 现在上游真的来了：<b>网关是链路第一跳，负责生成 traceId 并透传给下游</b>：
 *
 * <pre>
 *   浏览器 → gateway(生成 traceId) → mall-app(沿用) → ai-service(再透传)
 * </pre>
 *
 * <p>于是 `grep <traceId>` 能跨三个进程捞出同一次请求的全链路日志——
 * 这正是"可观测性"在微服务下的意义（V1.5 埋的伏笔在这里兑现）。</p>
 *
 * <p>实现要点（WebFlux 版网关）：</p>
 * <ul>
 *   <li>请求进来：无 X-Trace-Id 就生成，通过 {@code mutate()} 加到下游请求头；</li>
 *   <li>响应出去：把 traceId 回写响应头（用户报障时手里有凭证）；</li>
 *   <li>记录访问日志（统一在网关记一次，含耗时）。</li>
 * </ul>
 */
@Slf4j
@Configuration
public class TraceIdGlobalFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";

    @Bean
    @Order(-1)   // 最先执行：后续过滤器/路由都能拿到 traceId
    public GlobalFilter traceIdFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String incoming = request.getHeaders().getFirst(TRACE_HEADER);
            String traceId = (incoming != null && !incoming.isBlank())
                    ? incoming
                    : "gw-" + UUID.randomUUID().toString().substring(0, 8);

            ServerHttpRequest mutated = request.mutate()
                    .header(TRACE_HEADER, traceId)
                    .build();
            exchange.getResponse().getHeaders().set(TRACE_HEADER, traceId);

            long start = System.currentTimeMillis();
            return chain.filter(exchange.mutate().request(mutated).build())
                    .then(Mono.fromRunnable(() -> log.info("[{}] {} {} -> {} ({}ms)",
                            traceId,
                            request.getMethod(),
                            request.getURI().getPath(),
                            exchange.getResponse().getStatusCode(),
                            System.currentTimeMillis() - start)));
        };
    }
}
