package com.aimall.ai.controller;

import com.aimall.ai.dto.ChatRequest;
import com.aimall.ai.dto.ConversationVO;
import com.aimall.ai.dto.MessageVO;
import com.aimall.ai.service.ChatService;
import com.aimall.common.api.R;
import com.aimall.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 问答接口 —— AI 模块的 HTTP 门户，共 5 个接口：会话管理 3 个 + 问答 2 个。
 *
 * <h2>与前端 chatApi 一一对应（frontend/src/api/index.js）</h2>
 * <pre>
 *   POST   /api/v1/chat/conversations            → chatApi.createConversation  点"新会话"
 *   GET    /api/v1/chat/conversations            → chatApi.conversations       左侧会话栏
 *   GET    /api/v1/chat/conversations/{id}/messages → chatApi.messages         切会话回显
 *   POST   /api/v1/chat                          → chatApi.send               一次给全结果（前端保留但不主用）
 *   POST   /api/v1/chat/stream                   → chatApi.sendStream         带图与纯文字统一走（SSE 流式）
 * </pre>
 *
 * <h2>0 基础须知</h2>
 * <ul>
 *   <li>全部接口要求登录：Sa-Token 拦截 /api/**（SaTokenConfig），登录注册在白名单；</li>
 *   <li>返回值统一 R&lt;T&gt; = {code, msg, data}，code=200 成功；
 *       唯独 /stream 返回 Flux&lt;String&gt;（SSE 流），不走 R 包装——流一旦开始，
 *       HTTP 状态码已发出，后续信息只能以流内容表达（教程第 6 章）；</li>
 *   <li>Controller 一行业务逻辑都没有：校验/会话/调模型/落库全在 ChatService。
 *       分层纪律：Controller 只做"HTTP 协议 ↔ 业务调用"的翻译。</li>
 * </ul>
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 6 章（流式）、第 7 章（会话接口）、第 8 章。</p>
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatRestController {

    /** 依赖接口而非实现类：换 AI 实现（V2 加 RAG 等）Controller 不动（依赖倒置） */
    private final ChatService chatService;
    /** V3：AI 接口限流（Redis 固定窗口，可降级） */
    private final com.aimall.common.redis.RedisOps redisOps;

    /**
     * 新建空会话（前端传"新会话"占位标题，首条消息后由后端自动改名）。
     *
     * <p>body 允许整体缺失（required=false）：前端"新会话"按钮可能什么都不带，
     * req 为 null 时 title 传 null，Service 兜底占位标题。</p>
     */
    @PostMapping("/conversations")
    public R<ConversationVO> createConversation(@RequestBody(required = false) ChatTitleRequest req) {
        return R.ok(chatService.createConversation(req == null ? null : req.title()));
    }

    /** 当前用户的会话列表（新→旧），左侧会话栏展示用 */
    @GetMapping("/conversations")
    public R<List<ConversationVO>> listConversations() {
        return R.ok(chatService.listConversations());
    }

    /** 拉取某个会话的全部历史消息（旧→新），切会话时回显；Service 层做归属校验防越权 */
    @GetMapping("/conversations/{id}/messages")
    public R<List<MessageVO>> listMessages(@PathVariable Long id) {
        return R.ok(chatService.listMessages(id));
    }

    /**
     * 普通问答（非流式，返回完整回答）。
     *
     * <p>前端约定：带图与纯文字都走 /stream（流式）；本接口保留作为"一次要全量结果"的
     * 备选（测试、非流式调用方），与 stream() 共用同一套分流逻辑。
     * @Valid 触发 Bean Validation，但 message 的"与 image 二选一"校验是跨字段规则，
     * 在 Service.validate() 里做。</p>
     */
    @PostMapping
    public R<String> chat(@RequestBody @Valid ChatRequest req) {
        checkRateLimit();
        return R.ok(chatService.chat(req));
    }

    /**
     * SSE 流式问答：text/event-stream，逐字下发（HTTP/1.1 + SSE）。
     *
     * <p>两个关键点：① produces 声明响应是 SSE，Spring MVC 会把 Flux 的每个元素
     * 自动包装成 "data:xxx\n\n" 事件（无需引入 WebFlux）；② 返回 Flux&lt;String&gt;
     * 而非 R&lt;String&gt;：流式响应不走统一 JSON 包装，正文就是事件流本身。
     * 前端 fetch + ReadableStream 手动解析（教程第 6.5 节）。</p>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody @Valid ChatRequest req) {
        checkRateLimit();
        return chatService.stream(req);
    }

    /**
     * ★ V3：AI 接口限流（每用户 10 次/分钟）。
     *
     * <p>为什么 AI 接口必须限流而普通接口可以宽容：</p>
     * <ul>
     *   <li><b>按 token 计费</b>：一次问答真实花钱，被脚本刷一晚就是真金白银的损失；</li>
     *   <li><b>耗时资源</b>：AI 调用秒级耗时，占住线程池，刷接口影响所有正常用户。</li>
     * </ul>
     * 实现是 Redis 固定窗口计数（RedisOps.allow）——Redis 不可用时<b>放行</b>：
     * 限流组件绝不能反过来成为可用性瓶颈（拒绝服务比多花点钱更糟）。
     */
    private void checkRateLimit() {
        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        boolean allowed = redisOps.allow("aimall:rate:chat:" + userId, 10, 60);
        if (!allowed) {
            throw new BusinessException(com.aimall.common.api.ResultCode.AI_SERVICE_ERROR,
                    "问得太频繁啦，休息一下再来～（每分钟 10 次）");
        }
    }

    /**
     * 新建会话的请求体。
     * 用 record（Java 17）：不可变数据载体，一行顶一个类；
     * title 可为 null——允许"裸点新会话"。
     */
    public record ChatTitleRequest(String title) {
    }
}
