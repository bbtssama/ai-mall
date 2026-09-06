package com.aimall.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪 ID（traceId）上下文工具 —— 基于 SLF4J 的 {@link MDC}。
 *
 * <h2>为什么需要它</h2>
 * 一个 HTTP 请求进来，会打十几条日志（Controller 入参、SQL、AI 调用、异常……）。
 * 线上并发几百时，这些日志是<b>交错</b>的——没有 traceId，你根本分不清哪些日志属于同一个请求，
 * 只能靠时间戳猜。这就是"玩具项目"和"能上线的项目"的分水岭之一。
 *
 * <h2>MDC 是什么</h2>
 * MDC（Mapped Diagnostic Context）= 日志框架提供的<b>线程本地 Map</b>。
 * 往里 put 了 traceId，日志 pattern 里写 {@code %X{traceId}} 就能自动打印出来，
 * 业务代码完全不用手动传参——这是"无侵入埋点"的典型手法。
 *
 * <h2>为什么用 MDC 而不是方法参数传递</h2>
 * 参数传递要改所有方法签名，侵入性极大。MDC 靠 ThreadLocal，天然"随线程走"。
 * 代价是：<b>切线程会丢失</b>（异步线程池 / @Async），所以需要 {@link MdcTaskDecorator} 配合。
 */
public final class TraceIdContext {

    /** MDC 中的 key，与 logback-spring.xml 的 %X{traceId} 对应 */
    public static final String TRACE_ID = "traceId";

    private TraceIdContext() {
    }

    /** 生成一个短 traceId（16 位十六进制，够用且日志里不占地方） */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static void set(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    public static String get() {
        return MDC.get(TRACE_ID);
    }

    public static void clear() {
        MDC.remove(TRACE_ID);
    }
}
