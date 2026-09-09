package com.aimall.content.service.impl;

import com.aimall.ai.rag.RagRetrievalService;
import com.aimall.ai.rag.RetrievedChunk;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.content.dto.NoteGenerateRequest;
import com.aimall.goods.bean.Product;
import com.aimall.goods.mapper.ProductMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 种草文案生成 —— 「给素材 → 出草稿」的写作助手。
 *
 * <h2>与导购问答的本质区别</h2>
 * 导购是<b>检索+回答</b>（事实问答），文案生成是<b>创作</b>（写作任务）。
 * 创作任务的核心是"给足素材、限定风格"：
 * <ul>
 *   <li>素材：用户 brief + 商品信息 + <b>RAG 检索到的真实用户反馈</b>（V2 亮点：
 *       生成的文案能引用真实体验，不是凭空编的"绝绝子"）；</li>
 *   <li>风格：种草笔记的文体特征（第一人称、场景化、有细节、不硬广）写进 prompt；</li>
 *   <li>红线：<b>只生成草稿绝不自动发布</b>——AI 不直写业务数据（设计文档 5.6），
 *       用户编辑后手动走发布+审核流程。</li>
 * </ul>
 *
 * <h2>为什么让 RAG 参与文案生成（设计巧思）</h2>
 * 普通文案生成只喂商品参数 → 产出干巴巴的说明书复读。
 * 本实现先检索"这款商品的用户笔记片段"作为参考素材 →
 * 生成的文案带真实使用细节（"通勤地铁上开降噪，世界瞬间安静"）。
 * 这形成了内容飞轮：<b>UGC 笔记 → RAG 语料 → 帮新用户写出更好的笔记 → 更多 UGC</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteGenerateService {

    private final ChatClient chatClient;
    private final ProductMapper productMapper;
    private final RagRetrievalService ragRetrievalService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> generate(NoteGenerateRequest req) {
        // 1. 组装素材：商品信息（可选）+ RAG 检索的真实用户反馈
        String productContext = "";
        if (req.getProductId() != null) {
            Product p = productMapper.selectById(req.getProductId());
            if (p != null) {
                productContext = "商品：" + p.getSpuName()
                        + (p.getSubTitle() != null ? "（" + p.getSubTitle() + "）" : "")
                        + "\n官方介绍：" + abbreviate(p.getDetail(), 300);
            }
        }

        String ugcContext = "";
        try {
            List<RetrievedChunk> chunks = ragRetrievalService.retrieve(req.getBrief());
            if (!chunks.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(chunks.size(), 3); i++) {
                    sb.append("- ").append(abbreviate(chunks.get(i).content(), 150)).append('\n');
                }
                ugcContext = "其他用户的真实体验（可参考细节，但别照抄）：\n" + sb;
            }
        } catch (Exception e) {
            log.warn("文案生成检索语料失败（不影响生成）: {}", e.getMessage());
        }

        // 2. 创作 prompt：文体特征显式化
        //    ★ JSON 输出规范（bug 修复补充）：模型偶发在 content 里写未转义的半角双引号
        //    （如 "小玩具"）——整串 JSON 立即非法。这里三重约束：禁半角引号用「」、
        //    自检可解析、只输出一个 JSON。即便如此模型仍可能笔误，解析端另有兜底（见 parseDraft）。
        String system = "你是小红书风格的种草笔记写手。写作要求：\n"
                + "1. 第一人称、场景化开头（通勤/约会/宿舍等真实场景）\n"
                + "2. 有具体细节（用了多久、什么感受、和什么对比过），不写空话\n"
                + "3. 语气" + (req.getTone() == null ? "真诚分享" : req.getTone()) + "，不硬广不浮夸\n"
                + "4. 只输出一个 JSON 对象（不加任何说明文字、不用 markdown 代码块）：\n"
                + "{\"title\":\"标题(20字内，可带1个emoji)\",\"content\":\"正文(200-400字，分段)\",\"tags\":[\"3-5个标签\"]}\n"
                + "5. JSON 格式红线：content 里禁止出现未转义的英文双引号，"
                + "需要强调的词一律用中文引号「」，换行用 \\n 转义；输出前自检 JSON 必须能被解析";

        String user = "请根据以下素材写一篇种草笔记草稿：\n"
                + (productContext.isEmpty() ? "" : productContext + "\n")
                + (ugcContext.isEmpty() ? "" : ugcContext + "\n")
                + "用户想法：" + req.getBrief();

        String raw;
        try {
            raw = chatClient.prompt()
                    .system(system)
                    .user(user)
                    .options(org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .temperature(0.8).build())   // 创作任务给高温度（对比审核的 0.1）
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 文案生成失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "文案生成失败，请稍后再试");
        }

        // 3. 解析为结构化对象（★ bug 修复：不再把 JSON 字符串丢给前端 parse）
        //    早期返回 {"draft":"<JSON文本>"}，前端 JSON.parse 一失败就把整串 JSON
        //    填进正文框。现在后端解析到"解析不了就当纯文本"的终态，前端拿到的永远是
        //    {title, content, tags} 结构化对象——脏活全在服务端收口。
        return Map.of(
                "draft", parseDraft(raw),
                "editable", true);   // 提醒前端：这是草稿，需用户编辑确认
    }

    /**
     * 模型输出 → {title, content, tags} 结构化草稿。
     *
     * <p>解析阶梯（每一级失败都降级而不是抛错——草稿生成是"尽力而为"型功能）：</p>
     * <ol>
     *   <li>剥掉 markdown 代码围栏（```json ... ```）与首尾说明文字，截取第一个 { 到最后一个 }；</li>
     *   <li>Jackson 严格解析——一次成活则字段逐项校验/类型收口；</li>
     *   <li>失败 → 尝试"引号修复"：content 内未转义的半角引号是模型最高频笔误，
     *       按 JSON 结构位置启发式修复后再解析一次；</li>
     *   <li>仍失败 → 整段输出当纯文本正文（title 留空），用户自己改——
     *       好过把一串 JSON 糊在正文里。</li>
     * </ol>
     */
    private Map<String, Object> parseDraft(String raw) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("title", "");
        fallback.put("content", "");
        fallback.put("tags", new ArrayList<String>());
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        // ① 截取 JSON 主体（剥围栏/说明文字）
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            fallback.put("content", raw.trim());
            return fallback;
        }
        String json = raw.substring(start, end + 1);

        // ② 严格解析
        Map<String, Object> parsed = tryParse(json);
        if (parsed != null) {
            return parsed;
        }

        // ③ 半角引号修复：content 值里的 "xxx" 未转义是最高频笔误。
        //    启发式：非结构性位置（不紧跟 { [ , : 也不在行首键名上下文）的双引号成对包成中文引号
        String repaired = repairUnescapedQuotes(json);
        if (!repaired.equals(json)) {
            parsed = tryParse(repaired);
            if (parsed != null) {
                log.info("草稿 JSON 引号修复后解析成功");
                return parsed;
            }
        }

        // ④ 终极降级：整段当纯文本（title 留空，全文进 content）
        log.warn("草稿 JSON 解析失败，降级为纯文本（前120字）：{}", abbreviate(raw, 120));
        fallback.put("content", raw.trim());
        return fallback;
    }

    /** 严格解析 + 字段类型收口；失败返回 null（不抛异常） */
    private Map<String, Object> tryParse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                return null;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("title", node.path("title").asText(""));
            r.put("content", node.path("content").asText(""));
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = node.path("tags");
            if (tagsNode.isArray()) {
                tagsNode.forEach(t -> {
                    String s = t.asText("").trim();
                    if (!s.isEmpty()) {
                        tags.add(s);
                    }
                });
            }
            r.put("tags", tags);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 修复 content 值里未转义的半角双引号（模型高频笔误）。
     *
     * <p>启发式规则：一个双引号是"结构性"的，当且仅当它的前一个非空白字符是
     * {@code { [ , :} 之一，或后一个非空白字符是 {@code : , } ]} 之一。
     * 其余的双引号判定为"内容笔误"，成对替换为中文引号「」。不追求完美——
     * 修复后再走严格解析，失败就放弃（还有纯文本降级兜底）。</p>
     */
    private String repairUnescapedQuotes(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        boolean[] contentQuote = new boolean[json.length()];
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c != '"') {
                continue;
            }
            boolean structural = false;
            // 前看：上一个非空白字符
            for (int j = i - 1; j >= 0; j--) {
                char p = json.charAt(j);
                if (Character.isWhitespace(p)) {
                    continue;
                }
                if (p == '{' || p == '[' || p == ',' || p == ':') {
                    structural = true;
                }
                break;
            }
            // 后看：下一个非空白字符
            if (!structural) {
                for (int j = i + 1; j < json.length(); j++) {
                    char n = json.charAt(j);
                    if (Character.isWhitespace(n)) {
                        continue;
                    }
                    if (n == ':' || n == ',' || n == '}' || n == ']') {
                        structural = true;
                    }
                    break;
                }
            }
            if (!structural) {
                contentQuote[i] = true;
            }
        }
        // 成对替换内容引号（奇数个就只换到倒数第二个，最后孤引号保留——交给解析失败兜底）
        boolean open = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (contentQuote[i]) {
                sb.append(open ? '」' : '「');
                open = !open;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
