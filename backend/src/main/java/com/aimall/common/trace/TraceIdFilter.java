package com.aimall.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * traceId 入口过滤器：为每个请求生成/透传 traceId，并回写到响应头。
 *
 * <h2>三个设计点</h2>
 * <ol>
 *   <li><b>用 Filter 而不是 Interceptor</b>：Filter 在 Servlet 容器层面，早于 Spring MVC 的
 *       DispatcherServlet。这样连「被拦截器拦掉的 401 请求」也有 traceId —— 排查鉴权问题时最需要它。</li>
 *   <li><b>支持上游透传</b>：若请求头带了 {@code X-Trace-Id}（例如 V4 网关已生成），直接沿用而不重新生成。
 *       这是<b>分布式链路追踪</b>的基础——一条链路跨多个服务，traceId 必须一致才能串起来。</li>
 *   <li><b>finally 里必须 clear</b>：Tomcat 线程是<b>池化复用</b>的，不清 MDC 会串号——
 *       下一个请求复用到这个线程时，会打印上一个请求的 traceId。这是初学者最常踩的坑。</li>
 * </ol>
 *
 * <p>回写响应头的价值：用户报障时只要给出响应头里的 traceId，你就能在日志里精确 grep 到他这次请求的全部日志。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER_TRACE_ID);
        // 上游（网关/其他服务）传了就沿用，否则自己生成 —— 为 V4 微服务链路追踪预留
        String traceId = StringUtils.hasText(incoming) ? incoming : TraceIdContext.generate();

        TraceIdContext.set(traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理：Tomcat 线程池复用，不清会串号
            TraceIdContext.clear();
        }
    }
}
