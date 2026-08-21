package com.aimall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：
 * - 本地开发：允许 Vite dev server（localhost/127.0.0.1 任意端口）
 * - 公网访问：允许 Cloudflare Tunnel 域名（请求经 Vite proxy 转发时保留 Origin 头，
 *   浏览器同源带 Origin 的 POST 会被后端 CORS 校验，域名须加入白名单否则 403 Invalid CORS request）
 * 生产环境由网关/同域部署替代。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "https://paimon.store",
                        "https://*.paimon.store")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}