package com.aimall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类 —— 所有外部流量的唯一入口（端口 9000）。
 *
 * <h2>★ 为什么 V4 需要网关（不是"微服务标配所以加"）</h2>
 * <ol>
 *   <li><b>安全边界</b>：ai-service 的内部端点（/internal/**）绝不能暴露给用户。
 *       没有网关时，用户只要知道 8081 端口就能直接打；有了网关，外部只有 9000 一个口，
 *       且网关<b>显式拒绝</b>任何 /internal/** 的外部请求。</li>
 *   <li><b>统一入口</b>：前端只需认识一个地址（未来加服务也不改前端）。</li>
 *   <li><b>横切逻辑集中</b>：traceId 生成/透传、跨域、限流、日志都放在网关——业务服务各自专注业务。</li>
 * </ol>
 *
 * <p>反过来——<b>没做的部分</b>：网关鉴权（JWT 校验）本项目仍放在 mall-app（Sa-Token），
 * 网关只做转发与链路标记。生产可把"认证"上移到网关（Spring Security + JWT），
 * <b>知道它在演进路线上的位置即可</b>。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
