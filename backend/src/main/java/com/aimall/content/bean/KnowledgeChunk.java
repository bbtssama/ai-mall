package com.aimall.content.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 知识切片（t_knowledge_chunk）
 *
 * <h2>为什么要切片（Chunking）</h2>
 * 一篇笔记几百上千字，整篇喂给模型有三个问题：
 * <ol>
 *   <li><b>不精准</b>：用户只问其中一个细节，整篇塞进去，模型要在长文里"找"；</li>
 *   <li><b>成本高</b>：整篇入上下文 = 每次多花几千 token；</li>
 *   <li><b>检索粒度粗</b>：相似度是按整篇算的，细节问题匹配不上。</li>
 * </ol>
 * 切片后：按片段检索，只把命中的 3~5 个片段（几百字）拼进 prompt。
 *
 * <h2>本项目的切片策略</h2>
 * <b>固定长度 + 重叠（overlap）</b>：每片 300 字、相邻片重叠 50 字。
 * 重叠是为了避免"一句话被从中间切断"——切断的片段两边都语义不全，检索与生成都会变差。
 * 更优的做法是语义切分/父子切片，但固定长度+重叠是性价比最高的起步方案，
 * 且本项目语料以短笔记为主，切分损失很小。
 */
@Data
public class KnowledgeChunk {

    private Long id;
    private Long docId;
    private Integer chunkIndex;
    private String content;
    /** 向量，JSON 数组字符串（为空表示该切片尚未生成向量，检索时只参与关键词通道） */
    private String embedding;
    private Integer tokenCount;
    private LocalDateTime createdAt;

    /** 切片长度（字） */
    public static final int CHUNK_SIZE = 300;
    /** 相邻切片重叠长度（字）：防止语义被切断 */
    public static final int CHUNK_OVERLAP = 50;
}
