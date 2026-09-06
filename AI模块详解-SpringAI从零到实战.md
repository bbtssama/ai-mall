# Spring AI 从零到实战：跟着 AI 种草商城项目学会大模型应用开发

> **写给谁**：会 Spring Boot + MyBatis + Maven + MySQL 的 Java 后端学习者；**Spring AI 与大模型应用开发均为 0 基础**（token、temperature 这些词也一并在本教程内解释）。
>
> **跟着哪个项目学**：`ai-mall`（AI 种草商城）——一个真实的求职项目，本文所有代码都来自它，可以边读教程边对着源码看。
>
> **怎么用本教程**：
> 1. **按顺序读**。每一章只引入少量新概念，学完一章你能跑通对应的最小示例，再进下一章。不要跳章——后面每一章都假设你已掌握前面的概念。
> 2. **每章末尾有「动手作业」**。真正动手做一遍，比读十遍都有效。
> 3. **附录有「学习自查清单」**。全部能打勾，就说明这门课学明白了，面试也够用。

## 全书地图

| 章 | 主题 | 你将获得 |
|---|---|---|
| 0 | 破除恐惧：大模型 API 就是个 HTTP 接口 | 看懂 chat/completions 请求与响应的每一个字段 |
| 1 | 最小可用：跑通第一个 ChatClient | 5 分钟让 Java 代码获得 AI 能力 |
| 2 | 让 AI 更可控：system prompt 与 temperature | 知道怎么给 AI 立人设、控发散 |
| 3 | 多轮对话：给没有记忆的模型装上记忆 | 理解 messages 数组 + 用数据库存历史 |
| 4 | 函数调用 Function Calling（重点） | 让 AI 会"查数据库"，而不是编数据 |
| 5 | 视觉多模态：让 AI 看图 | 一张图从浏览器到模型的完整旅程 |
| 6 | 流式输出 SSE：打字机效果 | 后端 Flux + 前端 fetch 全链路 |
| 7 | 会话持久化与产品细节 | 两张表、防越权、自动命名 |
| 8 | 完整源码精读 | 串讲 ChatServiceImpl 每一段 |
| 9 | 踩坑复盘 | 4 个真实事故的排查全过程 |
| 10 | 面试问答速记 | 12 个高频问题与答法 |

---

# 第 0 章 破除恐惧：大模型 API 就是一个 HTTP 接口

> 📚 **本章可跳转词条**：[HTTP 结构](AI模块详解-SpringAI从零到实战【知识词典】.md#http-basics) · [LLM 是什么](AI模块详解-SpringAI从零到实战【知识词典】.md#llm) · [Token](AI模块详解-SpringAI从零到实战【知识词典】.md#token) · [上下文窗口](AI模块详解-SpringAI从零到实战【知识词典】.md#context-window) · [temperature](AI模块详解-SpringAI从零到实战【知识词典】.md#temperature) · [OpenAI 兼容协议](AI模块详解-SpringAI从零到实战【知识词典】.md#openai-protocol) · [状态码](AI模块详解-SpringAI从零到实战【知识词典】.md#http-status)

> **本章你将学会**：用 HTTP 的视角理解大模型服务；看懂请求和响应报文的每一个字段；建立贯穿全书的心智模型。

## 0.1 它和你接过的其他第三方接口没有本质区别

你大概率接过这些第三方接口：短信验证码、微信支付、对象存储。它们的共同套路是：

```
你（客户端）── 带「鉴权 + 参数」发 HTTP 请求 ──→ 对方服务器
你（客户端）←── 返回 JSON 结果 ──────────────── 对方服务器
```

大模型 API（ChatGPT / DeepSeek / 通义……）**一模一样**，只是"对方服务器干的活"从"发一条短信"变成了"根据你给的文字生成一段回答"。你在 Java 里调它，本质就是发一个带 JSON 的 POST 请求。

所以第一步先破除神秘感：**没有什么黑魔法，就是一个慢一点、贵一点、返回内容不确定的 HTTP 接口**。

## 0.2 手撕一次请求：/chat/completions

主流大模型厂商都兼容 [OpenAI 定义的接口格式](AI模块详解-SpringAI从零到实战【知识词典】.md#openai-protocol)（行业事实标准），核心端点是 `POST /v1/chat/completions`。下面是一次真实请求（以 DeepSeek 官方为例）：

```http
POST https://api.deepseek.com/v1/chat/completions
Authorization: Bearer sk-xxxxxxxx          ← 鉴权：你的 API Key，像密码一样保管
Content-Type: application/json

{
  "model": "deepseek-chat",                ← 用哪个模型（同一服务商有多个模型可选）
  "messages": [                            ← 对话内容：一个消息数组（核心字段！）
    { "role": "system",    "content": "你是商城导购" },
    { "role": "user",      "content": "有什么降噪耳机？" }
  ],
  "temperature": 0.7                       ← 回答的发散程度（0.2 保守 ~ 1.5 放飞）
}
```

逐个字段讲：

- **`Authorization` 头**：API Key 鉴权。Key 就是你的"付费凭证 + 身份证"，泄露=别人刷你的钱。所以项目里用环境变量装它，不硬编码。
- **`model`**：模型名是字符串配置项。同一家服务商通常有"便宜快的"和"聪明贵的"多个模型，还有能看图的"视觉模型"。
- **`messages`**：**全书最重要的字段**。它是个数组，每个元素一条消息，一条消息 = `role`（谁说的）+ `content`（说了什么）。role 有三种基本角色：
  - `system`：系统设定。你给 AI 下达的"人设与工作规则"，用户看不到。比如"你是商城导购，不许编造商品"。
  - `user`：用户的发言。
  - `assistant`：AI 之前的回答（多轮对话时把历史回答也放进来，第 3 章详讲）。
- **`temperature`**：采样温度。低→AI 挑最稳妥的说法，回答稳定但呆板；高→AI 更愿意冒险，回答发散有创意但可能跑偏。它是"发散度旋钮"，**不是**"准确率旋钮"。

## 0.3 手撕一次响应：choices 是什么鬼？

服务端返回的 JSON 大概长这样（加了注释的教学版，真实响应无注释）：

```jsonc
{
  "id": "cmpl-9a8b7c",
  "choices": [                                        // ← 候选回答列表
    {
      "message": { "role": "assistant", "content": "推荐 AirSound Pro 真无线降噪耳机……" },
      "finish_reason": "stop"                         // stop=正常说完；length=达到长度上限被截断
    }
  ],
  "usage": {                                          // 本次消耗量（计费依据）
    "prompt_tokens": 52,                              // 输入消耗
    "completion_tokens": 89                           // 输出消耗
  }
}
```

**初学者最容易懵的是 `choices` 为什么是个数组。** 答案：这个 API 支持**一次生成多个候选回答**——请求里加 `"n": 3`，就返回 3 个备选，供你的程序挑选或让用户选。实际业务里 99% 的情况只要一个答案（默认 `n=1`），所以你永远只见 `choices[0]`。看到 `choices` 不必再困惑：它就是个"几乎总长度为 1 的候选列表"。

另外两个字段排查问题时特别有用：

- `finish_reason: "length"` → 回答不是模型"不想说了"，而是撞到 `max_tokens` 上限被**硬截断**了。回答莫名缺尾巴时先查它。
- `usage` → 账单依据。大模型按 **token** 计费，token 是模型处理文本的最小计量单位（详见 0.5 术语表）。

> 上面只是**响应**。想看**请求**长什么样，以及多模态、函数调用两轮、SSE 流式这几种场景的完整报文，直接翻 **附录 A：四种场景的真实报文速查**。

## 0.4 贯穿全书的心智模型（背下来）

**模型没有记忆，没有你的数据库，它每次都是"第一次见你"。**

HTTP 接口本身无状态很好理解，但大模型 API 把这点推向极致：

1. 你这次请求里不发对话历史，模型就完全不知道之前聊过什么；
2. 模型也**看不到**你数据库里的商品、订单——它训练时见过互联网上的公开文本，但不知道你店里今天卖多少钱；
3. 想让它"记住上下文"或"知道你的业务数据"，全部要靠**你在每次请求的 `messages` 里喂给它**。

后面每一章其实都在解决这个心智模型带来的问题：

| 你想要的 | 模型的现实 | 解决方案（章节） |
|---|---|---|
| 多轮对话 | 模型无记忆 | 每次把历史消息一起发（第 3 章） |
| AI 知道商品数据 | 模型不知道你的库 | 函数调用，让它现查（第 4 章） |
| AI 能看图 | 纯文本模型看不了 | 视觉模型 + 多模态消息（第 5 章） |
| 回答快点出来 | 生成完整回答很慢 | 流式输出（第 6 章） |

## 0.5 高频术语速查表（全书通用）

| 术语 | 大白话 |
|---|---|
| **[token](AI模块详解-SpringAI从零到实战【知识词典】.md#token)** | 模型处理文本的最小计量单位（≈1 个汉字 1~2 个 token，≈1 个英文单词 1.3 个 token）。**计费、限长、速度全按 token 算**。"Token 爆炸" = prompt 太长 → 又贵又容易顶到上限 |
| **[上下文窗口](AI模块详解-SpringAI从零到实战【知识词典】.md#context-window)** | 模型单次能"看见"的最大 token 数（如 128K）。装不下就丢信息，所以历史消息、业务数据都不能无限塞 |
| **[system / user / assistant](AI模块详解-SpringAI从零到实战【知识词典】.md#system-prompt)** | messages 里的三种角色：system=系统设定（人设与规则）；user=用户发言；assistant=模型此前的回答。函数调用时还有第四种 role=`tool`（工具执行结果，见第 4 章） |
| **[temperature（温度）](AI模块详解-SpringAI从零到实战【知识词典】.md#temperature)** | 回答的发散程度：低→稳定克制（识别/抽取类任务）；高→发散有创意（导购话术）。不是"准确率旋钮"，调太高反而开始胡编 |
| **[prompt / 提示词](AI模块详解-SpringAI从零到实战【知识词典】.md#prompt)** | 发给模型的全部文字输入；其中 system prompt 即系统设定部分 |
| **[多模态（vision）](AI模块详解-SpringAI从零到实战【知识词典】.md#multimodal)** | 模型除文字外还能读图片/音频。视觉模型同时吃"文字+图片" |
| **[SSE](AI模块详解-SpringAI从零到实战【知识词典】.md#sse-protocol)** | Server-Sent Events：HTTP 上的服务器单向推送协议，聊天"打字机效果"的标配（详见第 6 章） |
| **[OpenAI 兼容协议](AI模块详解-SpringAI从零到实战【知识词典】.md#openai-protocol)** | 行业事实标准的接口格式（本文 0.2/0.3 那套报文）。兼容它 = 换厂商只改 base-url 和模型名，代码不动 |

## 本章动手作业

1. 打开 DeepSeek 开放平台（或任一大模型平台），手工创建一个 API Key，**不要提交到 git**。
2. 用 curl（或 Apifox/Postman）照着 0.2 的报文发一次请求，拿到 0.3 的响应。把 `temperature` 分别改成 `0.1` 和 `1.5` 各发一次，观察同一问题回答风格的差异。
3. 把 `messages` 里加上一条历史 `{"role":"assistant","content":"我们刚才聊到耳机"}`，再问"接着说"，体会模型为什么"记得"了。

---

# 第 1 章 最小可用：跑通第一个 ChatClient

> 📚 **本章可跳转词条**：[Starter 依赖](AI模块详解-SpringAI从零到实战【知识词典】.md#starter-dependency) · [Bean 与 DI](AI模块详解-SpringAI从零到实战【知识词典】.md#bean-di-ioc) · [ChatClient](AI模块详解-SpringAI从零到实战【知识词典】.md#chatclient) · [ChatModel](AI模块详解-SpringAI从零到实战【知识词典】.md#chatmodel) · [Lombok](AI模块详解-SpringAI从零到实战【知识词典】.md#lombok)

> **本章你将学会**：在 Spring Boot 项目里引入 Spring AI、写配置、用 3 行代码调通大模型——对应"写第一个 HelloController"的全过程。

## 1.1 类比：这件事和写第一个 Controller 完全同构

回想你第一次写 Spring Boot 的 HelloController 时干了什么：

| 写 HelloController | 让 Java 获得 AI 能力 |
|---|---|
| 引 `spring-boot-starter-web` 依赖 | 引 `spring-ai-starter-model-openai` 依赖 |
| 配 `server.port` | 配 `spring.ai.openai.base-url / api-key / model` |
| 写 `@RestController` 代码 | 注入 `ChatClient` 写 3 行调用代码 |
| 浏览器访问验证 | 跑个接口/测试验证 |

概念一一对应，没有任何新东西。唯一的区别：Spring AI 的 starter 帮你把 0.2 那坨 HTTP 报文的拼装和解析**全部封装掉了**。

## 1.2 第一步：引入依赖

Spring AI 用「BOM + starter」的经典二件套（和 Spring Cloud 一样的玩法）：

```xml
<properties>
    <spring-ai.version>1.0.0</spring-ai.version>
</properties>

<!-- ① BOM（Bill of Materials，物料清单）：只管版本统一，不引入任何 jar。
      它声明"Spring AI 全家桶各模块彼此兼容的版本组合"，你 import 它之后，
      下面引任何 spring-ai-xxx 都不用再写版本号 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- ② 核心 starter：OpenAI 兼容接入。
      注意：名字叫 openai 但不是只能连 OpenAI 官方——所有"OpenAI 兼容协议"的服务
      （DeepSeek 官方、各种中转站、通义的部分模式）都走它 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

要点：
- 只有这一个 AI 依赖。后文讲的多轮对话、函数调用、视觉多模态、SSE 流式**全部是它自带的**，不需要再引任何东西；
- Spring AI 1.x 发布在 Spring 官方仓库，国内网络可能拉不动，项目 pom 里配了阿里云 `aliyun-spring` 镜像仓库兜底。

## 1.3 第二步：写配置

```yaml
spring:
  ai:
    openai:
      # API 服务地址。注意：不要带 /v1 ！
      # Spring AI 会自动在后面拼 /v1/chat/completions，你带了反而变成 /v1/v1/... 404
      base-url: https://api.deepseek.com
      # API Key：生产环境用环境变量覆盖，别把真 key 提交进 git
      api-key: ${DEEPSEEK_API_KEY:sk-你的key}
      chat:
        options:
          model: deepseek-chat        # 默认模型（可被单次调用覆盖，见第 5 章）
          temperature: 0.7            # 默认温度
```

三个新手常踩的坑：

1. **base-url 带了 `/v1`** → 运行时报 404。规则：Spring AI 自动拼路径，你只给域名根。
2. **api-key 硬编码提交到 git** → key 立刻作废 + 被刷钱。`${DEEPSEEK_API_KEY:默认值}` 写法 = 优先读环境变量，读不到用默认值（开发方便），生产环境设置环境变量即可。
3. **模型名写错** → 报 `model not found` 之类错误。模型名去服务商文档抄，它是字符串不是枚举。

> 本项目（ai-mall）实际走的是一个 OpenAI 兼容的**中转服务**（opencode），只是 base-url 换成中转地址、模型名换成中转提供的 `deepseek-v4-flash-vision-exp`，其余配置与上面完全一致——这正好验证了"OpenAI 兼容协议换厂商零成本"。

## 1.4 第三步：3 行代码调通

Spring AI 的 starter 会根据你的配置**自动装配**一个 [`ChatClient.Builder`](AI模块详解-SpringAI从零到实战【知识词典】.md#chatclient)（[Builder 模式](AI模块详解-SpringAI从零到实战【知识词典】.md#builder-record)：先攒参数再 build 出成品）。你只需要在任意 Spring 组件里注入它：

```java
@Service
@RequiredArgsConstructor
public class HelloAiService {

    private final ChatClient.Builder builder;   // starter 自动配置好，直接注入

    public String sayHello() {
        ChatClient chatClient = builder.build();            // ① 拿到客户端
        return chatClient.prompt()                          // ② 开始一次对话
                .user("用一句话介绍降噪耳机")                //    用户消息
                .call()                                     // ③ 发请求，等完整回答
                .content();                                 // ④ 取回答正文（String）
    }
}
```

跑起来调用 `sayHello()`，你就能拿到大模型的回答字符串。对照第 0 章：

- `.prompt().user(...)` → 帮你拼 `messages` 数组（还自动附带你配置的 model/temperature）；
- `.call()` → 发出 `POST /v1/chat/completions` 并等响应；
- `.content()` → 替你取出 `choices[0].message.content`（0.3 讲过的那个候选列表的第一项）。

**几十行 HTTP 拼装解析代码，被压缩成了 3 行链式调用**——这就是 Spring AI 存在的意义（类比：MyBatis 之于 JDBC）。

## 1.5 对照真实项目：ChatClient 是怎么装配成 Bean 的

`HelloAiService` 里每次 `builder.build()` 有点浪费。项目里的做法是**在配置类里 build 一次，注册成 Bean，全局共用**。这就是 `AiConfig.java`（先混个脸熟，为什么是两个 Bean 在第 4 章末尾揭晓）：

```java
@Configuration
public class AiConfig {

    /** 文本链路的 ChatClient：全局注册了商品搜索工具（函数调用，第 4 章讲） */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ProductSearchTool searchTool) {
        return builder.defaultTools(searchTool).build();
    }

    /** 视觉链路的 ChatClient：干净的，不带工具 */
    @Bean
    public ChatClient visionChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
```

之后任何 Service 都能直接注入 `ChatClient` 用：

```java
private final ChatClient chatClient;   // 注入上面 build 好的成品
```

## 1.6 第一次跑常见的报错速查

| 现象 | 原因 | 处理 |
|---|---|---|
| 401 Unauthorized | key 错/过期，或环境变量没生效 | 打印 `$DEEPSEEK_API_KEY` 确认；重启 IDE 让环境变量生效 |
| 404 Not Found | base-url 带了 `/v1` | 去掉，只留域名根 |
| model not found | 模型名拼错/该服务没有此模型 | 抄服务商文档的准确模型名 |
| 连接超时 | 网络（部分服务需代理）或服务商故障 | curl 直接测 base-url 通不通；加超时配置 |
| 返回乱码/问号 | 终端编码问题（Windows PowerShell 常见） | 用 curl.exe/浏览器/IDE HTTP Client 测试，别用 `Invoke-RestMethod` 发中文 |

## 本章动手作业

1. 在你的（或 ai-mall 这个）项目里按 1.2/1.3 配好依赖和配置，写一个 `HelloAiService` 跑通 1.4 的 3 行代码。
2. 把配置里的 `model` 换成服务商的另一个模型，观察回答风格差异——体会"换模型不改代码"。
3. 故意把 base-url 改成带 `/v1` 的写法，亲眼看一次 404 长什么样（以后一眼认出来）。

---

# 第 2 章 让 AI 更可控：system prompt 与 temperature

> 📚 **本章可跳转词条**：[Prompt 工程](AI模块详解-SpringAI从零到实战【知识词典】.md#prompt) · [三种角色](AI模块详解-SpringAI从零到实战【知识词典】.md#system-prompt) · [temperature](AI模块详解-SpringAI从零到实战【知识词典】.md#temperature) · [max_tokens 等参数](AI模块详解-SpringAI从零到实战【知识词典】.md#max-tokens) · [幻觉](AI模块详解-SpringAI从零到实战【知识词典】.md#hallucination)

> **本章你将学会**：用 system prompt 给 AI 立人设、定规矩；理解 temperature 在项目里的实际取值策略；认识提示词工程的最小方法论。

## 2.1 为什么需要 system prompt：没有它 AI 是个"什么都聊的陌生人"

第 1 章的调用没有 system 设定，模型按它通用训练给你回答——有礼貌，但不知道自己是"你家的导购"。它会：

- 用户问"有什么耳机"时，开始推荐**全网**的耳机（索尼、 Bose……），而不是**你店里**的；
- 用户让它写一首诗，它就写一首诗（商城助手要不要满足这个需求？你来定）；
- 回答风格每次不一样，有时啰嗦有时简短。

**system prompt 就是给 AI 的"岗位说明书 + 公司规章"**：你是谁、你为谁服务、你该怎么做、什么不许做。它是整个应用里**性价比最高的控制手段**——不改一行 Java 代码，只改一段话，AI 的行为就完全不同。

## 2.2 本项目的两条 system prompt（逐句拆解）

ai-mall 有两条链路，各有一条 system prompt。

**文本链路**（`ChatServiceImpl.textSystemPrompt()`）：

```java
private String textSystemPrompt() {
    return "你是「AI 种草助手」，商城导购。用户询问商品/价格/找某类商品时，"
            + "先调用 searchProduct 工具按需搜索，再基于返回结果如实回答（给名称、价格、卖点）。"
            + "商品库里没有就坦诚说明，不要编造不存在的商品或参数。";
}
```

拆成三段看，正好是 [提示词的三要素](AI模块详解-SpringAI从零到实战【知识词典】.md#prompt)：

| 句子 | 要素 | 作用 |
|---|---|---|
| "你是「AI 种草助手」，商城导购" | **角色** | 立人设。模型的回答会向"导购"的风格靠拢（热情、懂行、促单） |
| "用户询问商品/价格……先调用 searchProduct 工具……如实回答" | **行为约束** | 规定动作顺序：先查工具、再回答。这是第 4 章函数调用能稳定工作的关键配合 |
| "商品库里没有就坦诚说明，不要编造" | **红线** | 大模型有"讨好型人格"，不知道也硬编（术语叫[幻觉](AI模块详解-SpringAI从零到实战【知识词典】.md#hallucination)）。明确写"没有就说没有"能显著压制编造 |

**视觉链路**（`visionSystemPrompt()`）：

```java
private String visionSystemPrompt() {
    return "你是「AI 种草助手」。用户会发来商品图片，请识别图片大概是什么商品（品类/外观/可能的商品类型），"
            + "并简要说明；不确定时如实说明，不要编造。";
}
```

同样的三要素：角色（种草助手）+ 任务（识别图片商品）+ 红线（不确定就说，别编）。

## 2.3 [temperature](AI模块详解-SpringAI从零到实战【知识词典】.md#temperature) 的实战取值：两条链路一个 0.7 一个 0.5

本项目调用时给两条链路设了不同温度：

```java
// 文本链路：跟随全局配置 0.7 —— 导购话术希望有感染力，允许适度发散
answer = chatClient.prompt()
        .system(textSystemPrompt())
        ...
        .call().content();

// 视觉链路：单次覆盖成 0.5 —— 识别是"认东西"的任务，要稳定克制、少发散
answer = visionChatClient.prompt()
        ...
        .options(OpenAiChatOptions.builder().model(VISION_MODEL).temperature(0.5).build())
        .call().content();
```

选值经验表（背下来面试能用）：

| 任务类型 | 建议 temperature | 理由 |
|---|---|---|
| 识别/分类/信息抽取（看图说商品、判断情感） | 0.1 ~ 0.5 | 答案有客观对错，要稳定、可复现 |
| 问答/导购/文案（本项目文本链路） | 0.6 ~ 0.9 | 要自然、有感染力，允许措辞多样 |
| 头脑风暴/起名/创意写作 | 1.0+ | 越发散越有料，错了也没关系 |

注意 `.options(...)` 的意义：**全局配置是默认值，单次调用可以覆盖**。这是 Spring AI 的实用设计——同一个 Bean 服务的不同场景可以各用各的参数。

## 2.4 提示词的最小方法论（够用了）

不用去背各种"提示词工程大师课"，写业务 system prompt 记住四件事：

1. **角色**：一句话告诉它"你是谁"；
2. **任务与步骤**：什么情况做什么事，最好有明确顺序（先查再答）；
3. **红线**：明确"不知道就说不知道，别编"；
4. **格式要求**（可选）：要 JSON？要分点？要简短？直接写在 prompt 里。

改 prompt 是迭代过程：上线后观察 badcase（比如它开始推荐库里没有的商品），针对性的加一句约束，再观察。**prompt 就是这个应用的"业务逻辑代码"**，一样要版本管理、一样要回归测试。

## 本章动手作业

1. 把 ai-mall 的 `textSystemPrompt()` 复制出来改三个版本：只会说英语的导购 / 只推荐 100 元以下商品的导购 / 说话不超过 20 字的导购。跑起来对比效果。
2. 用第 0 章的 HTTP 方式发请求，把 system prompt 删掉再问"你 restrictions 是什么"，体会没有 system 时模型的表现。
3. 思考题：视觉链路为什么用更低的温度？如果换成"看图写一段种草文案"的任务，温度该调高还是调低？

---

# 第 3 章 多轮对话：给没有记忆的模型装上记忆

> 📚 **本章可跳转词条**：[上下文窗口](AI模块详解-SpringAI从零到实战【知识词典】.md#context-window) · [Message 体系](AI模块详解-SpringAI从零到实战【知识词典】.md#springai-message) · [Lambda 与 Stream](AI模块详解-SpringAI从零到实战【知识词典】.md#lambda-stream) · [MyBatis](AI模块详解-SpringAI从零到实战【知识词典】.md#mybatis) · [Jackson](AI模块详解-SpringAI从零到实战【知识词典】.md#json-serialize)

> **本章你将学会**：理解"上下文记忆"的真相；用数据库存储对话历史；把 DB 记录转成 Spring AI 消息；认识 0 基础最容易懵的 `Message` 类型体系。

## 3.1 先亲眼看看"没有记忆"长什么样

```java
chatClient.prompt().user("我叫小明").call().content();     // → "你好小明！"（它记住了？）
chatClient.prompt().user("我叫什么？").call().content();    // → "抱歉，我不知道你叫什么"（原形毕露）
```

两次调用是两个独立请求，第二次的 `messages` 里根本没有"我叫小明"。第 0 章心智模型第一条说的就是这件事。

**那 ChatGPT 界面里为什么能连续对话？** 因为它的前端每次都把**之前的所有对话**重新发了一遍。你看到的"记忆"，其实是客户端每次都把历史装进 `messages` 里带来的。

```
第 1 轮请求: messages=[system, user("我叫小明")]
第 2 轮请求: messages=[system, user("我叫小明"), assistant("你好小明！"), user("我叫什么？")]
                              ↑ 把上一轮的完整问答原样带上，模型才"记得"
```

结论：**多轮对话 = 每次请求带全量历史**。历史从哪来？你需要自己存——这就是数据库登场的原因。

## 3.2 设计：两张表存对话

ai-mall 的方案（几乎是一切对话类产品的最小骨架）：

```sql
-- 会话表：一次连续对话 = 一个会话（微信的一个聊天框）
CREATE TABLE t_conversation (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL COMMENT '所属用户',
    biz_type   VARCHAR(20) NOT NULL COMMENT '会话类型：CHAT',
    title      VARCHAR(64) COMMENT '会话标题',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 消息表：会话里的每一条发言（聊天框里的一条气泡）
CREATE TABLE t_message (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL COMMENT '所属会话',
    role            VARCHAR(20) NOT NULL COMMENT 'user / assistant',
    content         TEXT        COMMENT '消息正文',
    extra_json      MEDIUMTEXT  COMMENT '扩展信息（本项目存用户图片 base64）',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
```

对应两个实体类（`ai/bean/` 下，MyBatis 映射对象）：

```java
@Data
public class Conversation {
    public static final String BIZ_CHAT = "CHAT";
    private Long id;
    private Long userId;
    private String bizType;
    private String title;
    private LocalDateTime createdAt;
}

@Data
public class ChatMessage {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String extraJson;
    private LocalDateTime createdAt;
}
```

`ChatMessage.role` 字段的取值 `user` / `assistant` **有意和 OpenAI 协议的 role 保持一致**——这样 DB 记录转成协议消息时不需要翻译表。

## 3.3 核心转换：DB 历史记录 → Spring AI 消息

这是本章最重要的一段代码（`ChatServiceImpl.toAiHistory()`）。先认识两个新类型：

- `UserMessage`：Spring AI 对"user 说的内容"的封装，构造时传文本；
- `AssistantMessage`：对"AI 说的内容"的封装。

它们都实现了统一接口 `org.springframework.ai.chat.messages.Message`（[Spring AI 的 Message 体系](AI模块详解-SpringAI从零到实战【知识词典】.md#springai-message)）。

这里有个新手必踩的坑：我们自己的实体**原本**也叫 `Message`（`com.aimall.ai.bean.Message`），和 Spring AI 的 `Message` 是两个完全不同的类，同一文件里同时用到时只能靠全限定名区分，读起来极易混淆。所以项目后来把这个实体改名为 **`ChatMessage`**（对应的表仍叫 `t_message`），从命名上根治了重名——现在看名字就能分辨：`ChatMessage` 是我们库里存的聊天记录，`Message` 是发给模型的协议消息。

```java
/** 历史消息转 Spring AI 消息数组（均为已完成的轮次，不与本次提问重复） */
private List<Message> toAiHistory(Long conversationId) {
    // ① 从 DB 按时间旧→新捞出这个会话的全部消息
    return messageMapper.selectByConversationId(conversationId).stream()
            // ② 按 role 分流：DB 的一条记录 → Spring AI 的一种消息
            .map(m -> (Message)
                    (ChatMessage.ROLE_USER.equals(m.getRole())
                            ? new UserMessage(m.getContent())        // 用户发言 → UserMessage
                            : new AssistantMessage(m.getContent()))) // AI 发言 → AssistantMessage
            .toList();
}
```

两个细节值得注意：

1. **排序必须是旧→新**（SQL 里 `ORDER BY created_at ASC, id ASC`）。对话是有顺序的，反了模型就读不懂剧情。
2. **不含本次提问**。本次的新问题由 `.user(req.getMessage())` 单独加，历史里不再重复放一遍（否则同问出现两次）。

## 3.4 组装完整请求：system + 历史 + 新问题

有了历史转换，一次多轮对话的调用就水到渠成（文本链路真实代码）：

```java
answer = chatClient.prompt()
        .system(textSystemPrompt())              // ① 人设与规则（第 2 章）
        .messages(toAiHistory(conv.getId()))     // ② 全部历史消息（本章）
        .user(req.getMessage())                  // ③ 本次新问题
        .call()
        .content();
```

发给模型的 `messages` 数组最终长这样：

```
[system(导购人设), user(旧问题1), assistant(旧回答1), user(旧问题2), assistant(旧回答2), ..., user(新问题)]
```

模型读完整段"剧本"后，接着最后一条往下演——这就是"连续对话"的全部真相。

**面试点：上下文会无限膨胀吗？** 会。每轮都带全量历史，token 消耗线性增长，迟早顶到上下文窗口。生产做法：只带最近 N 轮（滑窗）、或超长时用小模型把旧对话摘要成一条"前情提要"。本项目 V1 量小没做，但你要能说出演进方向。

## 3.5 会话的创建与复用：resolveConversation()

用户发消息时可能还没建会话（直接在输入框打字就发送）。项目用 `resolveConversation()` 统一处理：

```java
/** 复用传入会话（校验归属）或自动新建（标题取问题前 20 字） */
private Conversation resolveConversation(ChatRequest req) {
    Long userId = currentUserId();                       // Sa-Token 取当前登录用户
    if (req.getConversationId() == null) {
        // 没带会话 id → 自动新建：标题先取问题前 20 字占位
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setBizType(Conversation.BIZ_CHAT);
        c.setTitle(abbreviate(
                StringUtils.hasText(req.getMessage()) ? req.getMessage() : "图片识别", 20));
        conversationMapper.insert(c);
        return c;
    }
    // 带了会话 id → 查出来，且必须属于当前用户（防越权：拿别人的会话 id 直接 404）
    Conversation c = conversationMapper.selectById(req.getConversationId());
    if (c == null || !c.getUserId().equals(userId)) {
        throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
    }
    return c;
}
```

注意 `!c.getUserId().equals(userId)` 这一行——**AI 接口和普通接口一样要做越权防护**。攻击者拿别人的会话 id 乱试，绝不能因为"只是个聊天接口"就放行。

## 本章动手作业

1. 用第 0 章的 HTTP 方式手工模拟 3.1 的两次调用，第二次把"我叫小明"手动放进 messages，验证它"恢复记忆"。
2. 读 ai-mall 的 `ConversationMapper.xml` 和 `MessageMapper.xml`（手写 SQL），理解 `selectByConversationId` 为什么必须 `ORDER BY created_at ASC, id ASC`。
3. 进阶作业（为第 6 章铺垫）：想一想"流式输出时，AI 的回答是一个字一个字到达的，什么时候落库才不会存了半截话？"带着问题进入第 6 章。

---

# 第 4 章 函数调用 Function Calling：让 AI 会"查数据库"

> 📚 **本章可跳转词条**：[Function Calling](AI模块详解-SpringAI从零到实战【知识词典】.md#function-calling) · [工具 Schema 写法](AI模块详解-SpringAI从零到实战【知识词典】.md#tool-schema) · [JSON Schema](AI模块详解-SpringAI从零到实战【知识词典】.md#json-schema) · [@Tool 与注册](AI模块详解-SpringAI从零到实战【知识词典】.md#tool-annotation) · [注解与反射](AI模块详解-SpringAI从零到实战【知识词典】.md#annotation-reflection) · [空指针防御](AI模块详解-SpringAI从零到实战【知识词典】.md#null-safety) · [RAG](AI模块详解-SpringAI从零到实战【知识词典】.md#rag) · [Agent](AI模块详解-SpringAI从零到实战【知识词典】.md#agent)

> **本章你将学会**：为什么"把数据塞进提示词"行不通；如何用 `@Tool` 把一个 Java 方法暴露给 AI；模型调用工具时幕后发生的两轮 HTTP；以及模型传参的实战坑。**这是全书最有面试价值的一章。**

## 4.1 先撞一次南墙：全量注入为什么行不通

需求：AI 助手要能回答"店里有什么降噪耳机？"。

0 基础的第一反应通常是：把商品库全塞进 system prompt——"我店有 50 个商品：AirSound Pro 399 元……用户问什么你从里面找"。

本项目**最初真的这么做过，然后废弃了**。它有四个结构性问题：

| 问题 | 后果 |
|---|---|
| [Token 爆炸](AI模块详解-SpringAI从零到实战【知识词典】.md#token) | 50 个商品 ≈ 几千 token，每次对话都要全额重发；商品到 1000 个直接撑爆[上下文窗口](AI模块详解-SpringAI从零到实战【知识词典】.md#context-window)，账单也线性爆炸 |
| 数据过期 | prompt 是写死的快照。商品改价、下架后，AI 还在按旧数据回答 |
| 检索能力弱 | 模型在几千 token 的商品清单里"肉眼找"，容易看漏、看串，进而编造 |
| 无法分页聚合 | "最便宜的降噪耳机"这种需要排序/比较的问题，靠模型目测不可靠 |

**正确的思路换了个方向**：与其"把数据喂给 AI"，不如"给 AI 一个能查数据的工具"。就像新员工不可能背下全公司通讯录，但给他一个通讯录搜索系统就够了。

**[Function Calling（函数调用）](AI模块详解-SpringAI从零到实战【知识词典】.md#function-calling)** 就是干这个的：你把一个普通的 Java 方法注册为"工具"，AI 判断需要时，**自己决定**调用它、**自己填参数**，拿到工具的返回结果后再组织回答。数据永远实时来自你的数据库，token 只消耗"搜索结果"那一小段。

## 4.2 定义工具：@Tool 注解一个普通方法

> 核心机制：[`@Tool` 与工具注册](AI模块详解-SpringAI从零到实战【知识词典】.md#tool-annotation) · [工具 Schema 写法](AI模块详解-SpringAI从零到实战【知识词典】.md#tool-schema) · [注解与反射](AI模块详解-SpringAI从零到实战【知识词典】.md#annotation-reflection)

看 ai-mall 的真实工具类 `ProductSearchTool.java`（逐段讲）：

```java
@Component                                          // 普通 Spring Bean
@RequiredArgsConstructor
public class ProductSearchTool {

    private final ProductService productService;    // 复用商品模块的 Service！
    private final ObjectMapper objectMapper;

    /** 这个方法会暴露给 AI 模型，模型按需调用 */
    @Tool(description = "搜索本店在售商品：可按关键词、分类过滤，返回商品列表(名称/副标题/起售价)与总数。"
            + "当用户问商品、价格、或要找某类商品时调用，别自己编造商品。")
    public String searchProduct(
            @ToolParam(required = false, description = "关键词，如'耳机'") String keyword,
            @ToolParam(required = false, description = "分类id，可不传") Long categoryId,
            @ToolParam(required = false, description = "页码，默认1") Integer page,
            @ToolParam(required = false, description = "每页条数，默认10") Integer pageSize) {
        // ... 方法体见 4.3
    }
}
```

**`@Tool(description = ...)`** —— 这是工具的"门面"。description 会被 Spring AI 转成 JSON Schema 随请求发给模型，**模型完全看不到你的 Java 代码，它只靠这段描述判断"这个工具是干嘛的、什么时候该用"**。所以 description 要写两件事：

1. 这个工具返回什么（"返回商品列表(名称/副标题/起售价)与总数"）；
2. 什么场景该调用（"当用户问商品、价格、或要找某类商品时"），甚至写上"别自己编造商品"来压制幻觉。

写 description = 写"给 AI 看的接口文档"。它写得糊，模型就调得乱。

**`@ToolParam(required = false, description = ...)`** —— 每个参数的说明。本项目把 4 个参数全部设为 `required = false`（可空），原因见 4.5 的实战坑。

## 4.3 方法体：把模型的参数转成一次真实的数据库查询

```java
public String searchProduct(String keyword, Long categoryId, Integer page, Integer pageSize) {
    // 参数防御：模型可能传 null / 0 / 负数，全部兜底成合理默认值
    ProductQuery query = new ProductQuery();
    query.setKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim());
    query.setCategoryId(categoryId != null && categoryId > 0 ? categoryId : null);
    query.setPage(page == null || page < 1 ? 1 : page);
    query.setPageSize(pageSize == null || pageSize < 1 ? 10 : pageSize);

    // 复用商品模块的分页查询——AI 拿到的数据和前端搜索页完全一致
    PageResult<ProductVO> result = productService.pageOnSale(query);

    // 只挑模型需要的字段，组装成 JSON 字符串返回
    List<Map<String, Object>> items = result.getRecords().stream().map(vo -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", vo.getSpuName());
        m.put("subTitle", vo.getSubTitle());
        m.put("minPrice", vo.getMinPrice());
        m.put("id", vo.getId());
        return m;
    }).toList();

    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("total", result.getTotal());
    resp.put("items", items);
    try {
        return objectMapper.writeValueAsString(resp);       // 返回 JSON 文本
    } catch (Exception e) {
        return "{\"total\":" + result.getTotal() + ",\"items\":[]}";  // 序列化失败的兜底
    }
}
```

三个设计点：

1. **参数防御是必须的**。模型是"外人"：它可能传 null、传 0、传负数、传超长字符串。工具方法要像处理用户输入一样处理模型传参——这段代码把每个参数都兜了底。工具一抛异常，这次对话就直接失败，所以宁可兜底也不要让异常冒出去。
2. **复用 `ProductService.pageOnSale`**，而不是另写一条 SQL。这样 AI 检索到的数据和用户在搜索页看到的数据**永远一致**（同样的在售过滤、同样的排序）。面试金句：*"AI 能力复用业务 Service，而不是绕过它"*。
3. **返回值是 String**（模型只读文本）。序列化成 JSON 文本返回，并且只挑 name/subTitle/minPrice/id 这几个"回答问题用得上"的字段——商品详情、库存等大字段不给，省 token。

## 4.4 注册工具：defaultTools

工具类写好了，怎么让 AI "看得见"它？在 `AiConfig` 里注册：

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder, ProductSearchTool searchTool) {
    // defaultTools 接收 @Tool 注解对象，Spring AI 自动扫描其中的工具方法
    return builder.defaultTools(searchTool).build();
}
```

`defaultTools()` 的含义：**这个 ChatClient 发起的每一次对话，都携带这个工具**。之后第 3 章那套 `prompt().system().messages().user().call()` 的代码一行都不用改——工具是"后台装备"，对调用方透明。

## 4.5 幕后全景：一次函数调用到底发生了什么

这是面试必考题。用户问"有什么降噪耳机？"，表面上是"问了一下 AI"，背后是**两轮 HTTP 请求**：

```
第 1 轮请求（带上工具说明书）：
  请求: {
    messages: [system(导购人设), user("有什么降噪耳机？")],
    tools: [ {                       ← Spring AI 从 @Tool/@ToolParam 自动生成的"参数说明书"
      name: "searchProduct",
      description: "搜索本店在售商品……",
      parameters: { keyword: string(可选), categoryId: number(可选), ... }
    } ]
  }
  响应: 模型不直接回答，而是说——
    "我要调 searchProduct，参数 {"keyword": "降噪"}"     ← 这叫 tool_call

（Spring AI 自动执行 searchProduct("降噪")
  → 真实查库 → 得到 {"total":2, "items":[AirSound Pro ¥399, 声界 S09 ¥199]}）

第 2 轮请求（把工具结果喂回去）：
  请求: {
    messages: [
      system, user("有什么降噪耳机？"),
      assistant(tool_call: searchProduct({"keyword":"降噪"})),   ← 模型刚才"想做"的事
      tool({"total":2,"items":[...]})                            ← 第四种角色：工具的执行结果
    ],
    tools: [...]
  }
  响应: "为你找到 2 款降噪耳机：AirSound Pro（¥399）……"      ← 这次才是给用户的最终回答
```

三个必须能脱口而出的点：

1. **"模型执行了工具"是错觉**。模型只会"说想调什么工具、传什么参数"，**真正执行的是 Spring AI 框架**（在你的 JVM 里调你的方法、查你的 MySQL）；框架再把结果作为 role=`tool` 的消息发回去，模型才据此回答。
2. **messages 里出现了第四种角色 `tool`**，内容就是工具返回的那段 JSON。
3. **这一切对开发者全自动**：你要做的只有三件事——写 `@Tool` 方法、`defaultTools` 注册、`.call()` 拿最终文本。

## 4.6 实战坑：模型传参不老实（本项目真实踩坑）

**坑一：`required` 默认全为 true，模型硬填垃圾参数。**
`@ToolParam` 不写 `required` 时默认**必填**。模型一看"categoryId 必填"，而用户根本没提分类，它就硬填一个 `categoryId: 0`——查询结果空空如也，AI 回答"没有找到耳机"。用户明明问了个店里有货的东西！

修复：所有参数加 `@ToolParam(required = false)`，并在方法体里做 `categoryId <= 0 → null` 的防御（见 4.3 代码）。修复后"有什么降噪耳机"立刻能正确返回 AirSound Pro。

**坑二：description 不写"什么时候调"，模型该调时不调。**
只写"搜索商品"四个字，模型有时自己编（幻觉）也不调工具。把"当用户问商品/价格/找某类商品时调用"写进 description 后，配合 system prompt 里的"先调用 searchProduct 工具按需搜索"，调用率稳定下来。

**经验总结**：工具调用的稳定性 = description 质量 × system prompt 配合 × 参数防御。三件套缺一不可。

## 4.7 现在可以回答第 1 章留的问题了：为什么是两个 ChatClient？

- 文本链路：要工具 → `chatClient` 带 `defaultTools(searchTool)`；
- 视觉链路：本项目用的视觉模型对 function calling 支持不稳，**不能**挂工具 → `visionChatClient` 干干净净。

一个"带装备"、一个"空手"，互不污染。在 Service 里按 `req.hasImage()` 分流注入即可（第 8 章精读会看到分流代码）。

## 4.8 延伸：工具检索之后是什么？RAG

searchProduct 是"关键词检索"——用户问"有什么降噪耳机"，keyword="降噪" 能命中。但如果用户问"通勤地铁上想安安静静听歌，买啥？"——没有关键词能直接命中"降噪耳机"。

这就是 **RAG（检索增强生成）** 要解决的问题：用 embedding 模型把商品描述变成向量（一串数字，代表"语义"）存进向量库；用户提问也转向量，做**语义相似度检索**取回最相关的 Top-K 商品再喂给 AI。"关键词工具"负责精确查，"RAG"负责懂人话。本项目 V1 用工具检索够用，V2 升级 RAG 是规划好的演进路线（面试常被追问"再大点怎么办"，就这么答）。

## 本章动手作业

1. 给 ai-mall 加一个新工具 `queryMyOrders`：`@Tool` 注解一个方法，调 `OrderService` 查**当前登录用户**的最近订单（提示：工具方法里可以直接用 `StpUtil.getLoginIdAsLong()` 拿用户 id），让 AI 能回答"我最近买了什么"。
2. 故意把 `searchProduct` 的 description 改成只写两个字"搜索"，重启后问"店里有什么耳机"多试几次，观察模型是否开始不调工具/编商品。
3. 用第 0 章的 HTTP 知识解释：为什么说"函数调用是两轮 HTTP"？（提示：第 1 轮的响应里没有 content 正文，只有 tool_call）

---

# 第 5 章 视觉多模态：让 AI 看图

> 📚 **本章可跳转词条**：[多模态与视觉模型](AI模块详解-SpringAI从零到实战【知识词典】.md#multimodal) · [图片 token 计费](AI模块详解-SpringAI从零到实战【知识词典】.md#vision-token) · [Media 附件](AI模块详解-SpringAI从零到实战【知识词典】.md#media) · [Resource 抽象](AI模块详解-SpringAI从零到实战【知识词典】.md#resource-abstraction) · [Base64](AI模块详解-SpringAI从零到实战【知识词典】.md#base64) · [canvas 压缩](AI模块详解-SpringAI从零到实战【知识词典】.md#canvas-compress) · [dataURL/Blob](AI模块详解-SpringAI从零到实战【知识词典】.md#dataurl-blob) · [对象存储](AI模块详解-SpringAI从零到实战【知识词典】.md#oss)

> **本章你将学会**：一张用户图片从浏览器到模型的完整旅程（压缩→base64→解码→Media→vision 模型）；带图消息如何像文字一样流式输出；以及"图片存哪里"的架构权衡。

## 5.1 需求与链路总览

功能：用户在 AI 助手聊天框发一张商品图片（选图或 Ctrl+V 粘贴），AI 识别"这大概是什么商品"。

完整旅程（8 站，先有全局再逐站拆）：

```
用户选图/粘贴图
  → ① 前端 canvas 压缩（几 MB → 百来 KB）
  → ② 转成 base64 文本（dataURL）
  → ③ JSON 请求体 POST /api/v1/chat（image 字段）
  → ④ 后端 ChatRequest.image 接住，decodeImage 解码成字节
  → ⑤ 包成 Media，组装"文字+图片"的多模态 UserMessage
  → ⑥ 指定视觉模型（OpenAiChatOptions 覆盖）
  → ⑦ 模型识图回答
  → ⑧ 问答落库（图片 base64 存 extra_json），下次打开会话能回显
```

## 5.2 前端第①②站：压缩与 base64（为什么必须压）

真实用户的截图/照片动辄几 MB。直接 [base64](AI模块详解-SpringAI从零到实战【知识词典】.md#base64)：请求体暴涨几 MB、走一遍网络慢、塞数据库更存不下。所以**发之前先压缩**（[`Chat.vue` 的 `compressImage`](AI模块详解-SpringAI从零到实战【知识词典】.md#canvas-compress)，选图和粘贴两个入口共用）：

```js
// 最长边 ≤1280、JPEG 0.8（白底防透明变黑），返回 dataURL
function compressImage(file) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)          // 把文件变成本地 blob URL 供 <img> 加载
    img.onload = () => {
      URL.revokeObjectURL(url)                     // 用完释放，防内存泄漏
      const MAX = 1280
      let { width, height } = img
      const scale = Math.min(1, MAX / Math.max(width, height))   // 只缩不放
      if (scale < 1) {
        width = Math.round(width * scale)
        height = Math.round(height * scale)
      }
      const canvas = document.createElement('canvas')
      canvas.width = width; canvas.height = height
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#fff'                        // 白底！PNG 透明区域转 JPEG 会变黑，先铺白
      ctx.fillRect(0, 0, width, height)
      ctx.drawImage(img, 0, 0, width, height)       // 画上去
      resolve(canvas.toDataURL('image/jpeg', 0.8))  // 导出 JPEG，质量 0.8
    }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('图片解码失败')) }
    img.src = url
  })
}
```

产出是一个 **dataURL**：形如 `data:image/jpeg;base64,/9j/4AA...` 的自包含字符串（`data:` + MIME 类型 + `,` + base64 数据）。它可以直接塞进 `<img src>` 预览，也可以直接放进 JSON 请求体发给后端——前端预览和上传用同一份数据，很方便。

实测数据（本项目真实事故现场）：一张 2000×1500、219KB 的图，压缩后 base64 从 **299,548 字符降到 161,147**——注意，**压缩后仍然很大**！这就是第 9 章"数据库列被撑爆"事故的伏笔：光压缩不够，存储列也得扩容，双保险缺一不可。

## 5.3 后端第④站：解码 base64

前端发来的 `image` 字段可能是带 `data:image/jpeg;base64,` 前缀的 dataURL，也可能是裸 base64。`decodeImage()` 兼容两种：

```java
/** 解析 base64（兼容 data:image/...;base64,xxx 或纯 base64） */
private byte[] decodeImage(String image) {
    String data = image;
    int idx = image.indexOf("base64,");
    if (idx >= 0) {
        data = image.substring(idx + 7);        // 剥掉 "data:...;base64," 前缀
    }
    try {
        return Base64.getDecoder().decode(data);
    } catch (IllegalArgumentException e) {
        return Base64.getDecoder().decode(data.replaceAll("\\s", ""));  // 兜底：去掉换行空格再试
    }
}
```

## 5.4 后端第⑤站：组装多模态 UserMessage（本章核心 API）

> 涉及概念：[Media 附件](AI模块详解-SpringAI从零到实战【知识词典】.md#media) · [Resource 统一资源抽象](AI模块详解-SpringAI从零到实战【知识词典】.md#resource-abstraction) · [多模态消息](AI模块详解-SpringAI从零到实战【知识词典】.md#multimodal)

普通消息只有文字；带图消息 = 文字 + 图片附件。Spring AI 的写法（`buildUserMessage()`）：

```java
private UserMessage buildUserMessage(ChatRequest req) {
    if (!req.hasImage()) {
        return new UserMessage(req.getMessage());        // 纯文字：老样子
    }
    // 用户没配文字时给默认指令（识别任务总得说一句让它干嘛）
    String text = StringUtils.hasText(req.getMessage())
            ? req.getMessage() : "请识别这张图片，它大概是什么商品？";

    byte[] bytes = decodeImage(req.getImage());          // base64 → 原始字节
    // 以数据本身的格式为准，不采信前端声明（见 5.4.1）
    String mimeType = detectImageMimeType(bytes);
    String filename = "product." + imageExtension(mimeType);
    Media media = new Media(MimeTypeUtils.parseMimeType(mimeType),
            new ByteArrayResource(bytes) {               // 字节包成 Spring 的 Resource
                @Override
                public String getFilename() {
                    return filename;                     // 部分网关要求附件有文件名
                }
            });
    // Builder 组装"文字 + 图片"的多模态消息
    return UserMessage.builder().text(text).media(List.of(media)).build();
}
```

新面孔三个：

- **`Media`**（`org.springframework.ai.content.Media`）：一条消息携带的媒体附件（图片/音频），由 MIME 类型 + 数据源（`Resource`）构成；
- **`ByteArrayResource`**：Spring 的资源抽象，这里用来把 `byte[]` 包装成"数据源"。匿名子类重写 `getFilename()` 是兼容部分网关的小技巧；
- **`UserMessage.builder().text(...).media(...)`**：多模态消息的组装方式。对照协议：Spring AI 最终会把它翻译成 OpenAI 格式里的 `content: [{type:"text"...},{type:"image_url"...}]`。

> **为什么这里不用 `.user(req.getMessage())` 便捷方法？** 因为带图消息的 `content` 在协议里是**数组**（文字+图片混在一条消息），`.user(String)` 只能生成字符串 content、装不下 `Media`。所以带图必须手动组装 `UserMessage` 再经 `.messages()` 加入；纯文字链路用 `.user()` 一步到位即可。详见[词典：`.user()` vs 手动组装](AI模块详解-SpringAI从零到实战【知识词典】.md#springai-message)。

### 5.4.1 MIME 类型要跟着真实格式走（本项目真实踩过的坑）

早期版本这里直接硬编码：

```java
Media media = new Media(MimeTypeUtils.parseMimeType("image/png"), ...);  // ← 早期写法，已修
```

但**前端 canvas 压缩后导出的是 JPEG**（`toDataURL('image/jpeg', 0.8)`，见 5.2 节）。也就是说：声明的格式（`image/png`）和真实格式（JPEG）**不一致**。

它长期没暴露，是因为中转网关会按图片字节自行嗅探真实类型，不理会我们声明的 MIME——**属于"侥幸能跑"**，换个严格的网关、或换成按 MIME 拼 data URL 的实现就可能翻车。

修复思路：**格式要以数据本身为准，而不是听调用方说**。读文件头几个字节即可，这几个字节俗称 **magic number（魔数）**，相当于图片格式的"身份证"：

| 格式 | 文件头（十六进制） | 特征 |
|---|---|---|
| PNG | `89 50 4E 47` | 即 `\x89PNG` |
| JPEG | `FF D8 FF` | |
| GIF | `47 49 46 38` | 即 `GIF8`（GIF87a / GIF89a 同头） |
| WEBP | `52 49 46 46` … `57 45 42 50` | 前 4 字节 `RIFF`，第 8-11 字节 `WEBP` |
| BMP | `42 4D` | 即 `BM` |

```java
/** 按文件头字节嗅探图片真实格式，覆盖常见 5 种；认不出时兜底 image/jpeg */
private String detectImageMimeType(byte[] bytes) {
    if (bytes == null || bytes.length < 12) {
        return "image/jpeg";   // 真实图片远大于 12 字节，太短说明数据本身有问题
    }
    // PNG：89 50 4E 47，即 "\x89PNG"
    if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
        return "image/png";
    }
    // JPEG：FF D8 FF
    if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
        return "image/jpeg";
    }
    // GIF：47 49 46 38，即 "GIF8"
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

/** MIME → 文件扩展名，用于给附件起名（网关只关心"有个名"，不校验与内容是否一致） */
private String imageExtension(String mimeType) {
    return switch (mimeType) {
        case "image/png" -> "png";
        case "image/gif" -> "gif";
        case "image/webp" -> "webp";
        case "image/bmp" -> "bmp";
        default -> "jpg";
    };
}
```

**为什么用 magic number，而不是解析 dataURL 前缀**（`data:image/jpeg;base64,` 里就写着类型）？两个理由：

1. 前端也可能直接发**裸 base64**，压根没有前缀可解析；
2. 前缀是前端拼的，理论上可以和真实内容不符——**本项目踩的就是这个**（压成 JPEG 却声明 png）。

文件头字节来自数据本身，最权威，且不依赖调用方守规矩。

> **面试可讲**：这是"不要相信外部输入、以数据事实为准"的一个具体落地；顺带能体现你知道图片格式在二进制层面是靠 magic number 区分的，而不是靠文件扩展名——**扩展名可以随便改，文件头改不了**。

## 5.5 后端第⑥⑦站：切到视觉模型调用

看图必须用**视觉模型**（纯文本模型收到图片会报错）。本项目视觉模型是 `deepseek-v4-flash-vision-exp`，通过 `.options()` 单次覆盖（第 2 章讲过这个机制）。

> ⚠️ 顺便点破一个本项目当前的实情：yml 默认 model 也正是 `deepseek-v4-flash-vision-exp`——所以这里 `.model(VISION_MODEL)` 的覆盖**实际是无效的**，两条链路跑的是同一个模型。视觉链路的**真实差异只有 `temperature(0.5)`**（识图要稳）。将来若想两条链路用不同模型，改 yml 默认 model 并删掉这里的按次覆盖即可；若就用同一个，这段 `.model(...)` 可留作"将来换模型"的扩展点。

```java
/** 视觉识别模型（opencode 中转提供） */
private static final String VISION_MODEL = "deepseek-v4-flash-vision-exp";

// ChatServiceImpl.chat() 里的视觉分支
if (req.hasImage()) {
    answer = visionChatClient.prompt()
            .system(visionSystemPrompt())                        // 第 2 章的识图人设
            .messages(toAiHistory(conv.getId()))                 // 第 3 章的历史上下文
            .messages(List.of(buildUserMessage(req)))            // 本章的多模态消息
            .options(OpenAiChatOptions.builder()
                    .model(VISION_MODEL).temperature(0.5).build())  // 换模型 + 低温度
            .call()
            .content();
}
```

注意用的是**不带工具的 `visionChatClient`**（第 4.7 节的原因），`.messages()` 用了两次（历史 + 本次多模态消息，框架按顺序拼接）。

## 5.6 带图也能流式（实测支持后去掉降级）

早期本项目的中转视觉模型对"SSE 流式 + 多模态"组合支持不稳，曾用"带图降级非流式"兜底。**后来直连 DeepSeek 官方视觉模型，实测 `stream + 图片` 完全稳定**，于是去掉了降级——现在带图与纯文字一样逐字输出。

后端 `stream()` 里按 `req.hasImage()` 分流，两个分支都走 `.stream()`：

```java
if (req.hasImage()) {
    // 视觉链路流式：多模态消息走 messages()，温度 0.5（识别要稳）
    return visionChatClient.prompt()
            .system(visionSystemPrompt())
            .messages(toAiHistory(conv.getId()))
            .messages(List.of(buildUserMessage(req)))
            .options(OpenAiChatOptions.builder().model(VISION_MODEL).temperature(0.5).build())
            .stream().content()
            .doOnSubscribe(...)   // 存 user 消息（含图片）
            .doOnNext(sb::append)
            .doOnComplete(...)    // 存 assistant 完整回答
            .onErrorResume(...);
}
```

前端约定：**带图与纯文字统一走 `POST /api/v1/chat/stream`**。`/api/v1/chat` 接口保留，作为"一次要全量结果"的备选（测试、非流式调用方）。

> 判断"降级是否必要"的唯一标准是**实测模型支不支持流式+多模态**——别想当然，也别沿用旧结论。切换服务商后要重新验证。

## 5.7 第⑧站与架构权衡：图片存 base64 还是存对象存储？

本项目 V1 把图片 base64 直接存进 `t_message.extra_json`，`listMessages` 时映射为 `MessageVO.image` 返回，前端气泡 `<img :src="m.image">` 回显——不搭任何新服务就实现了"历史会话图片回显"。

**面试追问预案：为什么 base64 存数据库，而不是传 OSS/MinIO 存 URL？**

- V1 的权衡：① 少一个外部依赖（不用搭对象存储）；② 前端本来就必须把图交给后端喂视觉模型（base64 直通），顺手落库即可回显。
- 代价：DB 膨胀快；消息列表接口变重（每条带图消息背着一坨 base64）。
- V2 正确姿势：图片传对象存储，DB 只存 URL，调视觉模型也直接传 image_url（多模态 API 普遍支持）。

主动讲清"当前方案的边界 + 演进方向"，比声称"完美方案"更能体现工程判断。

## 本章动手作业

1. 在浏览器控制台里对任意网页截图，用 `compressImage` 的逻辑（可直接粘到控制台改改）把剪贴板图片压到 1280px，`toDataURL` 后看长度变化。
2. 用第 0 章的 HTTP 方式直接给视觉模型发一次请求：`content` 用数组格式 `[{"type":"text","text":"图里是什么"},{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}]`，理解 `UserMessage.builder().media()` 最终变成了协议里的什么。
3. 思考题：如果要把图片改成"先传 OSS 再把 URL 发给 AI"，前后端各要改哪几步？DB 列还用 MEDIUMTEXT 吗？

---

# 第 6 章 流式输出 SSE：从"转圈 30 秒"到"打字机"

> 📚 **本章是全书的难点，建议先花 20 分钟读完这几个词条再回来看代码**：
> [Flux](AI模块详解-SpringAI从零到实战【知识词典】.md#flux) ·
> [Mono](AI模块详解-SpringAI从零到实战【知识词典】.md#mono) ·
> [Publisher/Subscriber](AI模块详解-SpringAI从零到实战【知识词典】.md#publisher-subscriber) ·
> [惰性与 subscribe](AI模块详解-SpringAI从零到实战【知识词典】.md#subscribe-lazy) ·
> [常用操作符](AI模块详解-SpringAI从零到实战【知识词典】.md#reactor-operators) ·
> [冷流与热流](AI模块详解-SpringAI从零到实战【知识词典】.md#cold-hot) ·
> [背压](AI模块详解-SpringAI从零到实战【知识词典】.md#backpressure) ·
> [Schedulers](AI模块详解-SpringAI从零到实战【知识词典】.md#scheduler) ·
> [WebFlux vs MVC](AI模块详解-SpringAI从零到实战【知识词典】.md#webflux-vs-mvc) ·
> [MVC 如何返回 Flux](AI模块详解-SpringAI从零到实战【知识词典】.md#flux-in-mvc) ·
> [流中错误处理](AI模块详解-SpringAI从零到实战【知识词典】.md#flux-error) ·
> [响应式调试](AI模块详解-SpringAI从零到实战【知识词典】.md#reactor-debug) ·
> [SSE 协议](AI模块详解-SpringAI从零到实战【知识词典】.md#sse-protocol) ·
> [SSE vs WebSocket](AI模块详解-SpringAI从零到实战【知识词典】.md#sse-vs-websocket) ·
> [chunked](AI模块详解-SpringAI从零到实战【知识词典】.md#chunked) ·
> [缓冲与 curl -N](AI模块详解-SpringAI从零到实战【知识词典】.md#buffering) ·
> [ReadableStream](AI模块详解-SpringAI从零到实战【知识词典】.md#readable-stream) ·
> [EventSource vs fetch](AI模块详解-SpringAI从零到实战【知识词典】.md#eventsource)

> **本章你将学会**：流式的工程价值；SSE 协议 5 分钟入门；`Flux` 的最小必要知识（完全不需要响应式编程基础）；后端 `stream()` 与前端 fetch 解析的逐行精读，以及"流式时怎么落库才不会存半截话"。

## 6.1 为什么需要流式：体验账单

大模型生成一段 500 字回答要 10~30 秒（一个 token 一个 token 算出来的）。两种体验：

- **非流式**：用户盯着转圈 30 秒 → 突然弹出全部文字。像等一份慢递。
- **流式**：第 1 秒就开始出字，边生成边显示。像看人打字。

总耗时其实一样，但**首字时间**从 30 秒缩到 1 秒内。用户感知的"快"，几乎全由首字时间决定——这就是所有 AI 聊天产品都是打字机效果的原因。

## 6.2 SSE 5 分钟入门

**[SSE](AI模块详解-SpringAI从零到实战【知识词典】.md#sse-protocol)（Server-Sent Events）**：跑在普通 HTTP 上的**服务器单向推送**协议。

- 响应头：`Content-Type: text/event-stream`
- 响应体：不是一次性 JSON，而是一串文本事件流，每个事件形如：

```
data:为你

data:找到

data:2款

data:降噪耳机
```

（每个事件以 `data:` 开头、空行结尾。真实流式片段可能是任意长度的文本碎片，不一定是"一个词"。）

和 [WebSocket](AI模块详解-SpringAI从零到实战【知识词典】.md#sse-vs-websocket) 的对比一句话：WebSocket 是双向长连接（要升级协议、网关配置麻烦），SSE 是**单向推送 + 纯 HTTP**（服务器→客户端够用了，代理/网关天然友好）。聊天场景服务器只管推，SSE 就是最优解。

`[DONE]`：约定俗成的结束哨兵——流末尾发一个 `data: [DONE]`，前端看到它就知道"话说完了"。

## 6.3 Flux 最小必要知识（真的只需要这么多）

后端接口要返回 `Flux<String>`。0 基础直接给结论：

**[`Flux<String>`](AI模块详解-SpringAI从零到实战【知识词典】.md#flux) = "0~N 个字符串会随时间陆续到达"的异步流（[Reactor](AI模块详解-SpringAI从零到实战【知识词典】.md#publisher-subscriber) 库的类型）。在这里就把它理解成一个会陆续吐出字符串片段的盒子。**

> 想彻底搞懂"Flux 到底存了什么、为什么 Controller 一返回就能出字"，点 [Flux 内部原理](AI模块详解-SpringAI从零到实战【知识词典】.md#flux)、[惰性与 subscribe](AI模块详解-SpringAI从零到实战【知识词典】.md#subscribe-lazy)。

你不需要学响应式编程全套，只需要认识本项目用到的 5 个钩子：

| 调用 | 什么时候触发 | 项目里用来干嘛 |
|---|---|---|
| `.stream()` | —（替代 `.call()`） | 让 Spring AI 走流式：模型每算出一段就推一段 |
| `.content()` | — | 只要正文片段（`Flux<String>`），不要元数据 |
| `.doOnSubscribe(s -> {...})` | 订阅发生时（请求开始） | 落库用户消息 |
| `.doOnNext(s -> sb.append(s))` | 每个片段到达时 | 累积完整回答 |
| `.doOnComplete(() -> {...})` | 流正常结束 | 把攒齐的完整回答落库 |
| `.onErrorResume(e -> ...)` | 流中途出错 | 兜底返回一段提示文字 |

对照第 3 章埋的问题："流式时什么时候落库？"——答案是**两头落库**：user 消息在订阅时立刻存（它本来就是完整的），assistant 回答在 `doOnComplete` 里用累积的 `sb` 整体存。中途断流就不会把半截话存进库。

## 6.4 后端实现：stream() 逐行精读

先看 Controller（简单得离谱）：

```java
/**
 * SSE 流式问答：text/event-stream，逐字下发（HTTP/1.1 + SSE）
 */
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestBody @Valid ChatRequest req) {
    return chatService.stream(req);
}
```

唯一的新东西是 `produces = TEXT_EVENT_STREAM_VALUE`：声明响应是 SSE，Spring MVC 会自动把 Flux 的每个元素包装成 `data:xxx\n\n` 事件下发。**MVC 原生支持返回 Flux，不需要引入 WebFlux。**

> 📖 这三个概念没接触过？点这里直达知识词典：
> [`Flux` 是什么](AI模块详解-SpringAI从零到实战【知识词典】.md#flux) ·
> [WebFlux 与 MVC 的区别](AI模块详解-SpringAI从零到实战【知识词典】.md#webflux-vs-mvc) ·
> [MVC 怎么返回 Flux](AI模块详解-SpringAI从零到实战【知识词典】.md#flux-in-mvc) ·
> [SSE 协议](AI模块详解-SpringAI从零到实战【知识词典】.md#sse-protocol) ·
> [惰性 subscribe](AI模块详解-SpringAI从零到实战【知识词典】.md#subscribe-lazy)

Service 层完整代码（`ChatServiceImpl.stream()`）：

```java
@Override
public Flux<String> stream(ChatRequest req) {
    // 参数校验（与 chat() 同一条规则：无图时必须有问题文字）
    validate(req);
    // 确定所属会话（带 id 校验复用 / 不带自动新建），流式与非流式走同一套会话逻辑
    Conversation conv = resolveConversation(req);
    // 累积 AI 回答的完整文本：流式逐段吐字，落库必须等流结束后攒齐再存
    StringBuilder sb = new StringBuilder();
    if (req.hasImage()) {
        // 视觉链路流式：多模态消息走 messages()，温度 0.5（识别要稳，见 5.6）
        return visionChatClient.prompt()
                .system(visionSystemPrompt())
                .messages(toAiHistory(conv.getId()))
                .messages(List.of(buildUserMessage(req)))
                .options(OpenAiChatOptions.builder().model(VISION_MODEL).temperature(0.5).build())
                .stream().content()        //     Flux<String>：逐段正文
                .doOnSubscribe(s -> {
                    saveMessage(conv.getId(), ChatMessage.ROLE_USER, req.getMessage(), req.getImage());
                    autoRenameIfDefault(conv, req);
                })
                .doOnNext(sb::append)
                .doOnComplete(() -> saveMessage(conv.getId(), ChatMessage.ROLE_ASSISTANT, sb.toString(), null))
                .onErrorResume(e -> {
                    log.error("AI 图片流式问答失败: {}", e.getMessage(), e);
                    return Flux.just("\n\n[AI 服务暂时不可用，请稍后再试]");
                });
    }
    return chatClient.prompt()
            .system(textSystemPrompt())
            .messages(toAiHistory(conv.getId()))
            .user(req.getMessage())
            .stream()                      // ← 与 chat() 唯一的差别：call() 换成 stream()
            .content()                     //    拿到 Flux<String>：逐段正文
            .doOnSubscribe(s -> {
                saveMessage(conv.getId(), ChatMessage.ROLE_USER, req.getMessage(), null);  // 订阅即存用户消息
                autoRenameIfDefault(conv, req);     // 会话自动命名（第 7 章）
            })
            .doOnNext(sb::append)          // 每个片段追加到 StringBuilder
            .doOnComplete(() -> saveMessage(conv.getId(), ChatMessage.ROLE_ASSISTANT, sb.toString(), null))
            .onErrorResume(e -> {
                log.error("AI 流式问答失败: {}", e.getMessage(), e);
                return Flux.just("\n\n[AI 服务暂时不可用，请稍后再试]");   // 出错也走流推给前端
            });
}
```

三处值得停下来想：

1. **和 `chat()` 唯一的差别是 `.stream()` 换掉 `.call()`**——Spring AI 把流式做成了"换一个结尾词"的体验；
2. **`Flux.defer` 为什么包一层？** 因为 Java 方法调用是立即执行的：直接写 `Flux.just(chat(req))` 会在组装响应的瞬间就同步跑完整个 AI 问答（阻塞几十秒）；`defer` 让它推迟到"有人订阅"（响应真正开始输出）那一刻。这是函数调用时序的细节，面试讲出来很加分；
3. **错误也以流的方式给出**（`onErrorResume` 返回一段文字的 Flux）：HTTP 响应已经开始（200 + event-stream），没法再改状态码，只能把错误"说"给用户听。

## 6.5 前端实现：fetch 手撕 SSE 解析

为什么不用 axios？axios 把响应体当整体，不方便逐块读。原生 [`fetch`](AI模块详解-SpringAI从零到实战【知识词典】.md#fetch-api) + [`ReadableStream`](AI模块详解-SpringAI从零到实战【知识词典】.md#readable-stream) 是标准姿势（`api/index.js` 的 `sendStream`）：

```js
sendStream: async (data, onChunk, onDone, onError) => {
  const resp = await fetch('/api/v1/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: localStorage.getItem('token') || ''   // axios 拦截器帮不上忙，手动带 token
    },
    body: JSON.stringify(data)
  })
  if (!resp.ok || !resp.body) { onError?.(new Error('流式请求失败')); return }

  const reader = resp.body.getReader()          // 拿到流的"读卡器"
  const decoder = new TextDecoder('utf-8')      // 字节 → 文本
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read() // 逐块读（value 是 Uint8Array）
    if (done) break
    buffer += decoder.decode(value, { stream: true })   // stream:true：处理"半个中文字"跨块
    const lines = buffer.split('\n')            // SSE 按行解析
    buffer = lines.pop()                        // 最后一行可能不完整 → 留到下一轮拼
    for (const line of lines) {
      const trimmed = line.trim()
      if (trimmed.startsWith('data:')) {        // SSE 事件行
        const payload = trimmed.slice(5).trim()
        if (payload && payload !== '[DONE]') onChunk?.(payload)   // 每个片段回调给界面
      }
    }
  }
  onDone?.()
}
```

四个易错点（都藏在注释里了）：

1. `TextDecoder` 的 `{ stream: true }`：一个中文字占 3 字节，网络块可能恰好把字劈成两半，这个参数让解码器把"半个字"攒着等下一块；
2. `buffer = lines.pop()`：网络块边界不等于行边界，最后半行必须留到下一轮；
3. `payload !== '[DONE]'`：结束哨兵不显示给用户；
4. `onChunk` 回调驱动 UI：每来一片，把聊天气泡的内容更新一次（`Chat.vue` 里 `messages[last].content = acc`），就形成了打字机。

## 6.6 面试怎么讲流式（30 秒版本）

> "模型生成慢，首字时间是体验关键，所以我们用 SSE 流式：后端 Spring AI 把 `.call()` 换成 `.stream()` 得到 `Flux<String>`，Controller 标 `text/event-stream` 由 Spring MVC 自动按 SSE 下发；前端 fetch + ReadableStream 逐块解析 data: 行驱动打字机 UI。落库策略是两头存：用户消息在订阅时存，AI 回答在 doOnComplete 攒齐再存，避免断流存半截。带图链路也走流式——视觉模型实测支持流式+多模态，按 hasImage() 分流到对应的 ChatClient 即可。"

## 本章动手作业

1. 用 curl 直接打 `POST /api/v1/chat/stream`（记得带 token 头），肉眼看 `data:` 事件流和结尾的 `[DONE]`。
2. 故意在前端解析里去掉 `buffer = lines.pop()` 那行，观察偶发的"粘字/丢字"——理解为什么半行要缓存。
3. 进阶：给流式接口加一个"用户点停止"功能怎么设计？（提示：前端 AbortController 断开连接 + 后端 Flux 的取消语义 + 已生成部分要不要落库）

---

# 第 7 章 会话持久化与产品细节

> 📚 **本章可跳转词条**：[MyBatis](AI模块详解-SpringAI从零到实战【知识词典】.md#mybatis) · [TEXT vs MEDIUMTEXT](AI模块详解-SpringAI从零到实战【知识词典】.md#mysql-text) · [索引](AI模块详解-SpringAI从零到实战【知识词典】.md#db-index) · [Token 认证](AI模块详解-SpringAI从零到实战【知识词典】.md#auth-token) · [全局异常处理](AI模块详解-SpringAI从零到实战【知识词典】.md#exception-handling) · [分页](AI模块详解-SpringAI从零到实战【知识词典】.md#pagination)

> **本章你将学会**：会话模块的接口全景；消息怎么落库（含图片）；会话标题自动命名的产品逻辑。

## 7.1 接口全景：一个聊天助手的完整 API

`ChatRestController` 提供的 5 个接口（全部要求登录，Sa-Token 拦截 `/api/**`）：

```java
/** 新建空会话（前端传"新会话"占位标题，首条消息后由后端自动改名） */
@PostMapping("/conversations")

/** 当前用户的会话列表（新→旧），左侧会话栏展示用 */
@GetMapping("/conversations")

/** 拉取某个会话的全部历史消息（旧→新），切会话时回显；Service 层做归属校验防越权 */
@GetMapping("/conversations/{id}/messages")

/** 普通问答（非流式，返回完整回答） */
@PostMapping

/** SSE 流式问答：text/event-stream，逐字下发 */
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
```

前端 `chatApi` 与之一一对应：`createConversation / conversations / messages / send / sendStream`。切回旧会话时前端调 `messages` 拉历史回显，旧消息里的 AI 回答**不会**重新调模型——它们就是当初落库的文本。**这再次呼应第 0 章心智模型：模型不保存任何东西，一切记忆都是你存出来的。**

## 7.2 消息落库：saveMessage 与图片的存放

```java
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
```

设计意图：

- `content` 存正文（图片消息的正文可能是空串或用户配的文字）；
- `extra_json` 这个"扩展字段"装图片 base64（列用了 `MEDIUMTEXT`，[为什么不能是 TEXT](AI模块详解-SpringAI从零到实战【知识词典】.md#mysql-text)）——V2 若要做"AI 回答里引用的商品卡片"，也走这个字段存 JSON，**一张表预留扩展位**是常用手法；
- 出参映射时 `toMsgVO()` 把 `extraJson` 映射成 `MessageVO.image`，前端拿到的语义就是"这条消息带的图"。

注意非流式 `chat()` 是**回答成功之后**才落库（两条：user + assistant）；流式 `stream()` 是**订阅时存 user、完成时存 assistant**（第 6 章）。两种时序的原因不同：非流式等结果出来一起存更原子；流式怕断流，user 先存、assistant 攒齐再存。

## 7.3 会话标题自动命名

产品细节：前端新建会话先叫"新会话"，用户第一句话发出去后，标题自动变成那句话的浓缩——和微信置顶的 AI 助手、ChatGPT 侧边栏的行为一致。实现：

```java
/** 若会话标题仍是默认占位（新会话/图片识别/空），用首条用户消息自动命名 */
private void autoRenameIfDefault(Conversation conv, ChatRequest req) {
    String cur = conv.getTitle();
    if (cur == null || cur.isBlank() || "新会话".equals(cur) || "图片识别".equals(cur)) {
        String title = StringUtils.hasText(req.getMessage())
                ? abbreviate(req.getMessage(), 16)     // 取首条问题前 16 字
                : "图片识别";
        conversationMapper.updateTitle(conv.getId(), title);
        conv.setTitle(title);
    }
}
```

三个设计决策（面试聊产品思维的好素材）：

1. **谁命名**：后端命名而非前端——前端 send 完还要再发一次改名请求，且多端会话列表会不一致；后端在首个问答事务链里顺手改名，天然一致。
2. **为什么只认"占位标题"才改**：`autoRenameIfDefault` 只在标题是"新会话/图片识别"时才动。用户如果手动改过标题（未来功能），绝不能被覆盖——"只在占位态生效"就是保护用户数据。
3. **为什么是 16 字**：侧边栏宽度决定的展示极限，截断加 `...`（`abbreviate()`）。

对应 SQL 是 `ConversationMapper.updateTitle`（XML 里一条 UPDATE）——注意这是本模块唯一的 UPDATE，其余都是 INSERT/SELECT，聊天记录"只增不改"的语义。

## 7.4 防越权：AI 接口不豁免安全

第 3.5 节讲过 `resolveConversation()` 的归属校验，`listMessages` 同样要过 `ensureOwned()`：

```java
private void ensureOwned(Long conversationId) {
    Conversation c = conversationMapper.selectById(conversationId);
    if (c == null || !c.getUserId().equals(currentUserId())) {
        throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
    }
}
```

统一口径：**查不到和不是你的，都返回"会话不存在"**（不提示"这不是你的会话"——避免给攻击者枚举确认的信号）。安全细节在 AI 功能里照常执行，这是"AI 业务也是普通业务"的最好注脚。

## 本章动手作业

1. 画一张时序图：从"前端点击新会话"到"发第一条消息后侧边栏标题变化"，标出每一步调用的接口和后端方法。
2. 试着把 `ensureOwned` 删掉编译运行，用 A 用户的 token 携带 B 会话的 id 调 `messages`——亲眼看能把别人的聊天记录拉出来（学习环境做完**立刻还原**），理解这行代码的价值。

---

# 第 8 章 完整源码精读：ChatServiceImpl 从头到尾

> 📚 **本章可跳转词条**：[ChatClient](AI模块详解-SpringAI从零到实战【知识词典】.md#chatclient) · [@Tool 注册](AI模块详解-SpringAI从零到实战【知识词典】.md#tool-annotation) · [泛型](AI模块详解-SpringAI从零到实战【知识词典】.md#generic) · [Lambda 与 Stream](AI模块详解-SpringAI从零到实战【知识词典】.md#lambda-stream) · [构造器注入](AI模块详解-SpringAI从零到实战【知识词典】.md#bean-di-ioc)

> **本章你将学会**：把前面 6 章的知识串起来，逐段过一遍本项目 AI 业务核心类。读到这里，你应该能达到"这个类每一行都能给别人讲明白"的状态。

（建议对照源码 `backend/src/main/java/com/aimall/ai/service/impl/ChatServiceImpl.java` 阅读，类上注释已按 ①②③ 标注调用步骤。）

## 8.1 类声明与依赖注入

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** 视觉识别模型（opencode 中转提供） */
    private static final String VISION_MODEL = "deepseek-v4-flash-vision-exp";

    private final ConversationMapper conversationMapper;   // 会话表操作
    private final MessageMapper messageMapper;             // 消息表操作
    /** 文本链路：已全局注册 searchProduct 工具（见 AiConfig） */
    private final ChatClient chatClient;                   // 带装备的（第 4 章）
    /** 视觉链路：不带工具，避免视觉模型收到 function calling */
    private final ChatClient visionChatClient;             // 空手的（第 4.7 节）
```

两个 `ChatClient` + 两个 Mapper，就是本类的全部依赖。`@RequiredArgsConstructor`（Lombok）生成 final 字段的构造注入。

## 8.2 会话管理三件套

- `createConversation(title)`：建空会话，标题空则"新会话"占位；
- `listConversations()`：当前用户的会话列表（新→旧）；
- `listMessages(id)`：先 `ensureOwned` 防越权，再按旧→新拉消息，`toMsgVO` 把 `extraJson` 映射成 `image`。

## 8.3 chat()：非流式问答（带 ①②③ 步骤注释）

```java
@Override
public String chat(ChatRequest req) {
    // ① 参数校验：无图时必须有问题文字；纯图片识别允许空文本
    validate(req);
    // ② 确定本次对话所属会话：带了 conversationId 就校验归属后复用；没带就自动新建
    Conversation conv = resolveConversation(req);
    String answer;
    try {
        if (req.hasImage()) {
            // 视觉链路：识图（第 5 章）—— 多模态消息 + 视觉模型 + 低温度
            answer = visionChatClient.prompt()
                    .system(visionSystemPrompt())
                    .messages(toAiHistory(conv.getId()))          // 第 3 章历史
                    .messages(List.of(buildUserMessage(req)))     // 第 5 章多模态消息
                    .options(OpenAiChatOptions.builder().model(VISION_MODEL).temperature(0.5).build())
                    .call()
                    .content();
        } else {
            // 文本链路：导购 + 工具检索（第 2、4 章）
            answer = chatClient.prompt()
                    .system(textSystemPrompt())
                    .messages(toAiHistory(conv.getId()))
                    .user(req.getMessage())
                    .call()
                    .content();
        }
    } catch (Exception e) {
        // 任何模型侧异常（网络/限流/格式）统一转业务异常，不把堆栈漏给前端
        log.error("AI 问答失败: {}", e.getMessage(), e);
        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 服务暂时不可用");
    }
    // ③ 问答成功后两条消息一起落库：用户消息（含图片）供回显，AI 回答供下轮上下文
saveMessage(conv.getId(), ChatMessage.ROLE_USER, req.getMessage(), req.getImage());
saveMessage(conv.getId(), ChatMessage.ROLE_ASSISTANT, answer, null);
    // 首条消息后自动命名会话（第 7 章）
    autoRenameIfDefault(conv, req);
    return answer;
}
```

读法：先看 ①②③ 的骨架（校验 → 定会话 → 调模型 → 落库 → 改名），再看 try 里两条链路的差异（vision 带图带 options，text 带 tools），最后注意 catch 的位置——**模型异常被就地消化成业务异常**，调用方永远拿到统一的 `R{code,msg,data}`。

## 8.4 stream()：流式问答（第 6 章已逐行讲，这里只记结构）

带图与纯文字都走流式，按 `hasImage()` 分流：带图用 `visionChatClient`（多模态消息 + 温度 0.5），纯文字用 `chatClient`（挂工具）；两者都是 `.stream().content()` 加四个钩子（订阅存 user / 片段累积 / 完成存 assistant / 出错发提示语）。

## 8.5 私有工具方法一览（全部在前几章出现过）

| 方法 | 章节 | 一句话 |
|---|---|---|
| `validate(req)` | 8.3 | 无图必须有文字 |
| `resolveConversation(req)` | 第 3 章 | 定会话 + 防越权 |
| `ensureOwned(id)` | 第 7 章 | 查会话归属 |
| `toAiHistory(id)` | 第 3 章 | DB 历史 → Spring AI 消息 |
| `textSystemPrompt()` / `visionSystemPrompt()` | 第 2 章 | 两条链路的人设 |
| `buildUserMessage(req)` | 第 5 章 | 多模态消息组装 |
| `decodeImage(image)` | 第 5 章 | base64 → bytes |
| `saveMessage(...)` | 第 7 章 | 落库（含图片存 extra_json） |
| `autoRenameIfDefault(conv, req)` | 第 7 章 | 占位标题才自动命名 |
| `abbreviate(text, max)` | 第 7 章 | 标题截断 |

**验收标准**：合上教程，能按"骨架 → 两条链路差异 → 落库时序 → 每个私有方法的职责"四层把这个类讲给面试官。

---

# 第 9 章 踩坑复盘：4 个真实事故

> 📚 **本章可跳转词条**：[TEXT vs MEDIUMTEXT](AI模块详解-SpringAI从零到实战【知识词典】.md#mysql-text) · [Base64](AI模块详解-SpringAI从零到实战【知识词典】.md#base64) · [幻觉](AI模块详解-SpringAI从零到实战【知识词典】.md#hallucination) · [工具 Schema 写法](AI模块详解-SpringAI从零到实战【知识词典】.md#tool-schema) · [空指针防御](AI模块详解-SpringAI从零到实战【知识词典】.md#null-safety) · [多模态](AI模块详解-SpringAI从零到实战【知识词典】.md#multimodal) · [@Valid 校验](AI模块详解-SpringAI从零到实战【知识词典】.md#validation)

> **本章你将学会**：真实项目里 AI 功能怎么坏、怎么查、怎么修。面试讲"我踩过什么坑、怎么解决的"远比"我会用什么"有说服力。每个案例按"问题→排查→根因→修复→预防"五段结构。

## 案例一：用户发大图，报"服务器开小差了"（最经典）

- **问题**：测试时发小图一切正常；真实用户发截图/照片，前端弹 500"服务器开小差了"。
- **排查**：小图复现不了 → 翻后端日志，抓到完整堆栈：`MysqlDataTruncation: Data truncation: Data too long for column 'extra_json' at row 1`。
- **根因**：`t_message.extra_json` 建表时是 `TEXT`（上限 64KB，见 [TEXT vs MEDIUMTEXT](AI模块详解-SpringAI从零到实战【知识词典】.md#mysql-text)）。图片以 [base64](AI模块详解-SpringAI从零到实战【知识词典】.md#base64) 存这个字段，真实图片 base64 动辄几百 KB，INSERT 直接失败 → `DataIntegrityViolationException` → 全局异常兜底 500。之前测试用 120px 小图（约 20KB），根本没踩到天花板。
- **修复（双保险，缺一不可）**：
  1. 数据库：`ALTER TABLE t_message MODIFY extra_json MEDIUMTEXT`（16MB），建表脚本同步；
  2. 前端：发图前 [canvas 压缩](AI模块详解-SpringAI从零到实战【知识词典】.md#canvas-compress)（最长边 1280 + JPEG 0.8，实测 299KB → 118KB）。
  - 为什么两个都要：**压缩后 118KB 依然超 64KB**——压缩只能控制到"识别够用的分辨率"，不可能压进 64KB，所以扩列是必须的兜底；压缩则是为了请求体和存储体积本身的合理。
- **预防**：凡是"往字段里塞变长内容"的设计（base64、JSON），建表时按最大可能尺寸选型；测试要用真实量级的输入（大图/长文本/Unicode emoji）。

## 案例二：AI 一本正经地推荐库里没有的商品

- **问题**：用户问"有什么耳机"，AI 推荐了一款店里根本没有的"XX 降噪旗舰"。
- **排查**：看后端日志发现**模型压根没调用 searchProduct 工具**，直接凭训练记忆编了一个。
- **根因**：工具的 description 只写了"搜索商品"四个字，模型不确定"什么时候该用"；同时早期 system prompt 也没引导它先查再答。
- **修复**：description 写明触发场景（"当用户问商品、价格、或要找某类商品时调用，别自己编造商品"）+ system prompt 加"先调用 searchProduct 工具按需搜索，再基于返回结果如实回答"。双管齐下后调用稳定。
- **预防**：工具调用稳定性 = description 质量 × system prompt 配合 × 参数防御（第 4.6 节三件套）。prompt 是"业务逻辑"，要像代码一样迭代和回归。

## 案例三：模型传参传了个 `categoryId=0`，搜索永远为空

- **问题**：修好案例二后，"有什么降噪耳机"还是查不到货——工具被调用了，但返回空列表。
- **排查**：在工具方法里打日志，看到模型传的参数是 `{"keyword":"降噪","categoryId":0}`——`categoryId=0` 进了 SQL 条件，当然查不到。
- **根因**：`@ToolParam` 不写 `required` 默认必填。模型看到"必填"但用户没提分类，只好硬塞了个 0 交差。
- **修复**：所有参数显式 `@ToolParam(required = false)`，方法体内再做防御（`categoryId <= 0 → null`、page < 1 → 1 等）。
- **预防**：把模型当"不可信的外部调用者"——工具入参要做和用户输入同级别的校验兜底；能用"可选参数"的语义就不要用"必填"逼模型编值。

## 案例四：视觉模型收到"工具说明书"后开始胡言乱语

- **问题**：图片识别链路偶发答非所问、甚至报错。
- **排查**：对比成功/失败请求的报文，发现失败请求把 `tools: [searchProduct...]` 也带给了视觉模型。
- **根因**：早期只有一个 ChatClient，全局注册的工具对视觉调用同样生效；而本项目用的视觉模型对 function calling 支持不稳，收到 tools 后行为异常。
- **修复**：拆两个 ChatClient——`chatClient` 带 `defaultTools` 服务文本链路，`visionChatClient` 干净的服务视觉链路，按 `hasImage()` 分流。
- **预防**："一模型一配置"。不同模型的 prompt/工具/温度诉求不同，共用一个客户端迟早互相污染。

---

# 第 10 章 面试问答速记

> 📚 **本章涉及的高频词条**：[Function Calling](AI模块详解-SpringAI从零到实战【知识词典】.md#function-calling) · [RAG](AI模块详解-SpringAI从零到实战【知识词典】.md#rag) · [语义检索](AI模块详解-SpringAI从零到实战【知识词典】.md#semantic-search) · [多模态](AI模块详解-SpringAI从零到实战【知识词典】.md#multimodal) · [Flux](AI模块详解-SpringAI从零到实战【知识词典】.md#flux) · [SSE](AI模块详解-SpringAI从零到实战【知识词典】.md#sse-protocol) · [上下文窗口](AI模块详解-SpringAI从零到实战【知识词典】.md#context-window) · [对象存储](AI模块详解-SpringAI从零到实战【知识词典】.md#oss)

> 每条先给"30 秒答案"（可直接口述），括号里是展开方向。**理解优先，别背稿。**

**Q1：为什么用 Spring AI 而不是自己写 HTTP 调大模型？**
> ① 声明式 API：ChatClient 链式调用，把拼装报文/解析响应压缩成几行；② 函数调用全自动：工具 schema 生成、tool_call 执行、结果回传两轮编排框架做完；③ 多模态消息、SSE 流式开箱即用；④ OpenAI 兼容协议换厂商只改配置。（展开：类比 MyBatis 之于 JDBC；我们的 base-url 从 DeepSeek 官方换到中转站只改了两行 yml）

**Q2：Function Calling 的原理？模型真的执行了你的代码吗？**
> 两轮 HTTP：第一轮带 tools schema，模型返回 tool_call（方法名+参数）；框架在 JVM 里执行真实查询，把结果作为 role=tool 的消息发回；第二轮模型基于结果生成最终回答。模型只"点菜"，执行的是框架。（展开：结合本项目 searchProduct 讲数据实时性；"AI 复用业务 Service 而非绕过"）

**Q3：怎么解决大模型幻觉/编造商品？**
> 三层：工具给真实数据（有据可查）、system prompt 明确"库里没有就说没有"（红线压制）、识别任务用低温度（少发散）。（展开：讲案例二从"编商品"到稳定调工具的迭代过程）

**Q4：为什么两个 ChatClient？**
> 文本模型支持工具调用、视觉模型对 function calling 不稳，一个带 defaultTools 一个干净，按 hasImage() 分流。（展开：一模型一配置，避免互相污染——案例四）

**Q5：模型没有记忆，多轮对话怎么实现？**
> 每次请求带全量历史：DB 两张表存会话与消息，查询按旧→新转成 UserMessage/AssistantMessage 拼进 messages，本次问题追加在最后；回答再落库供下轮。（展开：归属校验防越权；上下文膨胀的滑窗/摘要演进）

**Q6：图片怎么传给模型？**
> 前端 canvas 压缩（1280px/JPEG0.8/白底）→ base64 → 后端解码 → `UserMessage.builder().text().media(new Media(mimeType, ByteArrayResource))` → `.options()` 切视觉模型 → 非流式返回。其中 MIME 用文件头 magic number 嗅探，不采信前端声明——早期硬编码 `image/png` 但前端压出来是 JPEG，靠网关自行嗅探才侥幸没出事（5.4.1）。（展开：为什么要压缩；base64 存 DB 的权衡与 OSS 演进；扩展名可随便改、文件头改不了）

**Q7：流式输出怎么做的？断流会不会存半截话？**
> 后端 `.stream().content()` 返回 `Flux<String>`，Controller 标 event-stream；前端 fetch + ReadableStream 解析 data: 行。落库两头存：user 消息订阅时存，AI 回答在 doOnComplete 攒齐再存，断流不会存半截。（展开：SSE vs WebSocket；Flux.defer 的延迟执行；带图与纯文字按 hasImage() 分流到不同 ChatClient 的流式实现）

**Q8：图片存 base64 进库还是传对象存储？**
> V1 base64 存 extra_json：少一个外部依赖、base64 本来就要喂视觉模型，顺手回显。代价是 DB 膨胀、列表接口重。V2 演进：对象存储存 URL，多模态调用也传 URL。（展开：主动讲方案边界比声称完美加分）

**Q9：商品库到 10 万级，searchProduct 还够吗？**
> 不够。关键词检索只能命中显式关键词，"通勤想安静听歌"这种语义需求匹配不上。演进到 RAG：商品描述 embedding 入向量库，提问做语义检索取 Top-K 喂给模型。工具负责精确查，RAG 负责懂语义，两者可组合。（展开：这就是 V2 规划，说明有架构演进意识）

**Q10：AI 服务的异常和普通接口有什么不同？**
> 处理原则相同（统一 R 结构、业务异常/系统异常分层），但要注意：① 模型超时/限流是常态，要转成友好业务异常（"AI 服务暂时不可用"）；② 流式场景 HTTP 200 已发出，错误只能以流内文本表达；③ 所有模型调用必须 try-catch 就地消化，不能让堆栈穿透到前端。

**Q11：上下文越长越贵，怎么控制成本？**
> 计量：打点每次调用的 usage（prompt/completion tokens）。手段：历史滑窗（只带最近 N 轮）、旧对话摘要、工具返回值精简字段（我们只返回 name/subTitle/minPrice/id）、system prompt 精炼。深层：高频问答可缓存。（展开：报价按 token 讲清"为什么不能无限塞历史"）

**Q12：这套 AI 功能怎么保证不被恶意使用？**
> 接入层：Sa-Token 登录 + 会话归属校验（防越权刷 AI）。用量层：按用户限流（如每分钟 N 次）。内容层：入参长度/图片大小白名单，必要时接内容审核。成本层：key 环境变量管理、usage 监控告警。（展开：AI 接口=普通业务接口，安全不豁免）

---

# 附录 A：Spring AI 常用类速查

> 📚 **相关词条**：[Message 体系](AI模块详解-SpringAI从零到实战【知识词典】.md#springai-message) · [Media](AI模块详解-SpringAI从零到实战【知识词典】.md#media) · [Resource](AI模块详解-SpringAI从零到实战【知识词典】.md#resource-abstraction) · [ChatModel](AI模块详解-SpringAI从零到实战【知识词典】.md#chatmodel) · [@Tool](AI模块详解-SpringAI从零到实战【知识词典】.md#tool-annotation)

```
org.springframework.ai.chat.client.ChatClient            // 调模型门面（全书主角）
org.springframework.ai.chat.client.ChatClient.Builder    // builder.defaultTools(tool).build()
org.springframework.ai.chat.messages.UserMessage         // builder().text().media()
org.springframework.ai.chat.messages.AssistantMessage    // 历史里的 AI 发言
org.springframework.ai.chat.messages.SystemMessage       // system prompt（.system() 内部用它）
org.springframework.ai.content.Media                     // (MimeType, Resource) 媒体附件
org.springframework.ai.tool.annotation.Tool              // @Tool(description)
org.springframework.ai.tool.annotation.ToolParam         // @ToolParam(required, description)
org.springframework.ai.openai.OpenAiChatOptions          // builder().model().temperature()
org.springframework.core.io.ByteArrayResource            // byte[] → Resource
org.springframework.util.MimeTypeUtils                   // parseMimeType(mimeType)，类型来自 sniff
reactor.core.publisher.Flux                              // 流式返回
```

# 附录 B：学习自查清单（全部打勾 = 出师）

**第 0 章 基础**
- [ ] 能白板画出 chat/completions 的请求/响应 JSON，并解释每个字段
- [ ] 能回答"choices 为什么是数组""finish_reason=length 说明什么"
- [ ] 能背出心智模型："模型无记忆、无你的数据库"

**第 1-2 章 起步与控制**
- [ ] 从零给一个 Spring Boot 项目接入 Spring AI 并跑通第一次调用
- [ ] 能写出含"角色+行为约束+红线"的 system prompt
- [ ] 能说清 temperature 的选值逻辑（识别低、创作高）

**第 3 章 多轮**
- [ ] 能解释"为什么 ChatGPT 界面能连续对话"，并设计出存历史的表
- [ ] 能手写 DB 记录 → Spring AI 消息的转换（注意排序与重名类）

**第 4 章 函数调用（重点）**
- [ ] 能写出 @Tool 工具方法并注册到 ChatClient
- [ ] 能白板推演两轮 HTTP 的完整报文（tools / tool_call / role=tool，对照附录 A.3 检查）
- [ ] 能讲出 required=false 与参数防御的实战坑

**第 5-6 章 多模态与流式**
- [ ] 能描述一张图从浏览器到模型的 8 站旅程
- [ ] 能手写多模态 UserMessage 的组装
- [ ] 能解释 SSE 协议格式、Flux 四个钩子的用途与落库时序
- [ ] 能用 30 秒讲清流式全链路（6.6 版本）

**第 7-8 章 工程化**
- [ ] 能设计"只增不改"的聊天记录持久化并处理越权
- [ ] 能逐段讲解 ChatServiceImpl 并回答追问

**第 9-10 章 综合素养**
- [ ] 能按"问题→排查→根因→修复→预防"讲 2 个以上真实踩坑
- [ ] 12 道面试题都能脱稿 30 秒作答

> 学完本教程 + 做完作业，你已经具备：在 Spring Boot 项目里独立实现"文本问答 + 工具检索 + 图片识别 + 流式输出 + 会话管理"的完整 AI 助手能力，并且知道每一步为什么这么做、出了问题怎么查——这正是面试官想确认的东西。下一步可以自学：RAG（embedding + 向量库）、多工具编排（MCP）、Agent 评测。

---

# 附录 C：四种场景的真实报文速查

> 前面各章讲的是"怎么写代码"，这里给你**代码最终变成的那串 JSON**。
> 全部按本项目真实配置拼装（模型、system prompt、工具定义均取自 `ChatServiceImpl` 与 `ProductSearchTool`），加了注释便于阅读，**真实报文无注释**。
>
> **学习建议：先读报文，再回头看代码。** 很多"为什么要这么封装"会瞬间明白——比如 `Media` 为什么要 MIME + 数据源，看一眼 A.2 就懂了。

## A.1 场景一：纯文本问答

**请求**

```jsonc
POST /chat/completions
{
  "model": "deepseek-v4-flash-vision-exp",
  "messages": [
    { "role": "system", "content": "你是「AI 种草助手」，商城导购。用户询问商品/价格/找某类商品时，先调用 searchProduct 工具按需搜索..." },
    { "role": "user",   "content": "帮我推荐一款降噪耳机" }
  ],
  "temperature": 0.7
}
```

**响应**：见 0.3 节，取内容走 `choices[0].message.content`。

## A.2 场景二：多模态看图（带图的 content 变数组）

```jsonc
{
  "model": "deepseek-v4-flash-vision-exp",
  "messages": [
    { "role": "system", "content": "你是「AI 种草助手」。用户会发来商品图片，请识别图片大概是什么商品..." },
    {
      "role": "user",
      "content": [                                    // ← 带图时从字符串变成数组
        { "type": "text", "text": "请识别这张图片，它大概是什么商品？" },
        {
          "type": "image_url",
          "image_url": { "url": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD..." }
        }                                             //    ↑ 三段式：data: + MIME + ;base64, + 编码
      ]
    }
  ],
  "temperature": 0.5                                  // 识图要稳，温度低于文本链路的 0.7
}
```

三个要点：

1. **`content` 从字符串变成数组**，图片作为 `image_url` 类型混在文字后面。
2. **data URL 三段式** = `"data:" + MIME + ";base64," + Base64编码`。这就是 `Media` 要你同时给 MIME 和数据来源的原因——**缺了 MIME 就拼不出中间那段**。
3. **图片仍以 base64 文本传输，没有独立的"图片上传接口"**。代价：体积比原始二进制大约 **1/3**（base64 用 4 个字符表示 3 个字节），所以前端必须先压缩。

> 想省掉这 33% 和整段编码往返，就把图片放对象存储、改成传 HTTP URL（V2 演进方向，见 5.6）。

## A.3 场景三：Function Calling —— 两轮 HTTP（重点）

**第 1 轮请求**：多了 `tools` 数组（Spring AI 从 `@Tool` / `@ToolParam` 注解生成）

```jsonc
{
  "model": "deepseek-v4-flash-vision-exp",
  "messages": [
    { "role": "system", "content": "你是「AI 种草助手」，商城导购。用户询问商品/价格/找某类商品时，先调用 searchProduct 工具按需搜索..." },
    { "role": "user", "content": "有没有降噪耳机" }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "searchProduct",
        "description": "搜索本店在售商品：可按关键词、分类过滤，返回商品列表(名称/副标题/起售价)与总数。当用户问商品、价格、或要找某类商品时调用，别自己编造商品。",
        "parameters": {
          "type": "object",
          "properties": {
            "keyword":    { "type": "string",  "description": "关键词，如'耳机'" },
            "categoryId": { "type": "integer", "description": "分类id，可不传" },
            "page":       { "type": "integer", "description": "页码，默认1" },
            "pageSize":   { "type": "integer", "description": "每页条数，默认10" }
          },
          "required": []                              // ← 四个参数全 required=false，这里是空数组
        }
      }
    }
  ]
}
```

**第 1 轮响应**：没有正文，只有"我要调工具"

```jsonc
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": null,                                // ← 注意：正文是 null！
      "tool_calls": [{
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "searchProduct",
          "arguments": "{\"keyword\":\"降噪\"}"        // ← 字符串化的 JSON，不是对象！
        }
      }]
    },
    "finish_reason": "tool_calls"                     // ← 不是 stop，是"我要调工具"
  }]
}
```

**第 2 轮请求**：把工具执行结果作为 `role=tool` 的消息发回

```jsonc
{
  "messages": [
    { "role": "system",    "content": "你是「AI 种草助手」..." },
    { "role": "user",      "content": "有没有降噪耳机" },
    { "role": "assistant", "content": null, "tool_calls": [ /* 上一条原样带回 */ ] },
    {
      "role": "tool",                                 // ← 工具结果专用角色
      "tool_call_id": "call_abc123",                  // ← 必须和上面的 id 对上
      "content": "{\"total\":1,\"items\":[{\"name\":\"AirSound Pro 真无线降噪耳机\",\"subTitle\":\"40dB 主动降噪\",\"minPrice\":399,\"id\":1}]}"
    }                                                 //   ↑ 就是 searchProduct 方法的返回值原样
  ]
}
```

**第 2 轮响应**：模型基于真实数据作答

```jsonc
{
  "choices": [{
    "message": { "role": "assistant", "content": "有的，推荐 AirSound Pro 真无线降噪耳机，40dB 主动降噪，399 元起……" },
    "finish_reason": "stop"
  }]
}
```

**四个新手必踩的点**：

| 坑 | 说明 |
|---|---|
| `content: null` | 第 1 轮响应没有正文，别去取 `message.content`，会拿到 null |
| `arguments` 是字符串 | 看着像 JSON 对象，实际是**字符串**，要反序列化后才能用 |
| `finish_reason: "tool_calls"` | 表明"要调工具"而非结束，框架据此进入第 2 轮 |
| 必须原样带回 assistant 消息 | 连同 `tool_calls` 一起，否则模型不知道在回答哪个调用；`tool_call_id` 必须对上 |

> 这两轮全部由 Spring AI 自动编排，你只写 `@Tool` 方法——但**报文长什么样必须知道**，否则工具不调用、参数填错时无从排查。

## A.4 场景四：SSE 流式（打字机）

服务器返回的不是 JSON，而是一段**事件流**：

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream

data: {"id":"cmpl-1","choices":[{"delta":{"content":"有"},"finish_reason":null}]}

data: {"id":"cmpl-1","choices":[{"delta":{"content":"的"},"finish_reason":null}]}

data: {"id":"cmpl-1","choices":[{"delta":{"content":"，"},"finish_reason":null}]}

data: {"id":"cmpl-1","choices":[{"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

要点：

- 每一块以 `data: ` 开头，**块之间用空行分隔**；
- 流式用 **`delta`（增量）**，非流式用 `message`（完整）——这是两者最大的结构差异；
- 最后一块 `delta` 为空、`finish_reason: "stop"`，是正常结束信号；
- `data: [DONE]` 是流的终止标志，前端见到它就停止读取；
- 因为响应头已按 200 发出，**中途出错改不了状态码**，只能在流内容里补一句错误文案（本项目 `onErrorResume` 就是干这个）。

## A.5 一张表：四种场景去哪儿取内容

| 场景 | 响应字段 | Spring AI 帮你做的事 |
|---|---|---|
| 非流式 | `choices[0].message.content` | `.call().content()` 直接取到字符串 |
| 流式 | `choices[0].delta.content`（每块一小段） | `.stream().content()` → `Flux<String>` |
| 工具调用 | `choices[0].message.tool_calls` | 框架解析参数、反射执行 `@Tool` 方法 |
| 工具结果回传 | 拼一条 `role: "tool"` 消息 | 框架自动拼装并再发一次请求 |

> 记住这张表，就掌握了"封装层 ↔ 协议层"的映射。面试白板推演时，照着 A.3 的四个报文画一遍两轮流程，比背概念有说服力得多。






