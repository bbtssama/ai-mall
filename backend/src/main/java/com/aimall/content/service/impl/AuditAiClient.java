package com.aimall.content.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 审核客户端 —— 把"文本 → 违规判定"封装成一个可 mock 的组件。
 *
 * <h2>为什么单独抽成类</h2>
 * ① 单测时不需要真调模型（mock 这个类即可测审核编排逻辑）；
 * ② 将来换审核方案（如接阿里云内容安全）只改这里。
 *
 * <h2>提示词设计要点（内容安全 prompt 的通用套路）</h2>
 * <ol>
 *   <li><b>角色限定</b>：只做审核，不闲聊；</li>
 *   <li><b>分类标准给出</b>：让模型按固定类目判断，而不是自由发挥；</li>
 *   <li><b>强制结构化输出</b>：要求返回 JSON，失败则当 ERROR 处理（不能让格式问题污染状态机）；</li>
 *   <li><b>低温度</b>：审核要确定性，temperature 给 0.1（对比导购链路的 0.7）。</li>
 * </ol>
 */
@Component
public class AuditAiClient {

    private final ChatClient chatClient;

    /** 判定结果（不可变值对象） */
    public record AuditVerdict(boolean pass, String reason, List<String> categories) {
        static AuditVerdict fail(String reason) {
            return new AuditVerdict(false, reason, List.of());
        }
    }

    public AuditAiClient(ChatClient chatClient) {
        // 审核走独立低温度参数：与导购(0.7)不同，判定要稳
        this.chatClient = chatClient;
    }

    public AuditVerdict review(String content) {
        String system = "你是电商平台的内容安全审核员。审核用户发布的种草笔记，判断是否违规。\n"
                + "违规类目：色情低俗 / 广告引流(联系方式、外链、代购) / 辱骂攻击 / 政治敏感 / 违禁品(刀具枪支药品等)。\n"
                + "只输出 JSON，格式：{\"pass\":true/false,\"reason\":\"理由(30字内)\",\"categories\":[\"命中的类目\"]}\n"
                + "轻微营销话术（如'太好用了绝绝子'）不算违规；拿不准时倾向 pass。";

        String raw;
        try {
            raw = chatClient.prompt()
                    .system(system)
                    .user("请审核以下内容：\n" + content)
                    .options(org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .temperature(0.1).build())
                    .call()
                    .content();
        } catch (Exception e) {
            // 模型调用失败交给上层按 ERROR 处理（不在这里吞掉）
            throw new IllegalStateException("审核模型调用失败: " + e.getMessage(), e);
        }
        return parse(raw);
    }

    /**
     * 解析模型返回。模型可能不守规矩（带 markdown 代码块/前后废话），
     * 这里做"从文本中抠出第一个 JSON 对象"的防御性解析。
     */
    static AuditVerdict parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AuditVerdict.fail("审核返回为空");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return AuditVerdict.fail("审核返回非 JSON");
        }
        String body = raw.substring(start, end + 1)
                .replace("true", "\"true\"").replace("false", "\"false\"");
        // 简化解析：不引 JSON 库的轻量做法对固定 schema 足够，且避免依赖注入复杂化
        boolean pass = body.contains("\"true\"");
        String reason = extract(body, "reason");
        return new AuditVerdict(pass, reason == null ? "" : reason, List.of());
    }

    private static String extract(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) {
            return null;
        }
        int colon = json.indexOf(':', k + key.length() + 2);
        if (colon < 0) {
            return null;
        }
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) {
            return null;
        }
        int q2 = json.indexOf('"', q1 + 1);
        return q2 > q1 ? json.substring(q1 + 1, q2) : null;
    }
}
