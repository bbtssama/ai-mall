package com.aimall.ai.config;

import com.aimall.ai.security.InternalAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册内部鉴权过滤器。
 *
 * <p>★ 用 FilterRegistrationBean 显式注册（而非 @Component 自动扫描），
 * 原因有二：</p>
 * <ol>
 *   <li><b>URL 模式</b>：@Component 的 Filter 会被 Boot 自动注册到 /*——
 *       我们只想保护 /internal/*；</li>
 *   <li><b>避免同名 Bean 冲突</b>：@Component + @Bean 同名会抛
 *       BeanDefinitionOverrideException（Boot 3 默认禁止覆盖）。</li>
 * </ol>
 * <p>密钥经构造器传入（@Value 在本配置类上取，Filter 本体保持纯净）。</p>
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<InternalAuthFilter> internalAuthFilterRegistration(
            @Value("${aimall.internal.secret:aimall-internal-secret}") String secret) {
        FilterRegistrationBean<InternalAuthFilter> bean =
                new FilterRegistrationBean<>(new InternalAuthFilter(secret));
        bean.addUrlPatterns("/internal/*");   // ★ 只保护内部端点（actuator/error 不受影响）
        bean.setOrder(1);
        return bean;
    }
}
