package com.aimall.ai.client;

import com.aimall.ai.dto.ChatRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

/**
 * ai-service 的声明式 HTTP 客户端（mall-app 侧）。
 *
 * <p>★ 声明式接口的价值：把"HTTP 调用"降级为"方法调用"——
 * Controller/Service 里看不到任何 URL 拼接、序列化、异常处理细节，
 * 就像调用本地方法一样（代理在背后完成 HTTP）。</p>
 */
@HttpExchange(url = "${aimall.ai-service.url}")
public interface AiServiceClient {

    /** 非流式问答 */
    @PostExchange("/internal/v1/ai/chat")
    Map<String, Object> chat(@RequestBody Map<String, Object> body);

    /** 入索引请求（笔记审核通过 / 商品上架时调用） */
    @PostExchange("/internal/v1/rag/index")
    Map<String, Object> index(@RequestBody Map<String, Object> body);
}
