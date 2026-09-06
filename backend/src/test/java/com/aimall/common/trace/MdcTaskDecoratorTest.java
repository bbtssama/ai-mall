package com.aimall.common.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MDC 跨线程传递测试 —— 验证异步场景 traceId 不丢、且不串号。
 *
 * <p>这两个测试的价值：MDC 丢失是<b>运行时才暴露</b>的隐性 bug——
 * 本地开发时一切正常，上线后才发现异步任务的日志串不起来。
 * 用单测把它钉死，比"线上出问题再排查"成本低得多。</p>
 */
class MdcTaskDecoratorTest {

    @Test
    @DisplayName("异步任务里能读到主线程的 traceId（ThreadLocal 跨线程传递）")
    void shouldPropagateTraceIdToAsyncThread() throws Exception {
        TraceIdContext.set("trace-123");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            String[] seenInWorker = new String[1];
            Runnable task = new MdcTaskDecorator().decorate(
                    () -> seenInWorker[0] = MDC.get(TraceIdContext.TRACE_ID));

            pool.submit(task).get(5, TimeUnit.SECONDS);

            assertEquals("trace-123", seenInWorker[0], "异步线程应能读到主线程的 traceId");
        } finally {
            pool.shutdownNow();
            TraceIdContext.clear();
        }
    }

    @Test
    @DisplayName("任务执行完毕后必须清空 MDC，否则线程池复用会串号")
    void shouldClearMdcAfterTaskFinished() throws Exception {
        TraceIdContext.set("trace-abc");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 跑一个有 traceId 的装饰任务
            pool.submit(new MdcTaskDecorator().decorate(() -> {
            })).get(5, TimeUnit.SECONDS);

            // 再往同一个线程提交一个【未装饰】的普通任务：
            // 如果装饰任务没清理干净，这里会读到上一次的 traceId（串号）
            String[] leaked = new String[1];
            pool.submit(() -> leaked[0] = MDC.get(TraceIdContext.TRACE_ID))
                    .get(5, TimeUnit.SECONDS);

            assertNull(leaked[0], "任务结束后必须清理 MDC，否则线程池复用会导致 traceId 串号");
        } finally {
            pool.shutdownNow();
            TraceIdContext.clear();
        }
    }
}
