package com.aimall.common.trace;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 异步线程池的 MDC 传递装饰器 —— 解决「切线程后 traceId 丢失」这个经典坑。
 *
 * <h2>问题：为什么异步里日志没有 traceId 了？</h2>
 * MDC 底层是 <b>ThreadLocal</b>（Logback 里是 ThreadLocal + 可选的 InheritableThreadLocal）。
 * 请求线程 A 往 MDC 塞了 traceId，然后 {@code @Async} 把任务扔给线程池的线程 B 执行——
 * 线程 B 的 ThreadLocal 是<b>空的</b>，于是异步任务里所有日志的 traceId 都是空的。
 * 结果：一次请求的日志被切成两半，一半有 traceId、一半没有，等于白做。
 *
 * <h2>解法：在任务提交时"拍照"，执行时"还原"</h2>
 * <pre>
 *   提交任务（线程A）              执行任务（线程B）
 *   ┌──────────────────┐         ┌──────────────────┐
 *   │ MDC.getCopyOf-   │  包装   │ MDC.setContextMap│
 *   │ ContextMap()     │ ─────► │ (拍下的快照)      │
 *   │  → 拍下快照       │         │  → 还原到线程B    │
 *   └──────────────────┘         │ 执行 runnable    │
 *                                 │ finally: clear() │
 *                                 └──────────────────┘
 * </pre>
 *
 * <h2>为什么 finally 里要 clear</h2>
 * 线程 B 执行完会<b>归还线程池</b>。如果不清理，它身上还挂着这次请求的 traceId，
 * 下次被别的请求复用时就会打印<b>错误的 traceId</b>（比"丢失"更糟——会误导排查方向）。
 *
 * <h2>面试加分点：为什么不用 InheritableThreadLocal？</h2>
 * Logback 支持把 MDC 配成 InheritableThreadLocal，子线程<b>创建时</b>会继承父线程的值。
 * 但线程池的线程是<b>提前创建好并长期复用</b>的——创建时父线程里还没有 traceId，
 * 继承了个寂寞。所以线程池场景<b>必须用装饰器手动传递</b>，这是唯一可靠的做法。
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 在【提交方线程】拍下当前 MDC 快照
        Map<String, String> snapshot = MDC.getCopyOfContextMap();

        return () -> {
            try {
                // 在【执行方线程】还原快照
                if (snapshot != null) {
                    MDC.setContextMap(snapshot);
                } else {
                    MDC.clear();
                }
                runnable.run();
            } finally {
                // 归还线程池前必须清理，避免串号
                MDC.clear();
            }
        };
    }
}
