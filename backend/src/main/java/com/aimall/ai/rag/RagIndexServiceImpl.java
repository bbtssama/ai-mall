package com.aimall.ai.rag;

import com.aimall.content.bean.KnowledgeChunk;
import com.aimall.content.bean.KnowledgeDoc;
import com.aimall.content.mapper.KnowledgeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 索引实现：切片 → 向量化 → 入库。
 *
 * <h2>切片策略：固定长度 + 重叠</h2>
 * 300 字一片、相邻重叠 50 字（见 {@link KnowledgeChunk} 常量与 V2 迁移注释）。
 *
 * <h2>为什么 upsert 是"先删旧切片再写新切片"</h2>
 * 笔记会被编辑、商品详情会被更新。如果只追加，同一来源会积累多版本切片，
 * 旧内容（可能已下架/已修正）继续被检索出来——脏语料比没语料更糟。
 * 所以：删旧 → 切新 → 写新，一个事务里完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIndexServiceImpl implements RagIndexService {

    private final KnowledgeMapper knowledgeMapper;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void index(String sourceType, Long refId, String title, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        // 1. upsert 文档头（uk(source_type, ref_id) 撞键走 UPDATE）
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setSourceType(sourceType);
        doc.setRefId(refId);
        doc.setTitle(title);
        doc.setStatus(KnowledgeDoc.STATUS_PENDING);
        doc.setChunkCount(0);
        knowledgeMapper.upsertDoc(doc);

        // 2. 清掉旧切片（同事务：索引更新是"替换"语义，不是"追加"）
        knowledgeMapper.deleteChunks(doc.getId());

        // 3. 切片 → 向量化 → 批量写入
        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<String> pieces = split(text);
        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i);
            KnowledgeChunk c = new KnowledgeChunk();
            c.setChunkIndex(i);
            c.setContent(piece);
            c.setEmbedding(toJson(embeddingClient.embed(piece)));
            c.setTokenCount(piece.length() / 2);  // 中文粗估：2 字 ≈ 1 token
            chunks.add(c);
        }
        if (!chunks.isEmpty()) {
            knowledgeMapper.batchInsertChunks(doc.getId(), chunks);
        }

        // 4. 文档状态置为已索引
        knowledgeMapper.updateDocStatus(doc.getId(), KnowledgeDoc.STATUS_INDEXED, chunks.size());
        log.info("RAG 索引完成 source={} ref={} chunks={} dim={}",
                sourceType, refId, chunks.size(), embeddingClient.dimension());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(String sourceType, Long refId) {
        KnowledgeDoc doc = knowledgeMapper.selectDocBySource(sourceType, refId);
        if (doc == null) {
            return;
        }
        knowledgeMapper.deleteChunks(doc.getId());
        knowledgeMapper.deleteDoc(doc.getId());
        log.info("RAG 索引移除 source={} ref={}", sourceType, refId);
    }

    // ------------------------------------------------------------------

    /**
     * 固定长度 + 重叠切片。
     *
     * <p>步长 = CHUNK_SIZE - CHUNK_OVERLAP（300-50=250）：
     * 每次前进 250 字，但每片取 300 字——相邻两片有 50 字的重叠，
     * 一句话即使被切断，也会完整出现在相邻的某一片里。</p>
     */
    private List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        String normalized = text.strip();
        if (normalized.length() <= KnowledgeChunk.CHUNK_SIZE) {
            pieces.add(normalized);
            return pieces;
        }
        int step = KnowledgeChunk.CHUNK_SIZE - KnowledgeChunk.CHUNK_OVERLAP;
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(start + KnowledgeChunk.CHUNK_SIZE, normalized.length());
            pieces.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
        }
        return pieces;
    }

    /** float[] → JSON 数组字符串（存 MEDIUMTEXT）。解析见 HybridRetriever。 */
    private String toJson(float[] vec) {
        try {
            StringBuilder sb = new StringBuilder(vec.length * 10);
            sb.append('[');
            for (int i = 0; i < vec.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(vec[i]);
            }
            sb.append(']');
            return sb.toString();
            // 不用 objectMapper.writeValueAsString：它对 float[] 会输出科学计数法变体，
            // 手拼格式稳定且更快（索引是大批量操作）。
        } catch (Exception e) {
            log.warn("向量序列化失败，该片将只参与关键词检索: {}", e.getMessage());
            return null;
        }
    }
}
