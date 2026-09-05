package com.aimall.config;

import com.aimall.ai.tool.ProductSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置 —— AI 模块的"装配车间"：把大模型客户端装配成两个用途明确的 Bean。
 *
 * <h2>为什么是两个 ChatClient？</h2>
 * <pre>
 *   chatClient          文本链路（模型来自 yml 默认，即 deepseek-v4-flash-vision-exp）
 *                       带装备：defaultTools(searchProduct)
 *                       → AI 能"查商品库"再回答，不编造
 *
 *   visionChatClient    视觉链路（同一模型）
 *                       也带 searchProduct 工具：视觉模型同样支持 function calling，
 *                       识别商品后可按用户追问进一步调工具查库（"视觉模型支持调用tool"提交）
 * </pre>
 * ChatServiceImpl 按 req.hasImage() 分流注入，两链路当前唯一真实的差异是
 * 视觉链路按次覆盖 temperature=0.5（识图要稳，见 ChatServiceImpl 的 .options()）。
 * 若将来需要让两条链路挂不同的工具集 / 模型，直接改这里各自 defaultTools / 按次覆盖即可。
 *
 * <h2>Builder 从哪来？（0 基础常见疑问）</h2>
 * spring-ai-starter-model-openai 的自动装配根据 application.yml 的
 * spring.ai.openai.*（base-url/api-key/model/temperature）预配好一个 ChatClient.Builder，
 * 你只管注入参数使用——和 MyBatis-Spring-Boot 自动配 SqlSessionFactory 一个套路。
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 1 章（装配入门）、第 4.7 节（拆分原因）。</p>
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * 文本链路客户端：全局注册商品搜索工具（function calling）。
     *
     * <p>defaultTools(searchTool) 的含义：这个客户端发起的<b>每一次</b>对话都携带
     * searchProduct 工具——框架自动把 @Tool 注解方法转成 JSON Schema（"参数说明书"）
     * 随请求发给模型；模型说"我要调 searchProduct"时，框架在你的 JVM 里执行真实查库
     * 并把结果回传给模型（两轮 HTTP，教程第 4.5 节）。对调用方完全透明：
     * ChatServiceImpl 里那行 chatClient.prompt()...call() 一字未变。</p>
     *
     * @param builder     starter 自动装配的建造器（已带 yml 里的默认模型/温度/key）
     * @param searchTool  商品搜索工具（@Component，Spring 容器直接取）
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ProductSearchTool searchTool) {
        // defaultTools 接收 @Tool 注解对象，Spring AI 自动扫描注册
        return builder.defaultTools(searchTool).build();
    }

    /**
     * 视觉链路客户端：与文本链路一样挂载 searchProduct 工具。
     *
     * <p>视觉模型自身的 function calling 支持良好，识别商品后可继续调工具查库。
     * 识图调用时 ChatServiceImpl 用 .options(...) 按次覆盖 temperature=0.5 让识别更稳；
     * 当前 model 与 yml 默认相同，温度才是与文本链路的真实差异。</p>
     */
    @Bean
    public ChatClient visionChatClient(ChatClient.Builder builder, ProductSearchTool searchTool) {
        return builder.defaultTools(searchTool).build();
    }
}
