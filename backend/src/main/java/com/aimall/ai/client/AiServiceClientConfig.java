package com.aimall.ai.client;

import com.aimall.ai.security.InternalSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * mall-app → ai-service 的客户端装配（<b>仅 remote 模式生效</b>）。
 *
 * <p>{@code @ConditionalOnProperty} 保证：默认 local 模式下不创建任何远程客户端 Bean——
 * 不配置就不存在，零依赖零影响（这也是"可降级"在 Spring 装配层面的表达）。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "aimall.ai.mode", havingValue = "remote")
public class AiServiceClientConfig {

    @Value("${aimall.ai-service.url:http://localhost:8081}")
    private String aiServiceUrl;

    @Value("${aimall.internal.secret:aimall-internal-secret}")
    private String secret;

    @Value("${aimall.internal.client-id:mall-app}")
    private String clientId;

    @Bean
    public RestClient aiServiceRestClient() {
        return RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory())
                // 每次请求自动带上内部签名三件套（服务端 InternalAuthFilter 校验）
                .defaultRequest(req -> {
                    String ts = String.valueOf(System.currentTimeMillis());
                    req.header(InternalSignature.HEADER_CLIENT, clientId);
                    req.header(InternalSignature.HEADER_TS, ts);
                    req.header(InternalSignature.HEADER_SIGN, InternalSignature.sign(clientId, ts, secret));
                })
                .build();
    }

    @Bean
    public AiServiceClient aiServiceClient(RestClient aiServiceRestClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(aiServiceRestClient))
                .build()
                .createClient(AiServiceClient.class);
    }

    /**
     * 超时设置：AI 调用本来就慢（流式更久），这里给足读超时；
     * 但连接超时必须短——连不上要快速失败，不要拖住用户请求线程。
     */
    private org.springframework.http.client.ClientHttpRequestFactory requestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory f =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(2));
        f.setReadTimeout(Duration.ofSeconds(120));
        return f;
    }
}
