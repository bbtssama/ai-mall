package com.aimall.ai.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.ai.bean.ChatMessage;
import com.aimall.ai.bean.Conversation;
import com.aimall.ai.dto.ChatRequest;
import com.aimall.ai.dto.ConversationVO;
import com.aimall.ai.dto.MessageVO;
import com.aimall.ai.mapper.ConversationMapper;
import com.aimall.ai.mapper.MessageMapper;
import com.aimall.ai.service.ChatService;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * AI 问答实现 —— 整个 AI 模块的业务心脏。
 *
 * <h2>一次问答的完整旅程（建议对着 chat() 的 ①②③ 步骤读）</h2>
 * <pre>
 *   HTTP 请求（ChatRestController）
 *     → ① validate 校验（无图必须有文字）
 *     → ② resolveConversation 定会话（带 id 校验复用 / 不带自动新建）
 *     → ③ 分流：
 *         hasImage() → visionChatClient（视觉模型识图，第 5 章）
 *         无图       → chatClient（文本模型 + searchProduct 工具，第 2/4 章）
 *     → 回答成功 → saveMessage 落库（user + assistant 两条）
 *     → autoRenameIfDefault 会话自动命名（第 7 章）
 *   流式链路 stream() 同骨架，但换 .stream().content() + Reactor 钩子两头落库（第 6 章）
 * </pre>
 *
 * <h2>三条贯穿本类的铁律</h2>
 * <ul>
 *   <li><b>模型无记忆</b>：每次请求都带 toAiHistory 查出的全量历史（第 3 章）；</li>
 *   <li><b>模型异常是常态</b>：所有模型调用包 try-catch，统一转业务异常
 *       "AI 服务暂时不可用"，绝不把堆栈漏给前端（第 10 章 Q10）；</li>
 *   <li><b>AI 接口不豁免安全</b>：会话归属校验（防越权）与普通业务一样严格（第 7 章）。</li>
 * </ul>
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 8 章（逐段精读本章）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /**
     * 视觉识别模型（opencode 中转提供）。
     * 注意：当前 yml 默认 model 与它同为 deepseek-v4-flash-vision-exp，
     * 所以视觉链路的 .options().model(本常量) 是无效覆盖——两条链路实际用同一个模型，
     * 视觉链路的真实差异只有 temperature(0.5)（识别要稳）。
     * 若将来要让两条链路用不同模型，改 yml 默认 model 并删掉这里的按次覆盖即可。
     */
    private static final String VISION_MODEL = "deepseek-v4-flash-vision-exp";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    /** 文本链路：已全局注册 searchProduct 工具（见 AiConfig） */
    private final ChatClient chatClient;
    /** 视觉链路：不带工具，避免视觉模型收到 function calling */
    private final ChatClient visionChatClient;

    /**
     * 新建空会话（对应前端"新会话"按钮）。
     * 标题先用"新会话"占位，等首条消息后由 autoRenameIfDefault 改成问题前 16 字。
     */
    @Override
    public ConversationVO createConversation(String title) {
        Conversation c = new Conversation();
        // 归属人 = 当前登录用户（"我是谁"由 Sa-Token 认证层给出）
        c.setUserId(currentUserId());
        // 会话类型：当前只有通用聊天 CHAT
        c.setBizType(Conversation.BIZ_CHAT);
        // 空标题兜底为占位符——自动命名只认"新会话/图片识别"这两个占位值
        c.setTitle(StringUtils.hasText(title) ? title.trim() : "新会话");
        conversationMapper.insert(c);
        return toVO(c);
    }

    /** 会话列表（新→旧），左侧栏展示。只查元信息不含消息——消息点开时按需拉取，列表接口保持轻量 */
    @Override
    public List<ConversationVO> listConversations() {
        return conversationMapper.selectByUserId(currentUserId())
                .stream().map(this::toVO).toList();
    }

    /**
     * 历史消息回显（切会话时前端调用）。
     * 先过 ensureOwned 防越权：拿别人的会话 id 探测 → 统一 404"会话不存在"。
     * 注意这里只是读库回显，不会重新调大模型——AI 回答当初已落库（模型不保存任何东西）。
     */
    @Override
    public List<MessageVO> listMessages(Long conversationId) {
        ensureOwned(conversationId);
        return messageMapper.selectByConversationId(conversationId)
                .stream().map(this::toMsgVO).toList();
    }

    /**
     * 非流式问答（带图必走这里）。①校验 → ②定会话 → 分流调模型 → 落库 → 自动命名。
     * 每一步的"为什么"见方法内注释；与 stream() 共享全部私有方法。
     */
    @Override
    public String chat(ChatRequest req) {
        // ① 参数校验：无图时必须有问题文字；纯图片识别允许空文本（不合法直接抛业务异常，请求到此为止）
        validate(req);
        // ② 确定本次对话所属会话：请求带了 conversationId 就校验归属后复用；没带就自动新建一个会话
        Conversation conv = resolveConversation(req);
        String answer;
        try {
            if (req.hasImage()) {
                // 视觉链路：识别图片中的商品（多模态 UserMessage 走 messages()）
                // 注意用的是"空手"的 visionChatClient（视觉模型不挂工具，教程案例四）。
                // .options() 按次覆盖模型与温度：当前模型与 yml 默认相同（覆盖无效），
                // 真正的差异是温度给 0.5（低于文本链路 0.7，识图要稳）
                answer = visionChatClient.prompt()
                        .system(visionSystemPrompt())
                        .messages(toAiHistory(conv.getId()))        // 历史上下文（模型无记忆，每次都要带）
                        .messages(List.of(buildUserMessage(req)))   // 本次的"文字+图片"多模态消息
                        .options(OpenAiChatOptions.builder().model(VISION_MODEL).temperature(0.5).build())
                        .call()
                        .content();
            } else {
                // 文本链路：引导调用 searchProduct 工具按需检索
                // chatClient 在 AiConfig 里 defaultTools(searchProduct)——
                // 这里代码看起来"没提工具"，实际框架会自动完成两轮调用（教程第 4.5 节）
                answer = chatClient.prompt()
                        .system(textSystemPrompt())
                        .messages(toAiHistory(conv.getId()))
                        .user(req.getMessage())                     // 本次新问题单独追加，不与历史重复
                        .call()
                        .content();
            }
        } catch (Exception e) {
            // 模型侧异常（网络/限流/格式/超时）是常态：就地转业务异常，
            // 前端拿到统一 code=3001"AI 服务暂时不可用"，堆栈只进日志不进响应
            log.error("AI 问答失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 服务暂时不可用");
        }
        // ③ 问答成功后两条消息一起落库。
        //    注意：两种消息的"文字"都同时用于回显和下一轮上下文，区别只在图片——
        //    用户消息的 base64 图片仅供前端回显，不重复喂给模型（见 toAiHistory 的说明）。
        saveMessage(conv.getId(), ChatMessage.ROLE_USER, req.getMessage(), req.getImage());
        saveMessage(conv.getId(), ChatMessage.ROLE_ASSISTANT, answer, null);
        // 首条消息后自动命名会话（标题仍为默认值时）
        autoRenameIfDefault(conv, req);
        return answer;
    }

    /**
     * 流式问答（打字机效果，纯文字走这里）。
     * 与 chat() 同一套校验/会话逻辑，差别只在：.call() 换成 .stream()，落库从"事后一起存"
     * 变成"两头存"——订阅时存 user，流结束攒齐再存 assistant（断流不会存半截话）。
     */
    @Override
    public Flux<String> stream(ChatRequest req) {
        // 参数校验（与 chat() 同一条规则：无图时必须有问题文字）
        validate(req);
        // 确定所属会话（带 id 校验复用 / 不带自动新建），流式与非流式走同一套会话逻辑
        Conversation conv = resolveConversation(req);
        // 累积 AI 回答的完整文本：流式逐段吐字，落库必须等流结束后攒齐再存
        StringBuilder sb = new StringBuilder();
        if (req.hasImage()) {
            // 带图暂不支持流式：退化到非流式返回（前端对图片走 chat 而非 stream）
            // defer：把 chat(req) 推迟到"真正被订阅"那一刻才执行
            // （不包 defer 的话，组装返回值的瞬间就会同步阻塞跑完整个问答）
            // onErrorResume：HTTP 响应已按 200/event-stream 开始，错误只能以流内容表达
            return Flux.defer(() -> Flux.just(chat(req))).onErrorResume(e -> {
                log.error("AI 图片问答失败: {}", e.getMessage(), e);
                return Flux.just("[AI 服务暂时不可用]");
            });
        }
        return chatClient.prompt()
                .system(textSystemPrompt())
                .messages(toAiHistory(conv.getId()))
                .user(req.getMessage())
                .stream()                      // ← 与 chat() 唯一的差别：call() → stream()
                .content()                     // Flux<String>：逐段正文，不是完整文本
                .doOnSubscribe(s -> {
                    // 订阅即存用户消息（它本来就是完整的），并顺手做首条自动命名
                    saveMessage(conv.getId(), ChatMessage.ROLE_USER, req.getMessage(), null);
                    autoRenameIfDefault(conv, req);
                })
                .doOnNext(sb::append)          // 每个片段追加到 StringBuilder
                .doOnComplete(() -> saveMessage(conv.getId(), ChatMessage.ROLE_ASSISTANT, sb.toString(), null))
                // 流中途出错：把错误"说"给用户听（响应已开始，改不了状态码了）
                .onErrorResume(e -> {
                    log.error("AI 流式问答失败: {}", e.getMessage(), e);
                    return Flux.just("\n\n[AI 服务暂时不可用，请稍后再试]");
                });
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    /** 当前登录用户 id（Sa-Token 从请求的登录态解析，"我是谁"由认证层决定） */
    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 参数校验：无图时必须有问题文本；纯图片识别允许空文本。
     *
     * <p>为什么写在 Service 而不是 DTO 加 @NotBlank？因为规则是
     * "message 和 image 二选一"的<b>跨字段条件</b>，注解表达不了。
     * 这是个真实修复：最初加了 @NotBlank，用户发纯图直接被 400 拦死。</p>
     */
    private void validate(ChatRequest req) {
        if (!req.hasImage() && !StringUtils.hasText(req.getMessage())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "问题不能为空");
        }
    }

    /**
     * 会话归属校验（防越权）：查不到、或不是当前登录用户的，一律 404"会话不存在"。
     * 统一口径是有意的：不提示"这不是你的会话"，避免给攻击者"id 有效"的枚举确认信号。
     */
    private void ensureOwned(Long conversationId) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c == null || !c.getUserId().equals(currentUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
    }

    /**
     * 复用传入会话（校验归属）或自动新建（标题取问题前 20 字）。
     * 用户"不点新会话直接打字发送"的路径也在这里兜住：conversationId 为 null 就现场建。
     */
    private Conversation resolveConversation(ChatRequest req) {
        Long userId = currentUserId();
        if (req.getConversationId() == null) {
            // 自动新建：标题先取问题前 20 字占位（纯图时占位"图片识别"）
            Conversation c = new Conversation();
            c.setUserId(userId);
            c.setBizType(Conversation.BIZ_CHAT);
            c.setTitle(abbreviate(StringUtils.hasText(req.getMessage()) ? req.getMessage() : "图片识别", 20));
            conversationMapper.insert(c);
            return c;
        }
        // 复用前必须确认"这会话是你的"——AI 接口不豁免越权防护
        Conversation c = conversationMapper.selectById(req.getConversationId());
        if (c == null || !c.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return c;
    }

    /**
     * 历史消息转 Spring AI 消息数组（均为已完成的轮次，不与本次提问重复）。
     *
     * <p>这是"多轮对话"的转换枢纽（教程第 3 章）：
     * DB 的 t_message（role 刻意与 OpenAI 协议一致）→ user 发言转 UserMessage、
     * AI 发言转 AssistantMessage → 拼成 messages 数组随每次请求发给模型。
     * SQL 已按 created_at ASC, id ASC 排序（旧→新），对话剧本顺序才正确。</p>
     *
     * <p><b>这里刻意不带历史图片</b>：只取 content 文字，不把 extra_json 的 base64
     * 还原成 Media。历史图片仅供前端回显，不重复喂给模型——图片按尺寸计 token，
     * 每轮都带上几张的话成本会指数级上涨。
     * 代价：用户追问"图里这件还有别的颜色吗"时，模型已经看不到那张图，
     * 只能依据当时生成的文字回答来推断。</p>
     *
     * <p><b>怎么区分两个"消息"类</b>：实体已由 Message 改名为 ChatMessage，
     * 不再与 Spring AI 的 Message 接口重名，因此这里可以直接按名字分辨——
     * ChatMessage = 我们库里存的聊天记录，Message = 组装好发给模型的协议消息。</p>
     */
    private List<Message> toAiHistory(Long conversationId) {
        return messageMapper.selectByConversationId(conversationId).stream()
                .map(m -> (Message)
                        (ChatMessage.ROLE_USER.equals(m.getRole())
                                ? new UserMessage(m.getContent())        // 用户发言 → UserMessage
                                : new AssistantMessage(m.getContent()))) // AI 发言 → AssistantMessage
                .toList();
    }

    /**
     * 文本链路 system prompt：引导按需调用搜索工具，不编造商品。
     * 提示词三要素齐活（教程第 2 章）：角色（导购）+ 行为约束（先查工具再答）+ 红线（不许编造）。
     * 它与 @Tool 的 description 是"双保险"：一个告诉模型"何时调"，一个告诉模型"调完怎么答"。
     */
    private String textSystemPrompt() {
        return "你是「AI 种草助手」，商城导购。用户询问商品/价格/找某类商品时，"
                + "先调用 searchProduct 工具按需搜索，再基于返回结果如实回答（给名称、价格、卖点）。"
                + "商品库里没有就坦诚说明，不要编造不存在的商品或参数。";
    }

    /** 视觉链路 system prompt：识别图片中的商品（同样三要素：角色+任务+红线） */
    private String visionSystemPrompt() {
        return "你是「AI 种草助手」。用户会发来商品图片，请识别图片大概是什么商品（品类/外观/可能的商品类型），"
                + "并简要说明；不确定时如实说明，不要编造。";
    }

    /**
     * 组装用户消息：带图 → 多模态（文本+图片），经 messages() 传入；否则纯文本。
     *
     * <p>多模态三件套（教程第 5.4 节）：</p>
     * <ul>
     *   <li>Media = 消息携带的媒体附件（MIME 类型 + 数据源）；</li>
     *   <li>ByteArrayResource = Spring 的资源抽象，把 byte[] 包成数据源，
     *       匿名子类重写 getFilename() 是兼容部分网关"附件要有文件名"的小技巧；</li>
     *   <li>UserMessage.builder().text().media() = "文字 + 图片"一体组装，
     *       最终会被翻译成 OpenAI 协议的 content 数组 [{type:text},{type:image_url}]。</li>
     * </ul>
     *
     * <p><b>MIME 类型从图片字节自动嗅探</b>（见 detectImageMimeType）：
     * 早期这里硬编码 image/png，但前端 canvas 压缩后实际产出是 JPEG，
     * 属于"声明的格式 ≠ 真实格式"——只因为网关会自行嗅探字节才一直没暴露。
     * 现在按文件头字节识别真实格式，png/jpeg/gif/webp/bmp 都能如实告诉模型。</p>
     */
    private UserMessage buildUserMessage(ChatRequest req) {
        if (!req.hasImage()) {
            return new UserMessage(req.getMessage());
        }
        // 识别任务总得给模型一句指令：用户没写文字时给默认指令
        String text = StringUtils.hasText(req.getMessage()) ? req.getMessage() : "请识别这张图片，它大概是什么商品？";
        byte[] bytes = decodeImage(req.getImage());
        // 以数据本身的格式为准，不采信前端声明（前端压成 JPEG 却按 png 上传的坑）
        String mimeType = detectImageMimeType(bytes);
        String filename = "product." + imageExtension(mimeType);
        Media media = new Media(MimeTypeUtils.parseMimeType(mimeType),
                new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                });
        return UserMessage.builder().text(text).media(List.of(media)).build();
    }

    /**
     * 按文件头字节（magic number）嗅探图片真实格式——图片格式就写在头几个字节里，像身份证。
     *
     * <p>为什么不用前端 dataURL 前缀（data:image/jpeg;base64,）里写的类型？
     * 两个原因：① 前端也可能直接发裸 base64，压根没有前缀；
     * ② 前缀是前端拼的，理论上可以和真实内容不符（本项目就踩过：压成 JPEG 却声明 png）。
     * 文件头字节来自数据本身，最权威，且不依赖调用方守规矩。</p>
     *
     * <p>覆盖常规业务常见的 5 种：PNG / JPEG / GIF / WEBP / BMP。
     * 认不出来时兜底 image/jpeg（前端 canvas 压缩的默认产出）。</p>
     *
     * @param bytes 解码后的图片字节
     * @return MIME 类型，如 image/png；永不返回 null
     */
    private String detectImageMimeType(byte[] bytes) {
        // 任何真实图片都远大于 12 字节，比这还短说明数据本身有问题，直接兜底
        if (bytes == null || bytes.length < 12) {
            return "image/jpeg";
        }
        // PNG：89 50 4E 47，即 "\x89PNG"
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        // JPEG：FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // GIF：47 49 46 38，即 "GIF8"（GIF87a 与 GIF89a 都是这个头）
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return "image/gif";
        }
        // WEBP：前 4 字节 "RIFF"，第 8-11 字节 "WEBP"
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        // BMP：42 4D，即 "BM"
        if (bytes[0] == 'B' && bytes[1] == 'M') {
            return "image/bmp";
        }
        return "image/jpeg";   // 未知格式兜底
    }

    /**
     * MIME → 文件扩展名，用于给附件起文件名。
     * 网关只关心"附件有个名字"，并不校验它和真实内容是否一致，所以简单映射即可。
     */
    private String imageExtension(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            default -> "jpg";   // image/jpeg 及一切未知格式
        };
    }

    /**
     * 解析 base64（兼容 data:image/...;base64,xxx 或纯 base64）。
     * 前端压缩后发来的是 dataURL 格式，剥掉 "data:...;base64," 前缀才是纯 base64 数据；
     * 二次尝试去掉空白符是防御性兜底（有些剪贴板/传输环节会混入换行）。
     */
    private byte[] decodeImage(String image) {
        String data = image;
        int idx = image.indexOf("base64,");
        if (idx >= 0) {
            data = image.substring(idx + 7);
        }
        try {
            return Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            return Base64.getDecoder().decode(data.replaceAll("\\s", ""));
        }
    }

    /**
     * 落库一条消息（聊天"只增"语义的唯一入口）。
     * 调用时序：chat() 在回答成功后存两条；stream() 两头存（订阅存 user / 完成存 assistant）——
     * 时序差异的原因见 ChatService 接口注释与教程第 7 章。
     *
     * <p>图片存 extra_json：V1 的简化方案（省一个对象存储依赖）。
     * 列已从 TEXT 扩为 MEDIUMTEXT——真实大图 base64 曾撑爆 64KB 导致 500，
     * 完整复盘见教程第 9 章案例一。V2 演进方向：传对象存储只存 URL。</p>
     */
    private void saveMessage(Long conversationId, String role, String content, String image) {
        ChatMessage m = new ChatMessage();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        // V1 简化：图片 base64 直接存 extra_json（用户消息）
        if (image != null && !image.isBlank()) {
            m.setExtraJson(image);
        }
        messageMapper.insert(m);
    }

    /**
     * 若会话标题仍是默认占位（新会话/图片识别/空），用首条用户消息自动命名。
     * 两个设计决策（教程第 7 章）：① 后端命名而非前端（多端一致，不用多发一次请求）；
     * ② 只认占位值才改——未来用户手动改过标题绝不能被覆盖。
     */
    private void autoRenameIfDefault(Conversation conv, ChatRequest req) {
        String cur = conv.getTitle();
        if (cur == null || cur.isBlank() || "新会话".equals(cur) || "图片识别".equals(cur)) {
            String title = StringUtils.hasText(req.getMessage())
                    ? abbreviate(req.getMessage(), 16)
                    : "图片识别";
            conversationMapper.updateTitle(conv.getId(), title);
            // 同步改内存对象：同一次请求里后续若再用 conv.getTitle()，拿到的也是新标题
            conv.setTitle(title);
        }
    }

    /** 截断文本用作会话标题：换行替换为空格、超长截断加省略号 */
    private String abbreviate(String text, int max) {
        if (text == null) {
            return "新会话";
        }
        String trimmed = text.trim().replace('\n', ' ');
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "...";
    }

    /** 实体 → 会话 VO 的映射（存储结构变化时不影响前端契约，教程 MessageVO 类注释） */
    private ConversationVO toVO(Conversation c) {
        ConversationVO vo = new ConversationVO();
        vo.setId(c.getId());
        vo.setBizType(c.getBizType());
        vo.setTitle(c.getTitle());
        vo.setCreatedAt(c.getCreatedAt() == null ? LocalDateTime.now() : c.getCreatedAt());
        return vo;
    }

    /** 实体 → 消息 VO 的映射。注意 extraJson → image 的语义化改名：前端拿到即懂"这是附图" */
    private MessageVO toMsgVO(ChatMessage m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setImage(m.getExtraJson());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}
