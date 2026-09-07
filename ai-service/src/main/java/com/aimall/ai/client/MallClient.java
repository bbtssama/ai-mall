package com.aimall.ai.client;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;

/**
 * mall-app 的内部服务客户端（Spring 6 的**声明式 HTTP 接口**）。
 *
 * <h2>★ 为什么用 @HttpExchange 而不是 OpenFeign</h2>
 * <table>
 *   <tr><th></th><th>Spring 6 HTTP Interface</th><th>OpenFeign</th></tr>
 *   <tr><td>依赖</td><td><b>零额外依赖</b>（spring-web 自带）</td><td>需 spring-cloud-openfeign + BOM 版本对齐</td></tr>
 *   <tr><td>编程模型</td><td>接口 + 注解，动态代理</td><td>接口 + 注解，动态代理</td></tr>
 *   <tr><td>生态整合</td><td>与 RestClient/WebClient 天然一体</td><td>与注册中心/负载均衡深度整合</td></tr>
 * </table>
 * 本项目只做"基础微服务"（一个服务拆分），引入 Spring Cloud 全家桶（含版本对齐风险）不划算；
 * 等真正需要注册发现/负载均衡/熔断时再上 Spring Cloud OpenFeign + Nacos——
 * <b>接口写法几乎一样，迁移成本极低</b>。这也是"按需演进"原则在依赖选型上的体现。
 *
 * <p>底层由 {@code RestClientAdapter} + {@code RestClient} 生成代理（见 AiClientConfig）。</p>
 */
@HttpExchange(url = "${aimall.mall.base-url}")
public interface MallClient {

    /**
     * 回调 mall-app 搜索商品 —— AI 的 searchProduct 工具需要商品数据，
     * 但商品库归 mall-app 所有，本服务不持有（**服务边界**）。
     */
    @PostExchange("/internal/v1/products/search")
    ProductSearchResult searchProduct(@RequestBody Map<String, Object> query);

    /** 健康检查：启动时探测 mall-app 是否可达（不可达不阻断本服务启动——可降级） */
    @GetExchange("/actuator/health")
    Map<String, Object> health(@RequestParam(name = "unused", required = false) String unused);

    /** 商品搜索结果（本服务侧的契约 DTO——微服务间不共享代码库，各自持有同名契约） */
    record ProductSearchResult(List<ProductItem> items, long total) {
    }

    record ProductItem(Long id, String spuName, String subTitle, java.math.BigDecimal minPrice,
                       String mainImg, Integer categoryId, String categoryName) {
    }
}
