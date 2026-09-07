package com.aimall.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ai-service 启动类 —— V4 服务化后唯一独立出来的服务。
 *
 * <h2>★ 为什么只有它需要独立（其余留在 mall-app 单体）</h2>
 * <table>
 *   <tr><th>理由</th><th>说明</th></tr>
 *   <tr><td>耗时差异</td><td>AI 调用秒级~几十秒，普通接口毫秒级——放一起会拖垮 mall 的 Tomcat 线程池</td></tr>
 *   <tr><td>故障隔离</td><td>模型超时/限流/供应商挂了，不能让下单也不可用</td></tr>
 *   <tr><td>独立扩容</td><td>AI 是成本密集（token 计费），需独立扩缩</td></tr>
 *   <tr><td>密钥隔离</td><td>API Key 只存在于本服务，泄露半径最小</td></tr>
 *   <tr><td>模型可替换</td><td>换模型/供应商只改这一个服务</td></tr>
 * </table>
 *
 * <p>反过来——<b>为什么不拆用户/商品/订单</b>：它们事务耦合紧密（下单要扣库存+锁订单+清购物车），
 * 拆开要引入分布式事务（Seata/消息表），当前业务量不划算。<b>能讲清"不做什么"比堆五个服务高级得多。</b></p>
 *
 * <h2>本服务拥有什么</h2>
 * <ul>
 *   <li>ChatClient 双链路（文本 + 视觉）—— 与 V1 的 AiConfig 同构</li>
 *   <li>RAG：HybridRetriever + EmbeddingClient + t_knowledge_* 语料表（**AI 域资产**）</li>
 *   <li>工具：searchProduct 通过 HTTP 回调 mall-app（本服务不持有商品库）</li>
 * </ul>
 */
@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
