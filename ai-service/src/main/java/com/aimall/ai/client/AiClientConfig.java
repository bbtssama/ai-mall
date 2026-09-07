package com.aimall.ai.client;

import com.aimall.ai.security.InternalAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 声明式 HTTP 客户端装配 + 内部调用签名拦截器。
 *
 * <p>关键点：把 {@link InternalAuthFilter} 需要的三个头在<b>客户端侧</b>自动带上——
 * 服务端校验什么，客户端就生成什么（同一个 secret，对称密钥）。</p>
 */
@Slf4j
@Configuration
public class AiClientConfig {

    @Value("${aimall.mall.base-url:http://localhost:8080}")
    private String mallBaseUrl;

    @Value("${aimall.internal.secret:aimall-internal-secret}")
    private String secret;

    @Value("${aimall.internal.client-id:ai-service}")
    private String clientId;

    @Bean
    public RestClient mallRestClient() {
        return RestClient.builder()
                .baseUrl(mallBaseUrl)
                // 超时：AI 链路已经很慢，回调商品服务必须"快进快出"——
                // 不能让一个慢回调把 AI 请求拖到不可用（故障隔离的微观实践）
                .requestFactory(clientHttpRequestFactory())
                .defaultRequest(req -> {
                    String ts = String.valueOf(System.currentTimeMillis());
                    req.header(InternalAuthFilter.HEADER_CLIENT, clientId);
                    req.header(InternalAuthFilter.HEADER_TS, ts);
                    req.header(InternalAuthFilter.HEADER_SIGN, sign(clientId, ts));
                })
                .build();
    }

    @Bean
    public MallClient mallClient(RestClient mallRestClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(mallRestClient))
                .build()
                .createClient(MallClient.class);
    }

    private org.springframework.http.client.ClientHttpRequestFactory clientHttpRequestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory f =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(2));
        f.setReadTimeout(Duration.ofSeconds(5));
        return f;
    }

    private String sign(String client, String ts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(("client=" + client + "&ts=" + ts).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("内部调用签名失败", e);
        }
    }
}
