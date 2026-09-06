package com.aimall.ai.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置（{@code aimall.rag.*}）。
 */
@Data
@ConfigurationProperties(prefix = "aimall.rag")
public class RagProperties {

    /** 总开关：关闭后 searchDocs 工具不返回内容（用于排查问题） */
    private boolean enabled = true;

    /**
     * embedding 实现：local（默认，零依赖哈希向量） / spring-ai（需另配 embedding 服务）
     */
    private String embedding = "local";

    /** 向量维度（local 实现按此值建桶；spring-ai 实现按此值裁剪/补齐） */
    private int dimension = 256;

    /** 最终拼进 prompt 的片段数（太多会挤爆上下文、拉高成本） */
    private int topK = 5;

    /** 每一路召回的候选数（粗筛，交给 RRF 融合后再截断到 topK） */
    private int candidateK = 20;

    /**
     * RRF 融合常数 k（默认 60）。
     * 作用：压低"排名靠前"的绝对优势，避免某一路的第一名碾压其他结果。
     * k 越小，排名越重要；k 越大，各名次差异越平滑。
     */
    private int rrfK = 60;

    /** 向量缓存有效期（秒）：默认 5 分钟，索引更新时也会主动失效 */
    private long vectorCacheSeconds = 300;

    /** 单片段最大字符数：防止异常长文本把上下文撑爆 */
    private int maxChunkChars = 800;
}
