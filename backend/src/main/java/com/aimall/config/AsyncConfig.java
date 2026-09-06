package com.aimall.config;

import com.aimall.common.trace.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置 —— 为 V2「AI 内容审核异步化」等场景准备，并解决 traceId 跨线程传递。
 *
 * <h2>为什么不用 Spring Boot 默认的线程池</h2>
 * Boot 的 {@code applicationTaskExecutor} 默认队列是 {@code Integer.MAX_VALUE}——
 * 队列无界意味着<b>永远触发不到拒绝策略</b>，请求疯狂堆积最终 OOM。
 * 生产必须显式设置<b>有界队列 + 明确的拒绝策略</b>。
 *
 * <h2>关键参数怎么定</h2>
 * <ul>
 *   <li><b>核心/最大线程数</b>：本应用是 IO 密集型（查库、调 AI 接口、写文件），
 *       线程大部分时间在等 IO，所以线程数可以大于 CPU 核数。取 4~16 足够 demo 与压测。</li>
 *   <li><b>有界队列 200</b>：超出后触发拒绝策略，宁可快速失败也不要无限堆积。</li>
 *   <li><b>拒绝策略 CallerRunsPolicy</b>：队列满了就让<b>提交任务的线程自己跑</b>。
 *       这相当于一种天然的"限流"——提交方被占用了就没法继续提交，压力会向上游传导而不是把内存撑爆。
 *       这是最温和、最适合业务的默认拒绝策略（对比：AbortPolicy 直接抛异常，DiscardPolicy 静默丢任务）。</li>
 *   <li><b>优雅停机</b>：{@code waitForTasksToCompleteOnShutdown} 让应用关闭时先跑完手里的活，
 *       避免"发版瞬间丢一批审核任务"。</li>
 * </ul>
 *
 * <h2>bean 名为什么叫 taskExecutor</h2>
 * Spring 的 {@code @Async} 默认按名字 {@code taskExecutor} 找执行器。
 * 这里显式命名为它，@Async 无需写 {@code @Async("xxx")} 就能用上带 MDC 传递的线程池。
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = {"taskExecutor", "appExecutor"})
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("aimall-async-");
        // 关键：把 MDC（含 traceId）从提交线程传递到执行线程
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
