package com.aimall.content.service.impl;

import com.aimall.ai.rag.RagRetrievalService;
import com.aimall.ai.rag.RetrievedChunk;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.content.dto.NoteGenerateRequest;
import com.aimall.goods.bean.Product;
import com.aimall.goods.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

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
        String system = "你是小红书风格的种草笔记写手。写作要求：\n"
                + "1. 第一人称、场景化开头（通勤/约会/宿舍等真实场景）\n"
                + "2. 有具体细节（用了多久、什么感受、和什么对比过），不写空话\n"
                + "3. 语气" + (req.getTone() == null ? "真诚分享" : req.getTone()) + "，不硬广不浮夸\n"
                + "4. 只输出 JSON：{\"title\":\"标题(20字内，可带1个emoji)\","
                + "\"content\":\"正文(200-400字，分段)\",\"tags\":[\"3-5个标签\"]}";

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

        // 3. 防御性解析（模型可能带 markdown 围栏）
        return Map.of(
                "draft", parseOrRaw(raw),
                "editable", true);   // 提醒前端：这是草稿，需用户编辑确认
    }

    /** 从模型输出抠 JSON；抠不出就当纯文本正文返回（降级而不是失败） */
    private String parseOrRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{\"title\":\"\",\"content\":\"\",\"tags\":[]}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        // 非 JSON：包装成合法结构，content 放全文
        return "{\"title\":\"\",\"content\":" + quote(raw.trim()) + ",\"tags\":[]}";
    }

    private String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
