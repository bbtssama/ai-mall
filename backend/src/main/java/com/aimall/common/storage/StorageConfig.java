package com.aimall.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * 存储装配：按配置选择实现类，并为本地存储补上静态资源映射。
 *
 * <h2>为什么用 if 判断而不是 @ConditionalOnProperty</h2>
 * 也可以写成两个 {@code @Bean} + {@code @ConditionalOnProperty(name="aimall.storage.type", havingValue="minio")}，
 * 但那样需要两个类都带 {@code @Component} 注解，且切换时容易漏配。
 * 这里用<b>一个工厂方法显式选择</b>，逻辑一眼可见，且"未配置 = 本地"的兜底语义非常明确。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public StorageService storageService(StorageProperties properties) {
        if ("minio".equalsIgnoreCase(properties.getType())) {
            return new MinioStorageService(properties);
        }
        // 默认（以及任何未知取值）都走本地实现，保证零依赖可启动
        return new LocalStorageService(properties);
    }

    /**
     * 本地存储时，把 {@code /files/**} 映射到磁盘目录。
     *
     * <p>不映射会怎样：文件上传成功了、数据库里也存了 URL，但浏览器访问 404——
     * 因为 Spring Boot 默认只把 {@code classpath:/static/} 等目录作为静态资源，
     * 磁盘上 {@code ./uploads} 目录它并不知道。</p>
     *
     * <p><b>注意安全边界</b>：这里只映射 uploads 一个目录，不要把整个磁盘根路径暴露出去。</p>
     */
    @Bean
    public WebMvcConfigurer storageResourceConfigurer(StorageProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String root = properties.getLocal().getRoot();
                if (!root.endsWith("/")) {
                    root = root + "/";
                }
                registry.addResourceHandler("/files/**")
                        .addResourceLocations("file:" + root)
                        // 文件名含 UUID 不会重复，内容不变，可安全长缓存
                        .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS));
            }
        };
    }
}
