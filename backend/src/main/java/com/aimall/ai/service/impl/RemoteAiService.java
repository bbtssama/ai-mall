package com.aimall.ai.service.impl;

import com.aimall.ai.client.AiServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 远程 AI 服务（V4：AI 能力部署在独立的 ai-service）。
 *
 * <h2>★ 它的存在让"拆分"对上层透明</h2>
 * 用户侧的 AI 问答仍由 mall-app 的 {@code ChatController} 承载（用户鉴权/限流/会话持久化都在业务侧），
 * 本服务只负责"把请求转给 ai-service"——对上层来说，本地与远程是同一个接口契约。
 *
 * <h2>降级：ai-service 不可用怎么办</h2>
 * 远程调用失败时记录日志并抛出业务异常，由 Controller 转成友好提示
 * （"AI 助手正在休息"）。<b>绝不让 AI 故障影响下单/浏览</b>——这正是独立拆分的收益：
 * 故障被隔离在 AI 链路里，商城主体照常可用。
 *
 * @see LocalAiService 本地实现（默认）
 */
@Slf4j
@Service("remoteAiService")
@RequiredArgsConstructor
public class RemoteAiService implements AiService {

    private final ObjectProvider<AiServiceClient> clientProvider;

    /**
     * 远程问答（带熔断 + 降级）。
     *
     * <h2>★ 熔断在这里的业务理由（不是"微服务标配所以加"）</h2>
     * AI 调用本来就慢（秒级），超时后每个请求还要<b>等满 120 秒</b>才失败——
     * 请求线程被长时间占住，正是 V4 拆出 ai-service 想解决的"AI 拖垮业务线程池"问题。
     * 熔断后：连续失败达到阈值 → <b>快速失败</b>（直接走 fallback，不再等超时）→
     * 每隔一段时间<b>半开</b>放一个请求试探 → 恢复则闭合。
     * 这是"故障隔离"从架构图落到代码的最后一步。
     *
     * @param message     文本问题
     * @param imageBase64 图片（跨服务传 base64 有带宽代价，生产应传对象存储 URL/键——见文档"演进方向"）
     */
    @Override
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "aiService", fallbackMethod = "chatFallback")
    public String chat(String message, String imageBase64) {
        AiServiceClient client = require();
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        if (imageBase64 != null) {
            body.put("imageBase64", imageBase64);
        }
        Map<String, Object> resp = client.chat(body);
        return String.valueOf(resp.getOrDefault("answer", ""));
    }

    /**
     * 降级方法（fallback）：签名必须与原方法一致 + 额外的 Throwable 参数。
     *
     * <p>注意：这里返回的是<b>友好文案</b>而不是抛异常——
     * 用户看到"AI 助手正在休息"，商城的浏览/下单/支付完全不受影响（故障隔离）。</p>
     */
    @SuppressWarnings("unused")
    private String chatFallback(String message, String imageBase64, Throwable t) {
        log.warn("AI 调用熔断/失败，走降级文案: {}", t.toString());
        return "AI 助手正在休息中（服务暂时不可用），请稍后再试～";
    }

    @Override
    public Flux<String> stream(String message) {
        // 流式跨服务透传：本版走 mall-app 本地链路（见 ChatServiceImpl 的模式判断），
        // 这里保留接口语义——生产可用 WebClient 直连 ai-service 的 SSE 端点做透传
        return Flux.just(chat(message, null));
    }

    private AiServiceClient require() {
        AiServiceClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("ai-service 客户端未装配（请检查 aimall.ai.mode=remote 配置）");
        }
        return client;
    }
}
