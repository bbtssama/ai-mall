package com.aimall.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器：/api/** 默认需要登录，白名单排除登录/注册
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        // V3：支付回调——第三方（支付宝服务器）不可能持有我们用户的 token。
                        // 回调接口的"身份"由【验签】保证，而不是 token：
                        //   业务接口靠"你是谁"（认证），回调接口靠"消息是谁发的、有没有被改"（完整性）。
                        // 若这里不放行，回调会被 401 拦掉，订单永远无法变为已支付。
                        "/api/v1/payments/callback/**",
                        "/error"
                );
    }
}