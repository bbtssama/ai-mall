package com.aimall.ai.rag;

/**
 * 向量化客户端抽象 —— 把文本变成向量（embedding）。
 *
 * <h2>★ 为什么要抽象：一个绕不开的现实约束</h2>
 * 本项目的大模型是 DeepSeek，而 <b>DeepSeek 官方不提供 embedding 接口</b>。
 * 要拿到真实语义向量，需要额外的 embedding 服务（OpenAI/本地 Ollama/硅基流动等）。
 *
 * <p>如果代码里硬依赖某个 embedding 服务，那么"没有配那个服务"的机器上
 * RAG 就整个跑不起来——违背本项目"任何环境都能启动"的硬约束。
 * 所以这里抽象出接口，提供两个实现：</p>
 *
 * <table>
 *   <tr><th>实现</th><th>何时用</th><th>语义能力</th></tr>
 *   <tr><td>{@code SpringAiEmbeddingClient}</td><td>配置了真实 embedding 服务时</td><td>强（真语义）</td></tr>
 *   <tr><td>{@code LocalHashEmbeddingClient}</td><td>默认，零依赖兜底</td><td>弱（字面相似）</td></tr>
 * </table>
 *
 * <h2>降级实现会不会让 RAG 变成摆设？</h2>
 * 不会，这正是 <b>Hybrid 检索</b>的价值所在：
 * 关键词通道（MySQL 全文索引）负责精确召回，向量通道负责模糊补充。
 * 即使向量通道用的是弱化的字面向量，整体召回仍由关键词通道托底；
 * 接入真实 embedding 模型后，语义召回能力会明显提升——
 * <b>换实现不改任何业务代码</b>。
 */
public interface EmbeddingClient {

    /**
     * 把文本向量化。
     *
     * @param text 输入文本（不会为 null）
     * @return 已 L2 归一化的向量（模长为 1，点积即余弦相似度）
     */
    float[] embed(String text);

    /** 向量维度（同一实现必须恒定，否则余弦计算会出错） */
    int dimension();

    /** 实现标识，用于日志排查（local / spring-ai） */
    String name();
}
