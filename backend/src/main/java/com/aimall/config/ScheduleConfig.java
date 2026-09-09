package com.aimall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关 —— @EnableScheduling 单独一个配置类，职责清晰。
 *
 * <p>为什么单独开：@Scheduled 注解默认不生效，必须有 @EnableScheduling 激活；
 * 和 @EnableAsync 分开放，各自的表达意图一目了然（异步执行器 / 定时调度器）。</p>
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {
}
