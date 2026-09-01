package com.aimall.config;

import com.aimall.ai.tool.ProductSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置 —— AI 模块的"装配车间"：把大模型客户端装配成两个用途明确的 Bean。
 *
 * <h2>为什么是两个 ChatClient？（本项目真实踩坑后拆的，教程案例四）</h2>
 * <pre>
 *   chatClient          文本链路（模型来自 yml 默认，即 deepseek-v4-flash-vision-exp）
 *                       带装备：defaultTools(searchProduct)
 *                       → AI 能"查商品库"再回答，不编造
 *
 *   visionChatClient    视觉链路（与文本链路同一模型，仅温度按次覆盖为 0.5）
 *                       空手：不带任何工具
 *                       → 识图专用；挂工具会让该模型胡言乱语（案例四）
 * </pre>
 * ChatServiceImpl 按 req.hasImage() 分流注入，互不污染。
 * 注意：当前两条链路用的是同一个模型（yml 默认 model 即 vision 模型），
 * 视觉链路的差异只有"不带工具 + 更低温度"；工具和温度诉求不同，分开装最清晰。
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
     * 视觉链路客户端：刻意"空手"，不带任何工具。
     *
     * <p>识图调用时用 .options(OpenAiChatOptions.builder().model(VISION_MODEL).temperature(0.5))
     * 按次覆盖。注意：当前 model 与 yml 默认相同，覆盖实际无效——真正生效的是 temperature(0.5)
     * （识别要稳，教程第 2 章）。温度/模型为什么不在 yml 里配死：同一个 yml 默认配置已被
     * 文本链路占用，视觉链路的差异参数（目前主要是温度）按次覆盖最清晰。</p>
     */
    @Bean
    public ChatClient visionChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
