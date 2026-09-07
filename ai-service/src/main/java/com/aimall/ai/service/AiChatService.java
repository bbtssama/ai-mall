package com.aimall.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 服务核心（ai-service 侧）。
 *
 * <p>与 V1/V2 的 ChatServiceImpl 同构：system prompt 定总纲 + 双工具由模型自选，
 * 按有无图片在两条 ChatClient 链路间分流。<b>迁移到独立服务后业务代码几乎不变</b>——
 * 这正是"AI 能力内聚、边界清晰"才做得到的平滑拆分。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ChatClient chatClient;
    private final ChatClient visionChatClient;

    public String chat(String message, String imageBase64) {
        if (imageBase64 != null && !imageBase64.isBlank()) {
            // 视觉链路：base64 → 字节 → Resource（Spring AI 的 media 需要 Resource）
            byte[] bytes = decodeBase64(imageBase64);
            return visionChatClient.prompt()
                    .system(visionSystemPrompt())
                    .user(u -> u.text(message == null ? "识别图中的商品" : message)
                            .media(MediaType.parseMediaType(detectMime(imageBase64)),
                                    new org.springframework.core.io.ByteArrayResource(bytes)))
                    .call()
                    .content();
        }
        return chatClient.prompt()
                .system(textSystemPrompt())
                .user(message)
                .call()
                .content();
    }

    public Flux<String> stream(String message, String imageBase64) {
        return chatClient.prompt()
                .system(textSystemPrompt())
                .user(message == null ? "" : message)
                .stream()
                .content();
    }

    /** 总纲：与 V2 一致的两级引导（system 定总纲 + 工具 description 定分工） */
    private String textSystemPrompt() {
        return "你是「派蒙」口吻的 AI 种草助手，商城导购，活泼俏皮、有点傲娇，但导购时认真负责。"
                + "你有两只手：涉及价格、库存、找商品时先调 searchProduct（实时数据）；"
                + "涉及使用体验、佩戴感受、功能细节（如防水/续航/怎么连接）时先调 searchDocs（商品说明书+真实用户笔记）；"
                + "基于工具返回结果如实回答并注明来源（官方说明还是用户笔记）。"
                + "商品库里没有就坦诚说明，不要编造不存在的商品、参数或体验。";
    }

    /** 识图链路的 system prompt（V1 同款三要素：角色+任务+红线） */
    private String visionSystemPrompt() {
        return "你是「AI 种草助手」。用户会发来商品图片，请识别图片大概是什么商品（品类/外观/可能的商品类型），"
                + "并结合工具检索给出建议；识别不出就如实说明，不要编造。";
    }

    /** base64 解码：兼容带 data URL 前缀（data:image/png;base64,xxx）与纯 base64 两种输入 */
    private byte[] decodeBase64(String input) {
        String s = input.contains(",") ? input.substring(input.indexOf(',') + 1) : input;
        return java.util.Base64.getDecoder().decode(s.trim());
    }

    /**
     * 从 base64 内容推断 MIME（V1 识图模块踩过的坑：MIME 跟着真实格式走，
     * 声明成 image/png 但实际是 jpeg 会导致模型识别失败）
     */
    private String detectMime(String base64) {
        if (base64.startsWith("data:")) {
            // 已带 data URL 声明时以声明为准（更可靠）
            int comma = base64.indexOf(',');
            String meta = base64.substring(5, Math.max(comma, 5));
            if (meta.contains("png")) {
                return "image/png";
            }
            if (meta.contains("gif")) {
                return "image/gif";
            }
            if (meta.contains("webp")) {
                return "image/webp";
            }
            return "image/jpeg";
        }
        if (base64.startsWith("/9j/")) {
            return "image/jpeg";      // JPEG 的魔法字节
        }
        if (base64.startsWith("iVBOR")) {
            return "image/png";       // PNG 的魔法字节
        }
        return "image/jpeg";
    }
}
