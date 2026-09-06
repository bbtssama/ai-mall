# Spring AI 从零到实战【知识词典】

> 本文件是 [`AI模块详解-SpringAI从零到实战.md`](./AI模块详解-SpringAI从零到实战.md) 的**超详细知识词典**。
>
> **用法**：在原笔记中阅读时，遇到带链接的术语（如 `Flux`、`SSE`、`token`）直接点击，会跳转到本文件对应词条；学完再点回原笔记继续。原笔记保持精简不冗余，需要深挖的概念全部沉淀在这里。

## 怎么用最高效

1. **别通读**。本词典是查的，不是读的——跟着原笔记的进度，点到哪学到哪。
2. 每个词条都按固定结构写：**一句话定义 → 为什么需要它 → 它内部怎么工作 → 在本项目里怎么用 → 常见误区 → 面试能怎么讲**。
3. 想系统补某块（比如响应式编程）时，再按下面的目录成组阅读。

## 返回原笔记

[← 回到《AI模块详解-SpringAI从零到实战》](./AI模块详解-SpringAI从零到实战.md)

---

## 目录

### 一、Java 与 Spring 根基

- [Lombok：@Data / @Slf4j / @RequiredArgsConstructor](#lombok)
- [Bean、DI 与 IoC](#bean-di-ioc)
- [Spring Boot Starter 与依赖管理](#starter-dependency)
- [RestClient / WebClient：Spring 的 HTTP 客户端](#restclient)
- [Resource：Spring 的统一资源抽象](#resource-abstraction)
- [Builder 模式与 Java Record](#builder-record)
- [Lambda 与 Stream API](#lambda-stream)
- [注解与反射](#annotation-reflection)
- [泛型](#generic)
- [空指针防御与 Optional](#null-safety)
- [全局异常处理 @RestControllerAdvice](#exception-handling)
- [@Valid 与 Bean Validation](#validation)
- [MyBatis 基础](#mybatis)
- [Jackson 与 JSON 序列化](#json-serialize)

### 二、响应式编程 Reactor

- [Flux：0~N 个元素的异步序列](#flux)
- [Mono：0~1 个元素](#mono)
- [Publisher / Subscriber / Subscription](#publisher-subscriber)
- [冷流与热流](#cold-hot)
- [背压 Backpressure](#backpressure)
- [Reactor 常用操作符速查](#reactor-operators)
- [惰性与 subscribe()](#subscribe-lazy)
- [线程调度 Schedulers](#scheduler)
- [WebFlux vs Spring MVC](#webflux-vs-mvc)
- [Spring MVC 如何返回 Flux](#flux-in-mvc)
- [流中的错误处理](#flux-error)
- [响应式代码调试与排错](#reactor-debug)

### 三、网络与协议

- [HTTP 请求/响应结构](#http-basics)
- [SSE 协议详解](#sse-protocol)
- [SSE vs WebSocket vs 轮询](#sse-vs-websocket)
- [Transfer-Encoding: chunked](#chunked)
- [Content-Type 与字符集](#content-type)
- [HTTP 状态码](#http-status)
- [CORS 跨域](#cors)
- [JSON Schema](#json-schema)
- [Base64 编码](#base64)
- [Token 认证与 Authorization 头](#auth-token)
- [缓冲与流式传输（curl -N）](#buffering)

### 四、大模型核心概念

- [大语言模型 LLM 是什么](#llm)
- [Token：模型的计量单位](#token)
- [分词器 Tokenizer](#tokenizer)
- [上下文窗口 Context Window](#context-window)
- [Prompt 与提示词工程](#prompt)
- [system / user / assistant 三种角色](#system-prompt)
- [temperature 与 top_p](#temperature)
- [max_tokens / n / stop 等参数](#max-tokens)
- [幻觉 Hallucination](#hallucination)
- [OpenAI 兼容协议](#openai-protocol)
- [常见模型厂商与选型](#model-providers)
- [训练 vs 推理](#inference)
- [多模态与视觉模型](#multimodal)
- [图片的 token 计费](#vision-token)

### 五、Function Calling、RAG 与 Agent

- [Function Calling 原理](#function-calling)
- [工具 Schema 与 description 写法](#tool-schema)
- [RAG 检索增强生成](#rag)
- [Embedding 向量](#embedding)
- [向量数据库](#vector-db)
- [语义检索 vs 关键词检索](#semantic-search)
- [分块策略 Chunking](#chunking)
- [重排序 Rerank](#rerank)
- [Agent 与工具编排](#agent)
- [MCP 协议](#mcp)

### 六、Spring AI 框架

- [ChatClient 链式 API](#chatclient)
- [ChatModel vs ChatClient](#chatmodel)
- [Spring AI 的 Message 体系](#springai-message)
- [Media 与多模态附件](#media)
- [@Tool 与工具注册](#tool-annotation)
- [Advisor 拦截器](#advisor)
- [ChatMemory 会话记忆](#chatmemory)
- [VectorStore 向量存储](#vectorstore-springai)
- [可观测性 Observability](#observation)

### 七、前端与数据层

- [fetch API](#fetch-api)
- [ReadableStream 流读取](#readable-stream)
- [EventSource vs fetch](#eventsource)
- [canvas 图片压缩](#canvas-compress)
- [dataURL / Blob / File](#dataurl-blob)
- [前端存储 localStorage](#localstorage)
- [MySQL TEXT vs MEDIUMTEXT](#mysql-text)
- [对象存储 OSS](#oss)
- [索引与查询优化](#db-index)
- [分页 Pagination](#pagination)

---

## 返回原笔记

[← 回到《AI模块详解-SpringAI从零到实战》](./AI模块详解-SpringAI从零到实战.md)

---

> 本组概念你作为 Java 后端开发者大多已接触，因此写得简短——只讲"在主笔记语境下需要知道的部分"。深入内容建议查官方文档。

<a id="lombok"></a>

### Lombok：@Data / @Slf4j / @RequiredArgsConstructor

**一句话定义**：编译期注解处理器，自动生成 getter/setter/构造器/日志对象等样板代码。

#### 核心认知

Lombok 是 **javac 的注解处理器（APT）**，在**编译期**修改抽象语法树，把方法写进 `.class` 文件。**它生成的是真实字节码，不是运行时反射魔法**——所以 MyBatis 能调 `getRole()`、Jackson 能调 `getMessage()`，和手写的一模一样。

| 注解 | 生成 | 本项目用法 |
|---|---|---|
| `@Data` | getter + setter + toString + equals + hashCode | `ChatMessage`、`ChatRequest`、`MessageVO` |
| `@Getter` | 只生成 getter | `ResultCode`（枚举）、`BusinessException` |
| `@Slf4j` | `private static final Logger log = ...` | `ChatServiceImpl`、`GlobalExceptionHandler` |
| `@RequiredArgsConstructor` | 为 **`final` 字段**生成构造器 | `ChatServiceImpl`、`ChatRestController` |

#### 在本项目里怎么用

最值得注意的组合是 **`@RequiredArgsConstructor` + `final` 字段 = 构造器注入**：

```java
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    // Lombok 生成构造器，Spring 用它做依赖注入（不需要 @Autowired）
}
```

这是 Spring 官方推荐的注入方式（相比字段注入更容易测试、能发现循环依赖）。

#### 常见误区

1. **以为 Lombok 是运行时反射。** 是编译期生成字节码，零运行时开销。
2. **日志变量写成 `logger`。** `@Slf4j` 生成的变量名是 **`log`**。

#### 面试能怎么讲

> "Lombok 是编译期注解处理器，直接改 AST 生成字节码，不是运行时反射。我们项目用 @Data 生成实体方法，用 @RequiredArgsConstructor 配合 final 字段做构造器注入——这也是 Spring 官方推荐的注入方式。"

相关词条：[#bean-di-ioc](#bean-di-ioc)、[#json-serialize](#json-serialize)

---

<a id="bean-di-ioc"></a>

### Bean、DI 与 IoC

**一句话定义**：Bean 是 Spring 容器管理的对象；IoC 是把"创建对象的控制权"交给容器；DI 是容器把依赖注入进来。

#### 核心认知

```
传统：  Service service = new Service();          // 自己 new，自己管依赖
IoC：   @Service + 容器创建 → 需要时注入给你      // 控制权反转
```

三种注入方式对比：

| 方式 | 写法 | 评价 |
|---|---|---|
| **构造器注入** | `private final X x;` + `@RequiredArgsConstructor` | ✅ **推荐**，可测试、能暴露循环依赖 |
| Setter 注入 | `@Autowired setX()` | 用于可选依赖 |
| 字段注入 | `@Autowired private X x;` | ❌ 不推荐，难测试、隐藏依赖 |

#### 在本项目里怎么用

- `@Service` / `@Component` / `@Mapper` / `@RestController` 标记的类都会被扫描成 Bean
- `AiConfig` 里用 `@Bean` 方法手动定义 `ChatClient`（因为需要定制配置）
- **两个 ChatClient 同名类型不同** → 靠**参数名**区分（`chatClient` / `visionChatClient`），这是 Spring 按名称注入的特性

#### 常见误区

1. **以为 `@Autowired` 是必须的。** 构造器注入不需要它。
2. **同类型多个 Bean 会报错。** 要用 `@Qualifier` 或参数名匹配。

#### 面试能怎么讲

> "IoC 是把对象创建交给容器，DI 是容器注入依赖。我们项目统一用构造器注入（Lombok 的 @RequiredArgsConstructor 配合 final 字段），这也是 Spring 官方推荐的——相比字段注入更好测试、能在启动时暴露循环依赖问题。"

相关词条：[#lombok](#lombok)

---

<a id="starter-dependency"></a>

### Spring Boot Starter 与依赖管理

**一句话定义**：Starter 是一组**预打包的依赖集合**；Spring Boot 的 parent POM 统一管理版本，你不用写版本号。

#### 核心认知

```xml
<parent>
    <artifactId>spring-boot-starter-parent</artifactId>   <!-- 版本管理 -->
    <version>3.4.5</version>
</parent>

<dependency>
    <artifactId>spring-boot-starter-web</artifactId>      <!-- 不用写版本 -->
</dependency>
```

**`spring-boot-starter-xxx` 的命名规律**：

| Starter | 带来什么 |
|---|---|
| `starter-web` | Spring MVC + Tomcat + Jackson |
| `starter-validation` | 参数校验（@Valid） |
| `starter-test` | JUnit + Mockito |
| `spring-ai-starter-model-openai` | Spring AI + OpenAI 协议客户端 + **reactor-core** |

**传递依赖**是本项目的关键点：`reactor-core`（Flux 所在库）不是我们显式引入的，而是由 `spring-ai-starter-model-openai` 带进来的——因为 Spring AI 的 `stream()` 返回 `Flux`。

#### 在本项目里怎么用

`backend/pom.xml` 还配了阿里云镜像加速依赖下载。

想看某个依赖从哪来：

```powershell
cd backend; mvn dependency:tree
```

#### 常见误区

1. **以为用了 Flux 就是 WebFlux。** 不是，看有没有 `starter-webflux`（见 [WebFlux vs Spring MVC](#webflux-vs-mvc)）。
2. **以为 starter 只能用官方的。** 第三方也提供 starter（如 `mybatis-spring-boot-starter`）。

#### 面试能怎么讲

> "Starter 是预打包的依赖集合，parent POM 统一管版本所以不用写版本号。我们项目要注意的是 reactor-core 属于传递依赖——是 spring-ai-starter-model-openai 带进来的，因为 Spring AI 的流式接口返回 Flux。所以'用了 Flux'不等于'引入了 WebFlux'。"

相关词条：[#bean-di-ioc](#bean-di-ioc)、[#webflux-vs-mvc](#webflux-vs-mvc)

---

<a id="restclient"></a>

### RestClient / WebClient：Spring 的 HTTP 客户端

**一句话定义**：Spring 提供的 HTTP 客户端——`RestClient` 是同步的（Spring 6.1+），`WebClient` 是响应式的。

#### 核心认知

| 客户端 | 风格 | 状态 | 说明 |
|---|---|---|---|
| `RestTemplate` | 同步 | **已过时** | 老项目常见，不推荐新用 |
| `RestClient` | 同步、链式 | ✅ 推荐 | Spring 6.1 引入 |
| `WebClient` | 响应式、返回 Mono/Flux | ✅ | WebFlux 栈的默认客户端 |

Spring AI 内部用它们发 HTTP 请求给模型。

#### 在本项目里怎么用

**业务代码不直接用**——Spring AI 封装了。但理解它有助于排查问题：

- 连接超时、读取超时在底层客户端配置
- 想看真实报文，开 `DEBUG` 日志或代理抓包

#### 常见误区

1. **以为要自己写 HTTP 调用模型。** 用框架，这是选 Spring AI 的核心价值。
2. **混淆 `RestClient` 和 `WebClient` 的返回类型。** 前者同步返回对象，后者返回 Mono/Flux。

#### 面试能怎么讲

> "Spring 6.1 后推荐用 RestClient 做同步 HTTP 调用，响应式场景用 WebClient，老的 RestTemplate 已不推荐。我们项目业务代码不直接调 HTTP——Spring AI 内部封装了，这也正是用框架的价值。"

相关词条：[#chatmodel](#chatmodel)、[#openai-protocol](#openai-protocol)

---

<a id="resource-abstraction"></a>

### Resource：Spring 的统一资源抽象

**一句话定义**：Spring 对"数据来源"的统一接口——文件、classpath、URL、字节数组全都实现它。

#### 核心认知

```
Resource（接口）
   ├── FileSystemResource    文件系统
   ├── ClassPathResource     classpath 下
   ├── UrlResource           网络 URL
   ├── ByteArrayResource     内存字节数组   ← 本项目用这个
   └── InputStreamResource   输入流
```

**为什么需要统一？** 因为框架代码不该关心数据从哪来。Spring AI 的 `Media` 只认 `Resource`，所以你传文件或字节数组它都能处理。

#### 在本项目里怎么用

`buildUserMessage()` 里把 `byte[]` 包成 Resource 传给 Media：

```java
new ByteArrayResource(bytes) {
    @Override
    public String getFilename() {
        return "product.jpg";     // 默认返回 null，部分网关要求必须有文件名
    }
};
```

**那个匿名子类重写是兼容层脏活**——`ByteArrayResource.getFilename()` 默认返回 `null`，而某些网关要求附件带文件名，所以硬补一个。

**V2 优化方向**：图片存对象存储后，改用 `UrlResource`，连 base64 编码都省了。

#### 常见误区

1. **以为 ByteArrayResource 有文件名。** 默认 `null`，要重写 `getFilename()`。
2. **以为 Resource 只能读文件。** 字节数组、URL 都行。

#### 面试能怎么讲

> "Resource 是 Spring 对数据来源的统一抽象，文件、classpath、URL、字节数组都有实现。Spring AI 的 Media 只认 Resource，所以传什么都行。我们用 ByteArrayResource 包图片字节，还用匿名子类重写了 getFilename——因为它默认返回 null，而部分网关要求附件必须有文件名。"

相关词条：[#media](#media)、[#oss](#oss)

---

<a id="builder-record"></a>

### Builder 模式与 Java Record

**一句话定义**：Builder 用于**复杂对象的分步构造**；Record 是 Java 16+ 的**不可变数据载体**语法糖。

#### 核心认知

**Builder 解决什么问题**——构造器参数太多时可读性差：

```java
// ❌  telescoping constructor：分不清第 3 个 true 是什么
new UserMessage(text, media, metadata, true, null, 3);

// ✅ Builder：自解释
UserMessage.builder()
        .text("这图里是什么？")
        .media(List.of(media))
        .build();
```

**Record** 是不可变数据类，一行顶掉几十行：

```java
public record Point(int x, int y) { }
// 自动生成：构造器、getter（x() / y()）、equals、hashCode、toString
```

#### 在本项目里怎么用

- **Builder**：`UserMessage.builder()`、`ChatClient.builder()`、`OpenAiChatOptions.builder()`——Spring AI 大量使用链式 Builder
- **Record**：本项目用了，但只用在请求体 DTO 上——`ChatRestController` 里的 `ChatTitleRequest(String title)` 就是 record。实体类用不了，因为 MyBatis 需要 setter 来填充查询结果

#### 常见误区

1. **以为 Record 能替代实体类。** 不能，MyBatis 需要 setter 来填充字段。
2. **以为 Builder 一定要手写。** Lombok 的 `@Builder` 能自动生成。

#### 面试能怎么讲

> "Builder 解决构造器参数过多、可读性差的问题，Spring AI 的 API 大量使用它（UserMessage.builder、ChatClient.builder）。Record 是 Java 16 的不可变数据载体，能一行生成构造器、getter、equals，但我们的实体用不了——MyBatis 需要 setter 来填充查询结果。"

相关词条：[#lombok](#lombok)、[#springai-message](#springai-message)

---

<a id="lambda-stream"></a>

### Lambda 与 Stream API

**一句话定义**：Lambda 是"可传递的函数"；Stream 是对集合的**声明式**批量操作。

#### 核心认知

Lambda 的前提是**函数式接口**（只有一个抽象方法的接口）：

```java
// 匿名内部类（老写法）
list.forEach(new Consumer<String>() {
    public void accept(String s) { System.out.println(s); }
});

// Lambda（新写法）
list.forEach(s -> System.out.println(s));
// 或方法引用
list.forEach(System.out::println);
```

常用函数式接口：`Function<T,R>`（转换）、`Consumer<T>`（消费）、`Supplier<T>`（提供）、`Predicate<T>`（判断）。

**Stream 的三段式**：

```java
list.stream()
    .filter(x -> x > 0)        // ① 中间操作（惰性，可多个）
    .map(x -> x * 2)
    .toList();                 // ② 终止操作（触发执行）
```

#### 在本项目里怎么用

`toAiHistory()` 是典型例子：

```java
return messageMapper.selectByConversationId(conversationId).stream()
        .map(m -> (Message) (ChatMessage.ROLE_USER.equals(m.getRole())
                ? new UserMessage(m.getContent())
                : new AssistantMessage(m.getContent())))
        .toList();
```

**理解 Lambda 是理解响应式的前提**——`.doOnNext(sb::append)` 里的 `sb::append` 就是方法引用，本质是个 `Consumer<String>`。

#### 常见误区

1. **在 Lambda 里修改外部局部变量。** 必须是 final 或 effectively final。
2. **以为 Stream 的中间操作会立即执行。** 惰性，遇到终止操作才跑。

#### 面试能怎么讲

> "Lambda 是函数式接口的简写，Stream 是对集合的声明式操作（中间操作惰性、终止操作触发）。我们项目 toAiHistory 就用 stream().map().toList() 把数据库记录转成协议消息。理解 Lambda 也是学响应式的基础——Reactor 的操作符参数基本都是函数式接口。"

相关词条：[#reactor-operators](#reactor-operators)、[#generic](#generic)

---

<a id="annotation-reflection"></a>

### 注解与反射

**一句话定义**：注解是"给代码加的标记"；反射是"运行时读取这些标记并操作类"的能力。

#### 核心认知

```
编译期：@Tool 注解写在方法上
          ↓（注解信息保留在 .class 里）
运行期：框架用反射扫描到这个方法
          ↓ 读取注解的 description、参数类型
          ↓ 生成 JSON Schema
          ↓ 后续反射调用该方法
```

**@Tool 能生效，全靠"注解 + 反射"这套机制**：

```java
@Tool(description = "搜索本店在售商品...")
public String searchProduct(@ToolParam(...) String keyword, ...) { }
```

Spring 启动 → 扫描 Bean → 反射找 `@Tool` 方法 → 读注解元数据 → 注册成工具。

**注解的生命周期**（`@Retention`）决定它什么时候可见：

| 策略 | 可见范围 | 例子 |
|---|---|---|
| `SOURCE` | 只在源码（编译后消失） | `@Override`、Lombok 注解 |
| `CLASS` | 保留到 class 文件 | 部分字节码工具 |
| `RUNTIME` | **运行时可用反射读取** | `@Tool`、`@Service` |

#### 在本项目里怎么用

- `@Tool` / `@ToolParam`：Spring AI 反射读取生成工具 Schema
- `@Service` / `@Mapper` / `@RestController`：Spring 反射扫描并注册 Bean
- Lombok 注解：`SOURCE` 级别，编译后就没了（这也是为什么 Lombok 不增加运行时开销）

#### 常见误区

1. **以为注解会"自动生效"。** 必须有代码去读它（框架或你自己写的处理器）。
2. **以为所有注解运行时都能读到。** 取决于 `@Retention`。

#### 面试能怎么讲

> "注解是标记，反射是运行时读取标记并操作类的能力，两者配合是框架的基础。@Tool 能生效就是这个流程：Spring 启动时反射扫描方法、读取注解元数据、生成 JSON Schema、注册工具，调用时再反射执行。要注意注解必须有人去读才有效，而且取决于 @Retention——Lombok 的是 SOURCE 级，编译后就消失了。"

相关词条：[#tool-annotation](#tool-annotation)、[#lombok](#lombok)

---

<a id="generic"></a>

### 泛型

**一句话定义**：把类型作为参数，让一份代码能安全处理多种类型。

#### 核心认知

```java
List<String> list = new ArrayList<>();    // 这个 List 只能装 String
R<ConversationVO> result;                  // 统一响应包装，data 是 ConversationVO
Flux<String> flux;                         // 流里的元素是 String
```

**泛型的好处**：编译期类型检查 + 不用强制转换。

**泛型擦除**（面试常问）：Java 的泛型在**编译后会被擦除**，`List<String>` 和 `List<Integer>` 运行时都是 `List`。这是为了兼容 Java 5 之前的代码。

#### 在本项目里怎么用

| 用法 | 例子 |
|---|---|
| 统一响应包装 | `R<T>` — `R<ConversationVO>`、`R<List<MessageVO>>` |
| 分页结果 | `PageResult<T>` — `PageResult<ProductVO>` |
| 流式类型 | `Flux<String>` — 元素是字符串 |
| 集合 | `List<Message>`、`List<ChatMessage>` |

#### 常见误区

1. **以为运行时能拿到泛型的具体类型。** 擦除了，除非通过反射的特殊手段。
2. **混淆 `List<Object>` 和 `List<String>`。** 泛型**没有协变**，`List<String>` 不是 `List<Object>` 的子类。

#### 面试能怎么讲

> "泛型是类型参数化，好处是编译期类型检查和免强转。我们项目用 R<T> 做统一响应包装、PageResult<T> 做分页、Flux<String> 表示字符串流。要注意 Java 泛型是编译期擦除的，运行时拿不到具体类型参数，而且泛型没有协变——List<String> 不是 List<Object> 的子类。"

相关词条：[#lambda-stream](#lambda-stream)

---

<a id="null-safety"></a>

### 空指针防御与 Optional

**一句话定义**：`NullPointerException` 是 Java 最常见的运行时异常；防御的核心是**不信任任何入参**。

#### 核心认知

防御的三种手段：

```java
// ① 显式判空
if (image != null && !image.isBlank()) { ... }

// ② 工具类（Spring 的 StringUtils）
StringUtils.hasText(req.getMessage())     // null / "" / "   " 都返回 false

// ③ Optional（适合返回值，不适合参数）
Optional.ofNullable(user).map(User::getName).orElse("匿名");
```

**Optional 的正确用法**：作为**返回值**表达"可能没有"，**不要**用作参数或字段（增加复杂度且序列化会出问题）。

#### 在本项目里怎么用

`ProductSearchTool` 是教科书级的防御示例——因为**模型是不可信调用者**：

```java
query.setKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim());
query.setCategoryId(categoryId != null && categoryId > 0 ? categoryId : null);
query.setPage(page == null || page < 1 ? 1 : page);
query.setPageSize(pageSize == null || pageSize < 1 ? 10 : pageSize);
```

**注意 `categoryId > 0`**：不只是判 null，还防了模型传 `0` 表示"无分类"——这是踩坑后的修复（第 4.6 节）。

#### 常见误区

1. **只在入口判空，调用链深处不判。** 外部输入要层层设防。
2. **用 Optional 包所有东西。** 过度使用反而降低可读性。
3. **忘了"业务上的无效值"。** 光判 null 不够，`0`、负数、空串也可能是非法的。

#### 面试能怎么讲

> "空指针防御的核心是'不信任任何外部输入'。我们项目最典型的是工具方法——模型作为调用者会传 null、0、负数，所以每个参数都要兜底：判空之外还要判业务合法性，比如 categoryId 必须是正数，因为模型会传 0 来表示'无分类'，只判 null 会查不到数据。"

相关词条：[#tool-schema](#tool-schema)、[#exception-handling](#exception-handling)

---

<a id="exception-handling"></a>

### 全局异常处理 @RestControllerAdvice

**一句话定义**：把 Controller 抛出的异常**集中转换**成统一的响应格式。

#### 核心认知

没有它时，每个 Controller 方法都要写 try-catch，且错误响应格式不统一。有了它：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)      // 业务异常
    public R<Void> handleBiz(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)      // 未登录（Sa-Token）
    public R<Void> handleNotLogin(NotLoginException e) {
        return R.fail(ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)              // 兜底
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.fail(ResultCode.SERVER_ERROR);     // 绝不把堆栈漏给前端
    }
}
```

**匹配规则**：Spring 找**最具体**的处理器。所以 `MethodArgumentNotValidException` 会优先匹配它的专用处理器，而不是 `BindException`（它是父类）或 `Exception`。

#### 在本项目里怎么用

**本项目一个容易误解的现象**：响应几乎都是 HTTP 200，真正的状态在 JSON 的 `code` 字段里。

```
HTTP 200  {"code":400,"msg":"请求参数错误","data":null}
HTTP 200  {"code":401,"msg":"未登录","data":null}
```

排查时别只看 HTTP 状态码。

**唯一的例外是流式接口**——200 已发出，异常只能在流内容里表达。

#### 常见误区

1. **把异常堆栈返回给前端。** 严重安全问题，只进日志。
2. **处理器顺序写错。** 子类的处理器要写在父类之前（或依赖 Spring 的最具体匹配）。
3. **以为流式接口的异常也能被它处理成 JSON。** 不能（见 [流中的错误处理](#flux-error)）。

#### 面试能怎么讲

> "@RestControllerAdvice 集中处理 Controller 异常，转成统一响应格式，避免每个方法都写 try-catch。我们项目有个特点：响应几乎都是 HTTP 200，状态放在 JSON 的 code 里，排查时要看 code 不是只看状态码。另外异常堆栈只进日志绝不返回前端。流式接口是例外——200 已发出，错误只能在流内容里说给用户听。"

相关词条：[#validation](#validation)、[#http-status](#http-status)

---

<a id="validation"></a>

### @Valid 与 Bean Validation

**一句话定义**：用注解声明字段校验规则，`@Valid` **激活**这些规则。

#### 核心认知

**最关键的一点**：写在字段上的注解**必须靠 `@Valid` 才会执行**。

```java
public List<...> list(@Valid PageQuery query) { }   // ← 不加 @Valid，注解形同虚设
```

**两个异常类型不同**（本项目踩过的坑）：

| 场景 | 抛的异常 |
|---|---|
| `@RequestBody @Valid` | `MethodArgumentNotValidException` |
| `@Valid PageQuery`（query 参数绑定） | `BindException` |

两者都要在全局异常处理器里单独处理，否则会漏成 500。

#### 在本项目里怎么用

`ChatRequest` 上**没有** `@NotBlank`——因为"message 和 image 二选一"是**跨字段条件**，注解表达不了，放在 Service 的 `validate()` 里做。这是第 9 章案例三的真实修复。

而 `PageQuery` 上有分页参数的校验：

```
GET /products?pageSize=9999  →  400 "pageSize 每页最多 100 条"
```

#### 常见误区

1. **写了注解但忘了 `@Valid`。** 完全不生效。
2. **只处理 `MethodArgumentNotValidException` 忘了 `BindException`。** query 参数校验会漏成 500。
3. **用注解表达跨字段条件。** 做不到，放 Service 层。

#### 面试能怎么讲

> "@Valid 用于激活字段上的校验注解，不加它注解不生效。要注意两种场景抛的异常不同：RequestBody 是 MethodArgumentNotValidException，query 参数绑定是 BindException，两个都要在全局处理器里配，我们项目就漏过 BindException 导致分页参数非法时返回 500 而不是 400。另外跨字段的校验规则（比如 message 和 image 二选一）注解表达不了，要放 Service 层。"

相关词条：[#exception-handling](#exception-handling)、[#pagination](#pagination)

---

<a id="mybatis"></a>

### MyBatis 基础

**一句话定义**：半自动 ORM 框架——你写 SQL，它负责把结果映射成 Java 对象。

#### 核心认知

```
Mapper 接口（方法声明）
        ↕  按 namespace + 方法名绑定
XML（具体 SQL）
```

**"半自动"的含义**：不像 Hibernate 那样自动生成 SQL，MyBatis 让你**自己写 SQL**（可见、可控、可优化）。

核心机制：

| 机制 | 说明 |
|---|---|
| `#{}` | **预编译占位符**，防 SQL 注入 |
| `${}` | 直接拼接，**有注入风险**，别用 |
| `resultType` | 结果映射到哪个类 |
| `useGeneratedKeys` | 自增主键回填 |
| `map-underscore-to-camel-case` | `created_at` → `createdAt` 自动驼峰转换 |

#### 在本项目里怎么用

`MessageMapper.xml`：

```xml
<insert id="insert" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO t_message (conversation_id, role, content, extra_json)
    VALUES (#{conversationId}, #{role}, #{content}, #{extraJson})
</insert>

<select id="selectByConversationId" resultType="com.aimall.ai.bean.ChatMessage">
    SELECT id, conversation_id, role, content, extra_json, created_at
    FROM t_message
    WHERE conversation_id = #{conversationId}
    ORDER BY created_at ASC, id ASC
</select>
```

三个要点：

1. `useGeneratedKeys` 让 INSERT 后主键回填到对象（本项目落库后要用 id）
2. `ORDER BY created_at ASC, id ASC`——**排序是业务正确性的一部分**，历史消息必须旧→新，否则模型读不懂剧情
3. XML 路径靠 `mapper-locations: classpath:mapper/**/*.xml` 扫描

#### 常见误区

1. **用 `${}` 拼参数。** SQL 注入。
2. **以为驼峰转换自动生效。** 要配 `map-underscore-to-camel-case: true`。
3. **忽略排序。** 对对话历史来说，顺序错了功能就错了。

#### 面试能怎么讲

> "MyBatis 是半自动 ORM，SQL 自己写、结果自动映射。我们用 XML 而非注解写 SQL，复杂查询可见可控。几个关键点：#{} 是预编译占位符防注入，${} 是拼接不能用；useGeneratedKeys 让自增主键回填；驼峰转换要显式配置。另外我们的历史查询 ORDER BY created_at ASC, id ASC 不是可选的——顺序错了模型就读不懂对话。"

相关词条：[#db-index](#db-index)、[#json-serialize](#json-serialize)

---

<a id="json-serialize"></a>

### Jackson 与 JSON 序列化

**一句话定义**：Spring Boot 默认的 JSON 库，负责 Java 对象 ↔ JSON 的相互转换。

#### 核心认知

两个方向：

| 方向 | 场景 |
|---|---|
| **序列化**（对象 → JSON） | Controller 返回对象时自动做 |
| **反序列化**（JSON → 对象） | `@RequestBody` 接收时自动做 |

常用注解：

| 注解 | 作用 |
|---|---|
| `@JsonIgnore` | 不参与序列化（如密码字段） |
| `@JsonProperty("name")` | 指定 JSON 字段名 |
| `@JsonFormat(pattern=...)` | 日期格式 |
| `@JsonInclude(NON_NULL)` | null 字段不输出 |

#### 在本项目里怎么用

1. **全局日期格式**（application.yml）：

```yaml
spring:
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai
```

所以 `LocalDateTime` 输出成 `"2026-08-30 12:34:56"` 而不是 ISO 格式。

2. **工具返回值序列化**：`ProductSearchTool` 用 `ObjectMapper` 手动把结果转成 JSON 字符串——因为模型只读文本。

```java
return objectMapper.writeValueAsString(resp);
```

3. **统一响应 `R<T>`**：全局异常处理器和 Controller 返回的都是它，被 Jackson 序列化成 `{"code":200,"msg":"操作成功","data":...}`。注意**成功码是 200 不是 0**（`R.CODE_SUCCESS = 200`），这是本项目 `R` 类的定义，别和"code 用 0 表示成功"的常见约定搞混。

#### 常见误区

1. **以为 `null` 字段一定不输出。** 默认会输出，要配 `@JsonInclude` 或全局配置。
2. **日期格式不统一。** 要全局配置，否则每个字段都要加注解。
3. **序列化失败导致接口 500。** 循环引用、无法序列化的类型都会。

#### 面试能怎么讲

> "Jackson 是 Spring Boot 默认的 JSON 库，负责对象和 JSON 的双向转换。我们项目配了全局日期格式和时区，所以时间统一输出 yyyy-MM-dd HH:mm:ss。另外工具方法里手动用 ObjectMapper 把查询结果序列化成 JSON 字符串——因为模型只接收文本，工具返回值就是这么传给它的。"

相关词条：[#mybatis](#mybatis)、[#exception-handling](#exception-handling)

---

<a id="flux"></a>

### Flux：0~N 个元素的异步序列

**一句话定义**：Reactor 提供的"异步数据流"类型，代表**将来会陆续到达的 0 到 N 个元素**。

#### 为什么需要它

先看本项目真正的问题：大模型生成一个回答要 10~30 秒。

传统写法是 `String answer = chatService.chat(req);` —— 方法阻塞 30 秒，这期间：

- Tomcat 线程被死死占住（默认 200 个线程，几十个用户同时问就打满）
- 前端转圈 30 秒，用户以为卡死
- 用户看不到任何中间内容，体验极差

我们想要的是：**AI 吐一个字，就推一个字给前端**。但"陆续到达的一堆字符串"用什么类型表示？

- `List<String>`：要求全部就绪才能返回，做不到"陆续"
- `Future<List<String>>`：还是等全部完成
- `String`：只有一个值

Reactor 的 `Flux<String>` 就是为这个场景设计的：**它描述"一串会随时间陆续到达的数据"，每个元素一到就能立刻处理**。

#### 它内部怎么工作：Flux 不装数据

这是理解 Flux 最关键、也最反直觉的一点。你去看源码会发现：

```java
public abstract class Flux<T> implements CorePublisher<T> {
    // 没有任何 List<T> elements 这样的字段！
}
```

**`Flux` 是个抽象类，而且没有存数据的字段。** 因为它根本不需要——它存的是"**当有人订阅时，该怎么一步步把数据生产出来**"的逻辑。

用两个类比：

| 类比 | 含义 |
|---|---|
| **菜谱 vs 菜** | `Flux` 是菜谱（步骤说明），不是做好的菜。照着做才会出菜 |
| **订阅报纸** | `Flux` 是订阅单。填了订阅单，报社才会每天送报；不订阅，一份也不会送 |

所以这句代码：

```java
Flux<String> flux = chatClient.prompt()
        .system(textSystemPrompt())
        .messages(toAiHistory(conv.getId()))
        .user(req.getMessage())
        .stream()
        .content()
        .doOnNext(sb::append);
```

**这一整串执行完，AI 一个字都还没生成。** 它只是在组装一条流水线：`.stream()` 说"我要流式"、`.content()` 说"只要正文"、`.doOnNext()` 说"每来一片就追加到 sb"。

真正的 HTTP 请求，要等到 **Spring MVC 订阅这个 Flux** 时才发出。

弹珠图（Reactor 文档的标准画法）：

```
时间轴 →
---a---b---c---|---
              ↑ 完成信号
```

每个字母是一个元素，`|` 是正常结束。元素之间的间隔就是 AI 逐字生成的时间。

#### 在本项目里怎么用

`ChatRestController` 第 96-99 行：

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestBody @Valid ChatRequest req) {
    return chatService.stream(req);
}
```

**这个 `return` 是毫秒级完成的**——方法立刻返回，此刻 AI 一个字都还没生成。随后 Spring MVC 在**返回值处理阶段**订阅这个 Flux（订阅动作本身不阻塞），随即进入 Servlet 异步、把 Tomcat 请求线程归还线程池；此后每来一个元素，由**产生数据的那个线程**（本项目是 Spring AI 内部 WebClient 的 Netty 事件循环线程）直接写一帧并 flush。

对应到 `ChatServiceImpl.stream()`（第 168-204 行），Flux 的消费侧是：

```java
return chatClient.prompt()
        .system(textSystemPrompt())
        .messages(toAiHistory(conv.getId()))
        .user(req.getMessage())
        .stream()                    // ← 与 chat() 唯一的差别：call() 换成 stream()
        .content()                   // ← Flux<String>：逐段正文
        .doOnSubscribe(s -> {        // 订阅那一刻：存用户消息 + 自动命名
            saveMessage(conv.getId(), ChatMessage.ROLE_USER, req.getMessage(), null);
            autoRenameIfDefault(conv, req);
        })
        .doOnNext(sb::append)        // 每来一片就追加
        .doOnComplete(() ->          // 流正常结束：攒齐了再存 AI 回答
                saveMessage(conv.getId(), ChatMessage.ROLE_ASSISTANT, sb.toString(), null))
        .onErrorResume(e -> {        // 出错：只能在流内容里"说"给用户听
            log.error("AI 流式问答失败: {}", e.getMessage(), e);
            return Flux.just("\n\n[AI 服务暂时不可用，请稍后再试]");
        });
```

**`.content()` 的每个元素 = 发给前端的一个 `data:` 块。** 前端 `Chat.vue` 每收到一块就 `acc += chunk` 刷新界面，于是有了打字机效果。

#### 常见误区

1. **以为 `Flux` 里装着全部回答数据。** 不是，它是流水线声明。你在 `return` 处打断点会发现方法瞬间返回，`sb` 还是空的。
2. **自己在 Service 里调 `.subscribe()`。** 错。订阅这件事交给框架（Spring MVC）去做。你自己提前订阅，等于在 Service 里就把流消费掉了，Controller 再返回时数据已经流完，还会导致重复订阅（见 [冷流与热流](#cold-hot)）。
3. **以为 `.map()` 会立刻执行。** 不会，它只是往流水线上加一道工序。所有操作符都要等订阅才激活（见 [惰性与 subscribe()](#subscribe-lazy)）。

#### 面试能怎么讲

> "流式输出我用 `Flux<String>` 承接。要注意 `Flux` 本身不存数据，它描述的是'元素会陆续到达'这件事，返回 Controller 是毫秒级的——真正的模型调用发生在 Spring 订阅之后。框架拿到 `Flux` 后，Servlet 异步机制会释放 Tomcat 请求线程，在异步线程上每收到一个元素就写一帧 SSE 给前端，所以能边生成边显示，也不会占死线程。"

相关词条：[#mono](#mono)、[#publisher-subscriber](#publisher-subscriber)、[#cold-hot](#cold-hot)、[#subscribe-lazy](#subscribe-lazy)、[#flux-in-mvc](#flux-in-mvc)

---

<a id="mono"></a>

### Mono：0~1 个元素

**一句话定义**：`Flux` 的"单数版"，代表将来会给 **0 个或 1 个**元素。

#### 为什么需要它

既然有 `Flux`（0~N），为什么还要 `Mono`（0~1）？因为**"最多一个"这个信息本身很有价值**：

- 拿到 `Mono<User>`，你知道查出来要么是空、要么是一个人，**不可能是 3 个人**——不需要写循环
- 框架能根据它做优化：返回 `Mono` 时 Spring 知道只会写一个值，不用走 SSE 分帧
- API 语义自解释：看见 `Mono<Void>` 就知道"这个操作没有返回值，只关心完成与否"

#### 它内部怎么工作

`Mono` 和 `Flux` 是兄弟，都实现同一个 `Publisher` 接口，**可以互相转换**：

| 转换 | 写法 | 说明 |
|---|---|---|
| Flux → Mono | `flux.next()` | 只取第一个元素 |
| Flux → Mono | `flux.collectList()` | 把全部元素收集成一个 `List`，包成 `Mono<List<T>>` |
| Flux → Mono | `flux.then()` | 丢弃所有元素，只关心"流完了没" |
| Mono → Flux | `mono.flux()` | 变成最多一个元素的 Flux |

#### 在本项目里怎么用

**这里有个极易混淆的点，务必看清楚**：

```java
// 非流式：返回的是 String，不是 Mono<String>！
String answer = chatClient.prompt()
        .system(textSystemPrompt())
        .user(req.getMessage())
        .call()          // ← 阻塞等待完整结果
        .content();      // ← 返回 String

// 流式：返回 Flux<String>
Flux<String> flux = chatClient.prompt()
        .system(textSystemPrompt())
        .user(req.getMessage())
        .stream()        // ← 不阻塞
        .content();      // ← 返回 Flux<String>
```

项目里 `ChatServiceImpl.chat()`（第 134-145 行）拿到的是普通 `String`，直接赋给 `String answer`——因为 `.call()` 是**阻塞**的，Spring AI 帮你在内部把异步结果等成了同步值。

所以：**本项目业务代码里其实没直接出现 `Mono`**。`Mono` 更多出现在：

- `ChatModel` 的底层接口（`ChatModel.stream()` 返回 `Flux<ChatResponse>`，内部用 `Mono` 做单次请求）
- WebClient 的请求（`WebClient.get().retrieve().bodyToMono(String.class)`）
- 你要把 Flux 聚合成单个结果时（`flux.collectList()`）

#### 常见误区

1. **以为 `.call().content()` 返回 `Mono<String>`。** 不是，是 `String`。Spring AI 的 `call()` 就是给你"同步等结果"用的。需要 `Mono` 要显式用 `Mono.fromCallable(...)` 包。
2. **用 `Mono` 装集合。** `Mono<List<User>>` 虽然合法，但语义上"一个元素（这个元素恰好是 List）"。如果数据真的是流式的，该用 `Flux<User>`。

#### 面试能怎么讲

> "`Mono` 是 `Flux` 的单数版，表示 0 或 1 个元素，两者都实现 `Publisher` 可以互转。本项目业务层其实直接用得少——非流式链路 Spring AI 的 `.call()` 已经把结果阻塞成 `String` 了，`.stream()` 才返回 `Flux`。`Mono` 主要出现在 WebClient 和底层 `ChatModel` 接口里。"

相关词条：[#flux](#flux)、[#reactor-operators](#reactor-operators)

---

<a id="publisher-subscriber"></a>

### Publisher / Subscriber / Subscription

**一句话定义**：响应式编程的四个核心接口，规定了"数据生产者"和"数据消费者"之间如何握手、如何传数据。

#### 为什么需要它

`Flux` 说"我将来会给数据"，那**谁来接收**？**怎么控制节奏**（消费者处理不过来怎么办）？**怎么知道结束了**？

这套规范（Reactive Streams）就是回答这些问题的。它定义了 4 个接口，2015 年由 Netflix、RedHat、Lightbend 等公司联合制定，**JDK 9 把它们收进了标准库**：`java.util.concurrent.Flow.Publisher` 等。

（你可以在 IDE 里对比 `org.reactivestreams.Publisher` 和 `java.util.concurrent.Flow.Publisher` 的方法签名，会发现一模一样——Reactor 只是这套标准的实现之一。）

#### 它内部怎么工作：握手四步

```
Subscriber                          Publisher
    |                                   |
    |--------- subscribe() ------------>|   ① 我要订阅
    |                                   |
    |<--- onSubscribe(Subscription) ----|   ② 给你一个"订阅凭证"
    |                                   |
    |-------- request(n) -------------->|   ③ 我最多能处理 n 个，发吧
    |                                   |
    |<-------- onNext(data) ------------|   ④ 给你一个数据
    |<-------- onNext(data) ------------|      （最多 n 个）
    |                                   |
    |-------- request(n) -------------->|   继续要
    |<-------- onNext(data) ------------|
    |                                   |
    |<-------- onComplete() ------------|   发完了（或 onError 出错）
```

四个接口各管一件事：

| 接口 | 职责 | 关键方法 |
|---|---|---|
| `Publisher` | 数据生产者 | `subscribe(Subscriber)` |
| `Subscriber` | 数据消费者 | `onSubscribe` / `onNext` / `onError` / `onComplete` |
| `Subscription` | 订阅凭证，用来**控制流量** | `request(n)` / `cancel()` |
| `Processor` | 既是生产者又是消费者（中间转换） | 两者兼具 |

**最关键的一点：第 ③ 步 `request(n)`。**

这是"拉"模型不是"推"模型——**消费者不主动要，生产者一个都不发**。这跟我们熟悉的"监听器回调"（来了就推给你）完全不同，也是背压能实现的基础。

#### 在本项目里怎么用

**你几乎不会手写 `Subscriber`**——这就是本词条的价值：让你知道谁在扮演这些角色。

| 角色 | 在本项目里是谁 |
|---|---|
| `Publisher` | Spring AI 内部的 HTTP 客户端（它从 DeepSeek 收 SSE，转换成 Flux） |
| `Subscriber` | **Spring MVC 的 `ReactiveTypeHandler`**（它订阅你的 Flux，把元素写成 SSE 帧） |
| 你自己 | **操作符组装工**——用 `.doOnNext()` 等往流水线上加钩子，不直接碰订阅 |

所以 `ChatServiceImpl.stream()` 里那些 `.doOnNext(...)`，本质是在给 Subscriber 的 `onNext` 回调**加钩子**，真正的 Subscriber 是框架。

#### 常见误区

1. **以为数据会自动流过来。** 不会，必须有人 `request(n)`。框架帮你做了，所以你感知不到。
2. **以为 `onNext` 里抛异常会被外面的 `try-catch` 抓住。** 不会。流里的异常走 `onError` 通道，要用 `onErrorResume` 等操作符处理（见 [流中的错误处理](#flux-error)）。

#### 面试能怎么讲

> "Reactive Streams 定义了四个接口：Publisher 生产、Subscriber 消费、Subscription 用 `request(n)` 控制流量、Processor 两者兼具。它是一套标准，JDK 9 的 `Flow` 类就是它。关键在于它是拉模型——消费者不 `request` 生产者就不发数据，这也是背压的基础。实际开发里我不手写 Subscriber，框架才是订阅方，我只负责用操作符组装流水线。"

相关词条：[#backpressure](#backpressure)、[#subscribe-lazy](#subscribe-lazy)

---

<a id="cold-hot"></a>

### 冷流与热流

**一句话定义**：冷流是"每次订阅都重新生产一遍数据"，热流是"数据在后台一直生产，订阅者只能收到订阅之后的部分"。

#### 为什么需要区分

这直接决定一个**很贵的后果**：你订阅几次，AI 就被调用几次、扣几次费。

#### 它内部怎么工作

| | 冷流（Cold） | 热流（Hot） |
|---|---|---|
| 数据何时产生 | **订阅时才开始** | 与订阅无关，一直在产生 |
| 每个订阅者 | 拿到**完整**的一份 | 只拿到订阅**之后**的部分 |
| 没人订阅时 | 什么都不做 | 数据照常产生（然后丢弃） |
| 类比 | 点播：你点一次播一次 | 直播：错过就没了 |
| Reactor 中 | `Flux.just/stream()/fromIterable` 等默认都是冷流 | 用 `Sinks`、`share()`、`cache()` 转成热流 |

弹珠图对比（两个订阅者先后订阅同一条冷流）：

```
数据流：   ---a---b---c---|---

订阅者1（第0秒订阅）：  ---a---b---c---|    ← 拿到全部
订阅者2（第2秒订阅）：        ---a---b---c---|   ← 也拿到全部，数据重新生产了一遍！
```

#### 在本项目里怎么用

**`stream()` 返回的是冷流**，而且是"贵"的冷流：每次订阅都会重新向 DeepSeek 发一次 HTTP 请求。

这带来一个真实风险——如果不小心订阅了两次：

```java
Flux<String> flux = chatService.stream(req);
flux.subscribe(...);           // 第 1 次订阅 → 调一次模型、扣一次费
return flux;                   // Spring 再订阅一次 → 又调一次、又扣一次
```

后果不只是扣费：`stream()` 链上的 `.doOnSubscribe()`（存用户消息）和 `.doOnComplete()`（存 AI 回答）**会各执行两次**，数据库里出现重复记录。

**这也是本项目必须警惕的坑**：不要自己 `subscribe()`，把 Flux 原样交给框架。

同理，`ChatServiceImpl.stream()` 里为什么用 `Flux.defer()` 包住图片分支？

```java
return Flux.defer(() -> Flux.just(chat(req))).onErrorResume(...);
```

`defer` 的作用就是**把"调用 chat(req)"这个动作推迟到订阅那一刻**，而不是在方法返回前就同步阻塞执行完。这跟冷流的思路一脉相承——一切都等订阅再发生。

#### 常见误区

1. **以为订阅两次只是"多读一遍数据"。** 对冷流来说是真的重新执行一遍，包括重新发 HTTP 请求、重新写数据库。
2. **滥用 `cache()` 把冷流缓存成热流。** `cache()` 会把所有元素存内存里，对无限流或大数据流就是内存泄漏。本项目这种"每次请求独立"的场景，用不上热流。

#### 面试能怎么讲

> "冷流是每次订阅都重新生产数据，热流是数据一直在产生、订阅者只拿到订阅后的部分。我们的流式接口返回的是冷流，意味着订阅一次就向模型发一次请求、扣一次费，所以代码里绝不能自己提前 `subscribe()`——必须原样交给框架，否则会重复调用模型和重复落库。"

相关词条：[#subscribe-lazy](#subscribe-lazy)、[#flux-in-mvc](#flux-in-mvc)

---

<a id="backpressure"></a>

### 背压 Backpressure

**一句话定义**：消费者告诉生产者"我一次只能处理这么多"的**流量控制机制**，防止消费者被数据淹没。

#### 为什么需要它

想象快餐取餐口：厨房（生产者）每秒出 10 个汉堡，前台（消费者）每秒只能递出 3 个。不做控制会怎样？汉堡堆满柜台，最后塌了——对应程序就是 **OOM**。

对 AI 流式场景，数据是从远端 HTTP 一点一点来的，看似不会"堆满"。但如果中间有耗时处理（比如每收到一片就写一次数据库），消费速度跟不上生产速度，数据就会在内存里堆积。

#### 它内部怎么工作

背压的实现基础就是前面说的 `request(n)`：

```
消费者：request(3)        →  "我最多处理 3 个"
生产者：onNext(x1) onNext(x2) onNext(x3)   →  恰好发 3 个，然后停下等待
消费者：（处理完）request(3)  →  "再来 3 个"
```

生产者**永远不会超过消费者申请的量**。Reactor 提供的背压策略：

| 策略 | 行为 | 适用 |
|---|---|---|
| `onBackpressureBuffer()` | 存起来慢慢消费（默认，有 OOM 风险） | 生产速度偶尔过快 |
| `onBackpressureDrop()` | 处理不过来的直接丢弃 | 允许丢数据（如实时监控采样） |
| `onBackpressureLatest()` | 只保留最新一个，丢弃中间的 | 只关心最新状态（如股价） |
| `onBackpressureError()` | 装不下就报错 | 宁可失败不能丢 |

#### 在本项目里怎么用

**诚实的结论：本项目一行背压代码都没写，因为根本不需要。**

原因有三：

1. **生产端天然慢**：AI 每秒只吐几十个 token，人类阅读速度远快于此，消费端永远跟得上
2. **消费端极轻量**：只是 `sb.append()` 追加字符串 + 刷新前端，微秒级
3. **Spring MVC 的 SSE 订阅是无界的**：它以 `Long.MAX_VALUE` 请求订阅，相当于说"有多少来多少"

所以这里**知道"为什么不需要背压"比背下那些策略更值钱**——面试时能讲清这一点，说明你理解的是原理而不是背 API。

什么样的场景才真需要背压？数据来自高速数据源（传感器、消息队列、文件流），而消费端要写数据库或调外部接口。那时才需要挑策略。

#### 常见误区

1. **以为不用背压就一定会 OOM。** 只有当生产速度持续快于消费速度时才会。AI 场景生产端是最慢的一环。
2. **以为 `request(n)` 是"每次要 n 个"的固定速率。** 它是"最多再给我 n 个"的额度，消费者可以随时补充申请。

#### 面试能怎么讲

> "背压是响应式里的流量控制，靠 `Subscription.request(n)` 实现——消费者不申请生产者就不发。它解决的是生产快于消费导致内存堆积的问题。我们这个流式场景没写背压，因为生产端是远端大模型，每秒只吐几十个 token，消费端只是追加字符串，根本不存在堆积；而且 Spring MVC 的 SSE 订阅是无界的。真需要背压的场景是消费端要写库或调外部接口、而数据源又很快的时候。"

相关词条：[#publisher-subscriber](#publisher-subscriber)、[#reactor-operators](#reactor-operators)

---

<a id="reactor-operators"></a>

### Reactor 常用操作符速查

**一句话定义**：挂在 `Flux` / `Mono` 上的方法，用来**声明**数据要经过哪些加工步骤。

#### 为什么需要它

流式编程的核心思路是：不要写"第一步做完存变量、第二步读变量"的命令式代码，而是**把加工步骤串成一条流水线**。操作符就是流水线上的工序。

#### 它内部怎么工作

先记住：**所有操作符都返回新的 Flux/Mono**（不可变），所以可以链式调用。按用途分组：

**创建类**

| 操作符 | 作用 |
|---|---|
| `Flux.just(a, b, c)` | 用固定元素创建流 |
| `Flux.fromIterable(list)` | 用集合创建流 |
| `Flux.defer(() -> ...)` | **延迟到订阅时才执行**里面的代码 |
| `Flux.error(e)` / `Flux.empty()` | 创建立即出错 / 立即结束的流 |
| `Flux.interval(Duration)` | 按时间间隔产生递增数字 |

**转换类（重点辨析）**

| 操作符 | 转换方式 | 是否保序 | 是否异步 |
|---|---|---|---|
| `map(f)` | 1 个元素 → 1 个元素 | ✅ | ❌ 同步 |
| `flatMap(f)` | 1 个 → N 个（返回流再摊平） | ❌ **不保序** | ✅ 异步并发 |
| `concatMap(f)` | 1 个 → N 个 | ✅ **保序** | ✅ 但串行 |
| `flatMapSequential(f)` | 1 个 → N 个 | ✅ 按原序收集 | ✅ 并发执行 |

> **`map` vs `flatMap`** 是面试高频。`map` 是同步一对一；`flatMap` 里返回的是流，会被"摊平"并**并发**执行，所以**结果顺序不保证**。要保序就用 `concatMap` 或 `flatMapSequential`。

**过滤类**：`filter` / `distinct` / `take(n)`（取前 n 个）/ `skip(n)` / `takeUntil`

**组合类**：`merge`（交错合并）/ `concat`（顺序拼接）/ `zip`（一一配对）/ `mergeWith`

**副作用类（`doOn*`，本项目主力）**

| 操作符 | 触发时机 | 典型用途 |
|---|---|---|
| `doOnSubscribe` | 订阅发生时 | 记日志、初始化、落库"开始"状态 |
| `doOnNext` | 每收到一个元素 | 累积数据、打印调试 |
| `doOnComplete` | 流正常结束 | 收尾、落库最终结果 |
| `doOnError` | 出错时 | **只旁观，不处理**（不能吞掉异常） |
| `doFinally` | 结束或出错都触发 | 释放资源 |

> `doOn*` 系列是**旁观者**：能看到数据但**不改变流的内容**，常用于副作用（写日志、写库）。要改数据用 `map`。

**错误类**：`onErrorResume`（换成另一个流）/ `onErrorReturn`（换成固定值）/ `onErrorMap`（换异常类型）/ `retry(n)` / `timeout(duration)`

**聚合类**：`collectList()`（收成 `Mono<List>`）/ `reduce` / `count` / `all` / `any`

**时间类**：`timeout` / `delayElements` / `buffer(Duration)` / `window`

#### 在本项目里怎么用

**好消息：本项目只用了 6 个操作符，不用背全表。**

```java
.stream()                                    // Spring AI 的，非 Reactor 原生
.content()                                   // Spring AI 的
.doOnSubscribe(s -> saveMessage(...))         // 订阅时存用户消息
.doOnNext(sb::append)                        // 每片追加到 StringBuilder
.doOnComplete(() -> saveMessage(...))         // 结束时存 AI 回答
.onErrorResume(e -> Flux.just("[AI 服务...]")) // 出错兜底
Flux.defer(() -> Flux.just(chat(req)))        // 图片分支：延迟执行
```

就这六个：`Flux.defer`、`Flux.just`、`doOnSubscribe`、`doOnNext`、`doOnComplete`、`onErrorResume`。（其中 `Flux.just` 用了两次：图片分支的 `Flux.just(chat(req))` 和兜底文案的 `Flux.just("[AI 服务暂时不可用]")`。）

#### 常见误区

1. **在 `map` 里做阻塞 IO。** `map` 是同步的，在里面查数据库会卡住整条流。异步 IO 要用 `flatMap`（且要配合合适的 Scheduler）。
2. **以为 `doOnError` 能处理异常。** 不能，它只是"看一眼"，异常会继续往下传。要处理用 `onErrorResume` / `onErrorReturn`。

#### 面试能怎么讲

> "操作符分创建、转换、过滤、组合、副作用、错误处理几类。最常被问的是 `map` 和 `flatMap` 的区别：`map` 同步一对一，`flatMap` 返回流会被摊平且并发执行、不保证顺序，要保序用 `concatMap`。另外 `doOn*` 系列是旁观者，只做副作用不改变流内容，我们项目就用它来做落库和日志。"

相关词条：[#subscribe-lazy](#subscribe-lazy)、[#scheduler](#scheduler)、[#flux-error](#flux-error)

---

<a id="subscribe-lazy"></a>

### 惰性与 subscribe()

**一句话定义**：`Flux` 的所有代码**只有在被订阅时才执行**，这就是响应式编程的"惰性"。

#### 为什么需要理解它

这是你产生"Controller 返回 Flux 到底返回了什么"这个困惑的**根源**。理解了惰性，整件事就通了。

#### 它内部怎么工作

把 `stream()` 方法里的代码按执行时机分成两类：

**① 急切执行（方法调用时立即跑，返回 Flux 之前）**

```java
validate(req);                    // 参数校验 —— 立刻执行
Conversation conv = resolveConversation(req);  // 查库/建会话 —— 立刻执行
StringBuilder sb = new StringBuilder();        // 创建对象 —— 立刻执行
```

**② 延迟执行（订阅之后才跑）**

```java
.stream()                         // 真正的 HTTP 请求 —— 订阅后才发
.doOnSubscribe(...)               // 订阅那一刻触发
.doOnNext(sb::append)             // 每来一个元素触发
.doOnComplete(...)                // 流结束时触发
```

所以时间线是这样的：

```
t=0ms    Controller 调用 stream(req)
t=1ms    validate、resolveConversation 执行完（这里已经查过一次数据库了）
t=2ms    return flux  ← 方法返回（此刻 AI 还没被调用）
t=3ms    Spring MVC 在返回值处理阶段订阅 flux（仍在请求线程上，但订阅不阻塞）
t=4ms    进入 Servlet 异步，Tomcat 线程归还线程池
t=5ms    发出 HTTP 请求给 DeepSeek（此后等待期间不占任何线程）
t=800ms  收到第一个 token → doOnNext → 前端显示第一个字
t=15s    流结束 → doOnComplete → 存 AI 回答
```

#### 怎么验证惰性

给流水线加一个 `.log()`，然后**不订阅**：

```java
Flux<String> flux = Flux.just("a", "b", "c").log();
// 什么都不做
// 控制台：一行输出都没有

flux.subscribe();
// 控制台：onSubscribe → request → onNext(a) → onNext(b) → onNext(c) → onComplete
```

**没有订阅，就没有任何输出。** 这就是惰性最直观的证明。

#### 在本项目里怎么用

正因为惰性，`stream()` 方法里那些"急切执行"的代码才要特别注意——**它们在返回 Flux 之前就已经跑完了**：

- `validate(req)` 抛异常 → 同步抛出，能被 Spring 正常捕获转成 400
- `resolveConversation(req)` 创建会话 → 会话在 AI 开口之前就已落库

这也解释了为什么图片分支要用 `Flux.defer()`：

```java
return Flux.defer(() -> Flux.just(chat(req))).onErrorResume(...);
```

如果不包 `defer`，`chat(req)` 会在**方法返回前就同步阻塞执行完**（30 秒），然后才把结果包进 Flux 返回——流式完全失效。包了 `defer`，`chat(req)` 被推迟到订阅那一刻才执行。

#### 常见误区

1. **在 Service 里 `.subscribe()`。** 前面说过，会导致重复订阅。记住：**谁消费谁订阅，业务层只组装。**
2. **以为 `defer` 让整条链都延迟。** 不是，它只延迟**它包住的那部分**。
3. **在 `doOnNext` 里写阻塞代码。** 会拖慢整条流。

#### 面试能怎么讲

> "响应式流是惰性的：没有订阅，整条链一个操作符都不会执行。这带来一个要注意的点——方法里写在返回 Flux **之前**的代码（比如参数校验、会话创建）是同步立即执行的，而流上的操作符要等订阅。我们项目里 `stream()` 的 `validate` 和 `resolveConversation` 就是同步的，模型请求和落库钩子才是订阅后触发；图片分支用 `Flux.defer` 也是为了把阻塞调用推迟到订阅时，否则流式就失效了。"

相关词条：[#flux](#flux)、[#cold-hot](#cold-hot)、[#flux-in-mvc](#flux-in-mvc)

---

<a id="scheduler"></a>

### 线程调度 Schedulers

**一句话定义**：决定响应式流**在哪个线程上执行**的调度器工厂。

#### 为什么需要它

流式代码最反直觉的地方：**你不知道它在哪个线程跑**。主线程订阅，但数据可能来自 Netty 的 IO 线程。打印一下 `Thread.currentThread().getName()` 常常会得到意外结果。

需要控制线程时（比如要在流里做阻塞 JDBC 查询），就得用 `Schedulers`。

#### 它内部怎么工作

| Scheduler | 特点 | 适用 |
|---|---|---|
| `Schedulers.immediate()` | 当前线程，不切换（默认） | 无需切换 |
| `Schedulers.single()` | 单一复用线程 | 轻量定时任务 |
| `Schedulers.boundedElastic()` | **有界弹性线程池**，按需创建，空闲回收 | **阻塞 IO（如 JDBC）** |
| `Schedulers.parallel()` | 固定 CPU 核数线程 | CPU 密集计算 |
| `Schedulers.fromExecutorService(...)` | 自定义线程池 | 特殊需求 |

两个切换方法，**极易混淆**：

| 方法 | 影响范围 | 说明 |
|---|---|---|
| `publishOn(scheduler)` | 影响它**下游**的操作符 | 可以多次调用，多次切换 |
| `subscribeOn(scheduler)` | 影响**源头**的订阅动作 | 写多个只有**最靠近源头**那个生效 |

弹珠图理解 `publishOn`（切换点在下游）：

```
源头线程:  ---a---b---c---|
                ↓ publishOn
新线程:              ---a---b---c---|
```

#### 在本项目里怎么用

**本项目一行 Scheduler 代码都没有，这是正确做法。**

原因：

- **谁订阅、在哪个线程写响应，由框架决定**。Spring MVC 在返回值处理阶段订阅（请求线程上，非阻塞），随后进入异步释放容器线程；数据到达时由**产生数据的线程**（Spring AI 内部 WebClient 的 Netty 事件循环）写 SSE
- **数据的产生线程由 Spring AI 内部的 HTTP 客户端决定**（它自己的连接池线程）
- 业务代码只做轻量的字符串追加，不需要切换线程

**什么时候才需要？** 如果你要在 `doOnNext` 里做阻塞操作（比如每收到一片就写一次 MySQL），那就要：

```java
.doOnNext(chunk -> saveFragment(chunk))
.publishOn(Schedulers.boundedElastic())   // 把阻塞操作切到弹性线程池
```

**红线：不要在不该阻塞的线程里 `.block()`**——在 Netty 事件循环、`parallel()`、`single()` 这类线程上调用会死锁或抛 `IllegalStateException: block() is blocking, which is not supported in thread reactor-http-nio-x`。在普通线程 / `boundedElastic()` 上 `block()` 是允许的（调试时常这么用，验证完记得删）。

#### 常见误区

1. **以为 `subscribeOn` 写多次会切换多次。** 只有最靠近源头的那一个生效。
2. **在响应式线程里 `block()`。** 会抛异常或造成死锁。要阻塞就切到 `boundedElastic()`。

#### 面试能怎么讲

> "`Schedulers` 控制流在哪个线程执行：`boundedElastic` 适合阻塞 IO，`parallel` 适合 CPU 密集。切换用 `publishOn`（影响下游，可多次）和 `subscribeOn`（影响源头，多个只有最靠近源头的生效）。我们项目没写 Scheduler——线程归属由框架决定，MVC 在异步线程订阅并写响应，Spring AI 的 HTTP 客户端在自己线程收流，业务代码只做字符串追加不需要切换。要记住的红线是响应式链路里不能用 `block()`。"

相关词条：[#reactor-operators](#reactor-operators)、[#webflux-vs-mvc](#webflux-vs-mvc)

---

<a id="webflux-vs-mvc"></a>

### WebFlux vs Spring MVC

**一句话定义**：Spring 的两套 Web 框架——MVC 是传统的 **Servlet 阻塞模型**（Tomcat，一请求一线程），WebFlux 是**响应式非阻塞模型**（默认 Netty，事件循环）。

#### 为什么需要区分

因为本项目**用了 `Flux` 但没用 WebFlux**——这看起来矛盾，正是你困惑的来源。

#### 它内部怎么工作

| 维度 | Spring MVC | Spring WebFlux |
|---|---|---|
| 服务器 | Tomcat / Jetty / Undertow | Netty（默认）/ 也支持 Servlet 容器 |
| 线程模型 | **一请求一线程**，阻塞则线程占住 | **事件循环**，少量线程处理大量连接 |
| 编程模型 | 同步命令式，返回具体对象 | 响应式，返回 `Mono` / `Flux` |
| 数据库 | JDBC（阻塞） | R2DBC（非阻塞）；用 JDBC 会退化 |
| 返回值 | `String` / `ResponseEntity` / **也支持 `Flux`** | `Mono` / `Flux` |
| 生态成熟度 | 极高，几乎所有库都支持 | 较弱，部分库没有响应式驱动 |
| 学习成本 | 低 | 高（调试困难、栈信息难读） |

**最关键的一条认知**：

> **"用了 `Flux`" ≠ "用了 WebFlux"。**

`Flux` 只是 Reactor 库提供的一个**数据类型**（就像 `List` 是 JDK 的集合类型）。它可以用在任何地方，包括普通的 Spring MVC 项目。

判断一个项目是不是 WebFlux，看 **pom.xml 有没有 `spring-boot-starter-webflux`**：

```xml
<!-- 本项目是这个 ↓ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>   <!-- MVC，Tomcat -->
</dependency>

<!-- 而不是这个 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

本项目 `pom.xml` 第 41-45 行是 `spring-boot-starter-web`，**没有 webflux**。而 `reactor-core` 是从 `spring-ai-starter-model-openai` **传递依赖**进来的——Spring AI 的 `ChatModel.stream()` 签名就是 `Flux<ChatResponse>`，所以它必须带 Reactor。

#### 该选哪个

| 场景 | 建议 |
|---|---|
| 传统 CRUD、团队熟悉阻塞模型 | **MVC**（绝大多数项目） |
| 高并发长连接、IO 密集、全链路都能非阻塞 | WebFlux |
| 半吊子改造（WebFlux + JDBC） | **千万别**，比 MVC 还慢 |

WebFlux 只有在**全链路非阻塞**（非阻塞数据库 + 非阻塞 HTTP 客户端 + 非阻塞缓存）时才真正更快。中间任何一环阻塞，性能优势荡然无存，还白白增加了调试难度。

#### 常见误区

1. **以为返回 `Flux` 就必须上 WebFlux。** 不用，MVC 原生支持（见下一条 [Spring MVC 如何返回 Flux](#flux-in-mvc)）。
2. **为了"高性能"盲目切 WebFlux。** 传统 CRUD 用 MVC 完全够，切了反而增加复杂度和踩坑概率。

#### 面试能怎么讲

> "WebFlux 和 MVC 是两套并列的 Web 框架，前者基于 Netty 事件循环、返回 Mono/Flux，后者基于 Servlet 一请求一线程。要注意的是'用了 Flux'不等于'用了 WebFlux'——Flux 只是 Reactor 的数据类型，MVC 也支持返回 Flux。我们项目就是 MVC + Tomcat，reactor-core 是 Spring AI 传递依赖带进来的。选型上我认为传统 CRUD 用 MVC 就够，WebFlux 要全链路非阻塞才有意义，配 JDBC 反而更慢。"

相关词条：[#flux-in-mvc](#flux-in-mvc)、[#scheduler](#scheduler)

---

<a id="flux-in-mvc"></a>

### Spring MVC 如何返回 Flux

**一句话定义**：Spring MVC 通过 `ReactiveTypeHandler` 识别返回的 `Publisher`，并用 **Servlet 异步机制**把它适配成逐步下发的响应。

#### 为什么需要它

这是"MVC 不是 WebFlux，却能流式输出"的**技术答案**。

#### 它内部怎么工作

完整链路：

```
① DispatcherServlet 调用 HandlerMethod
        ↓
② 方法返回 Flux<String>（毫秒级，数据还没产生）
        ↓
③ HandlerMethodReturnValueHandler 链中找到 ReactiveTypeHandler
        ↓
④ 检查 produces：
   - text/event-stream  → 创建 SseEmitter
   - 其他               → 创建 ResponseBodyEmitter（或收集成单值）
        ↓
⑤ 在返回值处理阶段 subscribe(flux)（仍在当前请求线程上，但订阅本身非阻塞）
        ↓
⑥ 随即 startAsync()，Tomcat 请求线程归还线程池
        ↓   （此后等待期间，这个流不占用任何线程）
⑦ 每来一个元素 onNext(x) → 由「产生数据的线程」写一帧 "data: x\n\n" 并 flush
        ↓
⑧ onComplete → 关闭连接（或 onError → 写错误内容）
```

**关于线程模型**（面试追问时的保命说法）：

> Servlet 3.0 的 `AsyncContext.startAsync()` 提供了骨架——**释放容器线程**，让响应可以延后完成；Servlet 3.1 的 `ReadListener/WriteListener` 才让 IO 真正非阻塞。Spring MVC 这条路径主要依赖 3.0 的异步。

**最关键、也最容易讲错的一点：等待期间一个线程都不占。**

订阅完成后 Tomcat 线程立刻归还线程池，在下一个 token 到达之前，**没有任何线程被这个流持有**；等数据真的到了，由**产生数据的那个线程**（本项目是 Netty 事件循环线程）执行写出。**Spring 不会给一个等待中的 SSE 流分配专属线程**——这正是 Servlet 异步的全部意义。

那 WebFlux 强在哪？区别在于**全链路**：从接收请求到数据库访问全程非阻塞，一个事件循环线程能扛成千上万个连接。MVC 只做到了"不用干等"，如果后端仍是阻塞式 JDBC，整体还是每连接一线程——所以**半吊子改造（WebFlux + JDBC）并不会更快**。

#### 在本项目里怎么用

`ChatRestController` 第 96-99 行：

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestBody @Valid ChatRequest req) {
    return chatService.stream(req);
}
```

两个要素缺一不可：

1. **返回 `Flux<String>`** → 触发 `ReactiveTypeHandler`
2. **`produces = TEXT_EVENT_STREAM_VALUE`** → 决定用 `SseEmitter`（包成 `data:` 帧）而不是普通 JSON

对比同文件里其他接口（第 59-86 行）返回 `R<...>`——那是普通对象，一次性序列化成 JSON 写完就关。

**注意流式接口不套 `R` 包装**：流一旦开始，HTTP 200 已经发出去了，没法再改成 `{code:0,data:...}` 的结构。所以错误只能在流内容里表达（这就是 `onErrorResume` 返回 `"\n\n[AI 服务暂时不可用...]"` 的原因）。

#### 怎么验证

```powershell
# -N 关掉 curl 缓冲，否则要等全部结束才显示
curl.exe -N -X POST http://localhost:8080/api/v1/chat/stream `
  -H "Content-Type: application/json" `
  -H "Authorization: 你的token" `
  --data-binary @req.json
```

你会看到 `data:` 行一行行往外蹦。相关配置：`spring.mvc.async.request-timeout`（异步请求超时，默认不限制）。

#### 常见误区

1. **漏写 `produces`。** 不写的话 Spring 不会用 SSE 格式，可能把 Flux 收集成一个 JSON 数组一次性返回。
2. **元素里有换行符。** `SseEmitter` 会把含换行的元素拆成多行 `data:`，前端按 `\n` 切分后**换行符会被吞掉**。本项目前端用 `acc += chunk` 拼接，就存在这个现象。
3. **以为异常能转成 HTTP 错误码。** 不能，200 已发出。

#### 面试能怎么讲

> "MVC 能返回 Flux 是靠 `ReactiveTypeHandler`：它识别到返回值是 Publisher，就根据 `produces` 选择 `SseEmitter` 或 `ResponseBodyEmitter`，然后调用 Servlet 异步的 `startAsync()` 释放 Tomcat 请求线程，在异步线程上订阅流，每来一个元素写一帧并 flush。准确说是 Servlet 3.0 异步提供了释放容器线程的能力，线程模型仍是一请求一异步线程，不是 WebFlux 的事件循环——但对聊天这种并发量完全够用。"

相关词条：[#flux](#flux)、[#webflux-vs-mvc](#webflux-vs-mvc)、[#flux-error](#flux-error)

---

<a id="flux-error"></a>

### 流中的错误处理

**一句话定义**：在响应式流里，**错误是一个终止信号**——一旦出错，流就此结束，不会再有后续元素。

#### 为什么需要它

传统代码用 `try-catch`。但流式代码里，异常发生在**另一个线程、另一个时刻**，包在外面的 `try-catch` 根本抓不到：

```java
try {
    return chatService.stream(req);   // 这个 try 抓不住流里的异常！
} catch (Exception e) {
    // 永远进不来
}
```

必须用 Reactor 提供的错误操作符。

#### 它内部怎么工作

| 操作符 | 行为 | 是否终止流 |
|---|---|---|
| `onErrorResume(fn)` | 出错时**换成另一个流** | 否，继续 |
| `onErrorReturn(value)` | 出错时**换成固定值**然后结束 | 是（正常结束） |
| `onErrorMap(fn)` | **换一种异常类型**继续抛 | 是（异常结束） |
| `doOnError(fn)` | **只旁观**（记日志），异常继续传 | 是 |
| `retry(n)` | 重新订阅 n 次 | 可能 |
| `retryWhen(spec)` | 按策略重试（如指数退避） | 可能 |
| `timeout(duration)` | 超时转成错误 | 是 |
| `doFinally(fn)` | 无论如何都会执行（释放资源） | — |

**关键认知**：`doOnError` 是"看一眼"，**不能吃掉异常**；要"救活"流必须用 `onErrorResume` 或 `onErrorReturn`。

#### 在本项目里怎么用

`stream()` 里有两处 `onErrorResume`：

```java
// 图片分支
return Flux.defer(() -> Flux.just(chat(req))).onErrorResume(e -> {
    log.error("AI 图片问答失败: {}", e.getMessage(), e);
    return Flux.just("[AI 服务暂时不可用]");
});

// 文本分支（流末尾）
.onErrorResume(e -> {
    log.error("AI 流式问答失败: {}", e.getMessage(), e);
    return Flux.just("\n\n[AI 服务暂时不可用，请稍后再试]");
});
```

**为什么只能"说"给用户听，不能返回错误码？** 因为 SSE 响应头（HTTP 200）在流开始时就已发出，**状态码改不了了**。这是流式接口最本质的限制。

还有一个精妙的设计：

```java
.doOnComplete(() -> saveMessage(..., sb.toString(), null))   // 只有正常结束才存
.onErrorResume(...)                                           // 出错走这里
```

**出错时 `doOnComplete` 不会触发** → 天然不会把半截话存进数据库。这就是笔记里"断流不存半截话"的实现原理。

#### 常见误区

1. **用 `try-catch` 包流式代码。** 抓不到，异常走 `onError` 通道。
2. **`onErrorResume` 放错位置。** 它只能处理**它上游**的错误。放在链末尾才能兜住全部。
3. **无脑 `retry()`。** 对大模型调用来说，重试 = **再扣一次费**，而且可能重复落库。要重试必须配合幂等设计。

#### 面试能怎么讲

> "流式里的错误是终止信号，外层 try-catch 抓不到，要用 Reactor 的错误操作符。我们用 `onErrorResume`：出错时记日志，然后返回一个只含兜底文案的流——因为 SSE 的 200 响应头已经发出去了，改不了状态码，只能在流内容里告诉用户。另外 `doOnComplete` 只在正常结束时触发，所以中途断流不会把半截回答写进数据库，这是有意的设计。"

相关词条：[#subscribe-lazy](#subscribe-lazy)、[#reactor-debug](#reactor-debug)

---

<a id="reactor-debug"></a>

### 响应式代码调试与排错

**一句话定义**：响应式流因为"声明和执行分离"，堆栈信息极难读，需要专门的工具和手法排查。

#### 为什么难

你写的 lambda（`.doOnNext(x -> ...)`）是在**装配期**定义的，但执行在**订阅后的另一个线程**。出异常时，堆栈里全是 Reactor 内部类的调用，很难定位到你写的那行代码。

#### 它内部怎么工作：可用工具

| 手段 | 用法 | 说明 |
|---|---|---|
| `.log()` | 插在链上任意位置 | 打印 onSubscribe/onNext/onComplete 全过程，**首选** |
| `.checkpoint("描述")` | 插在关键位置 | 出错时堆栈里会出现你写的描述文字 |
| `Hooks.onOperatorDebug()` | 全局开启 | 堆栈最详细，但**性能损耗大，生产禁用** |
| `StepVerifier` | 单元测试 | 用声明式断言验证流的行为 |
| 临时 `.block()` | 调试时用 | 把流转成同步值验证，确认后删掉 |
| `doOnNext(System.out::println)` | 最土但有效 | 看数据到底流没流 |

#### 在本项目里怎么用：排查清单

| 现象 | 排查方向 |
|---|---|
| **一个字都不出** | ① `produces` 是否写了 `text/event-stream`；② 前端是否正确解析 `data:` 前缀；③ curl 是否加了 `-N`（不加会缓冲） |
| **只出一段就没了** | 是否触发了 `onErrorResume`（看日志）；`timeout` 是否设太短 |
| **中途断流** | 网络问题或模型端限流；检查 `doOnComplete` 是否执行（决定有没有落库） |
| **中文乱码** | 前端 `TextDecoder` 要用增量模式 `new TextDecoder('utf-8')` 配合 `{stream: true}`，否则多字节字符被截断 |
| **数据库没记录** | `doOnComplete` 未触发（流异常结束）；或重复订阅导致写重 |
| **界面重复内容** | 重复订阅（冷流，见 [冷流与热流](#cold-hot)） |

#### 常见误区

1. **靠猜不靠 `.log()`。** 在流上插一个 `.log()`，90% 的问题立刻现形——能看到数据到底流到哪一步停了。
2. **生产环境开 `Hooks.onOperatorDebug()`。** 性能开销很大，只能在本地调试用。

#### 面试能怎么讲

> "响应式代码难调试是因为声明和执行分离，堆栈里都是框架内部类。我常用 `.log()` 插到链上看数据流转到哪一步停了，用 `checkpoint` 给关键位置加描述让堆栈可读；`Hooks.onOperatorDebug()` 堆栈最全但生产要禁用。我们项目排查流式问题有固定清单：一个字不出先查 produces 和前端解析，中途断流看 doOnComplete 有没有触发（决定落没落库）。"

相关词条：[#flux-error](#flux-error)、[#reactor-operators](#reactor-operators)

---

<a id="http-basics"></a>

### HTTP 请求/响应结构

**一句话定义**：浏览器与服务器之间"一问一答"的文本协议，每次交互由**请求报文**和**响应报文**组成。

#### 为什么需要它

第 0 章整章讲的都是"大模型 API 就是一个 HTTP 接口"。理解报文结构，你就能用 curl 手撕任何 AI 接口，也能看懂抓包结果排查问题。

#### 它内部怎么工作

**请求报文**（四部分）：

```http
POST /v1/chat/completions HTTP/1.1          ← ① 请求行：方法 路径 协议版本
Host: api.deepseek.com                       ← ② 请求头
Content-Type: application/json
Authorization: Bearer sk-xxxxxx
                                             ← ③ 空行（必须有）
{"model":"deepseek-chat","messages":[...]}   ← ④ 请求体
```

**响应报文**（四部分）：

```http
HTTP/1.1 200 OK                              ← ① 状态行：版本 状态码 原因短语
Content-Type: application/json               ← ② 响应头
Content-Length: 342
                                             ← ③ 空行
{"id":"...","choices":[...]}                 ← ④ 响应体
```

**空行是硬性的**——它告诉对方"头结束了，接下来是正文"。少一个空行，报文就解析失败。

常用方法：

| 方法 | 语义 | 幂等 | 本项目用法 |
|---|---|---|---|
| `GET` | 获取资源 | ✅ | 查会话列表、查历史消息 |
| `POST` | 创建/提交 | ❌ | 发消息、登录、下单 |
| `PUT` | 整体替换 | ✅ | （本项目未用） |
| `DELETE` | 删除 | ✅ | 删除购物车项等 |
| `PATCH` | 局部修改 | ❌ | （本项目未用） |

#### 在本项目里怎么用

项目接口统一前缀 `/api/v1`，全部返回统一的 `R<T>` 包装：

```jsonc
{
  "code": 200,          // ← 本项目成功码是 200（R.CODE_SUCCESS），不是 0
  "msg": "操作成功",
  "data": { ... }
}
```

**唯一例外是流式接口 `/chat/stream`**——它不套 `R`，直接吐 SSE 帧。原因见 [SSE 协议详解](#sse-protocol)：响应头先发出去了，没法再包一层。

#### 常见误区

1. **以为请求头和请求体之间可以没有空行。** 必须有，这是协议规定。
2. **以为 GET 不能带请求体。** 技术上可以，但很多服务器和中间件会忽略它，别这么用。
3. **混淆"幂等"和"安全"。** GET 是安全的（不改数据），POST 既不幂等也不安全。

#### 面试能怎么讲

> "HTTP 报文分请求行/状态行、头部、空行、正文四部分，空行是必需的。我们项目接口统一 /api/v1 前缀，返回统一包装的 R 对象——只有流式接口例外，它直接吐 SSE 帧不套包装，因为响应头已经先发出去了。"

相关词条：[#http-status](#http-status)、[#content-type](#content-type)、[#sse-protocol](#sse-protocol)

---

<a id="sse-protocol"></a>

### SSE 协议详解

**一句话定义**：Server-Sent Events，一种基于 HTTP 的**单向长连接**推送技术——服务器可以持续往客户端推数据。

#### 为什么需要它

普通 HTTP 是"一问一答"：客户端问，服务器答完就断。但打字机效果需要的是"**一次提问，服务器持续吐字**"。

SSE 就是为此设计的：连接建立后**保持打开**，服务器可以随时往里写数据。

#### 它内部怎么工作

**报文格式极其简单**——纯文本，每条事件几个字段，字段间换行，事件间空行：

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

data: 第一块内容

data: 第二块内容

data: 第三块内容
```

字段说明：

| 字段 | 作用 |
|---|---|
| `data:` | 数据内容（可多行，用多个 `data:` 行） |
| `event:` | 事件类型（默认 `message`，前端可按类型监听） |
| `id:` | 事件 ID（断线重连用 `Last-Event-ID` 头） |
| `retry:` | 断线后重连间隔（毫秒） |

**浏览器原生支持自动重连**——这是 SSE 相比手写长轮询的一大优势。

#### 在本项目里怎么用

**这里有个极易混淆的点，务必分清两层 SSE：**

| 链路 | 谁发给谁 | 数据格式 |
|---|---|---|
| **模型 → 后端** | DeepSeek → Spring AI | `data: {"id":"..","choices":[{"delta":{"content":"有"}}]}` |
| **后端 → 前端** | Spring MVC → 浏览器 | `data: 有` |

模型给的是**带 JSON 外壳**的（`delta.content` 才是真正的文字），Spring AI 用 `.content()` 剥掉外壳，只把纯文本转发给前端。

**另一个关键差异：`[DONE]`**

```
data: [DONE]      ← 这是 OpenAI 协议的约定，不是 SSE 标准
```

Spring 的 SSE **不会**自动写 `[DONE]`。所以本项目前端看到的是纯 `data: 文字` 序列，**流关闭就是结束信号**。如果你去看 `frontend/src/api/index.js`，会发现前端代码里有 `payload !== '[DONE]'` 的判断——那是防御性写法（万一上游带了），不是本项目后端产生的。

`ChatRestController` 的声明：

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestBody @Valid ChatRequest req) {
    return chatService.stream(req);
}
```

`produces` 是关键——它告诉 Spring 用 SSE 格式输出（详见 [Spring MVC 如何返回 Flux](#flux-in-mvc)）。

#### 常见误区

1. **以为 `data:` 后面必须有空格。** 标准允许 `data:内容` 和 `data: 内容`，浏览器都能解析。
2. **以为 SSE 会自动写 `[DONE]`。** 那是 OpenAI 的约定，不是 SSE 标准。
3. **以为流式接口能返回错误码。** 不能，200 已经发出去了（见 [流中的错误处理](#flux-error)）。

#### 面试能怎么讲

> "SSE 是基于 HTTP 的单向推送，连接保持打开，服务器持续写 `data:` 块，格式简单、浏览器原生支持自动重连。要注意我们项目里有两层 SSE：模型到后端是 OpenAI 协议的 JSON（delta.content 才是文字），后端到前端是 Spring 输出的纯文本。另外 `[DONE]` 是 OpenAI 的约定不是 SSE 标准，我们后端并不产生它，前端靠流关闭判断结束。"

相关词条：[#sse-vs-websocket](#sse-vs-websocket)、[#chunked](#chunked)、[#buffering](#buffering)

---

<a id="sse-vs-websocket"></a>

### SSE vs WebSocket vs 轮询

**一句话定义**：三种实现"服务器主动推数据"的技术方案，复杂度和适用场景递增。

#### 为什么需要对比

面试高频题："为什么用 SSE 不用 WebSocket？"——要能讲出取舍理由。

#### 它内部怎么工作

| 维度 | 短轮询 | 长轮询 | **SSE** | **WebSocket** |
|---|---|---|---|---|
| 方向 | 客户端拉 | 客户端拉 | **服务器→客户端 单向** | **双向** |
| 协议 | HTTP | HTTP | HTTP（长连接） | 独立协议 `ws://` |
| 连接 | 反复新建 | 保持但频繁重建 | **保持** | **保持** |
| 开销 | 高（大量无效请求） | 中 | 低 | 低 |
| 自动重连 | 需自己写 | 需自己写 | **浏览器原生** | 需自己写 |
| 数据格式 | 任意 | 任意 | **仅文本** | 文本+二进制 |
| 复杂度 | 低 | 中 | 低 | 高 |
| 代理/防火墙 | 无问题 | 无问题 | **无问题** | 可能被拦 |

#### 怎么选

| 场景 | 推荐 |
|---|---|
| 只需要服务器往客户端推（AI 打字机、实时通知、股价） | **SSE** |
| 需要双向实时通信（聊天室、协同编辑、游戏） | WebSocket |
| 数据更新极慢，实现要最简单 | 轮询 |

**AI 流式输出为什么选 SSE？**

1. **天然单向**：只需要模型 → 用户，用户的新问题走另一个普通 POST 请求就行
2. **基于 HTTP**：不需要额外的协议升级，Nginx、网关全都认识，不用特殊配置
3. **自动重连**：浏览器原生支持，断网恢复后自己接上
4. **实现简单**：后端返回 `Flux` 加个 `produces` 就完事

WebSocket 在这里属于"杀鸡用牛刀"——双向能力用不上，反而带来协议升级、代理配置、心跳保活等一堆麻烦。

#### 在本项目里怎么用

- **SSE**：`/api/v1/chat/stream`（流式问答，带图与纯文字都走这里）
- **普通 POST**：`/api/v1/chat`（一次返回完整回答，前端保留但不主用）、发新消息、创建会话等

注意前端**没用浏览器原生的 `EventSource`**，而是用 `fetch + ReadableStream` 手撕（见 [EventSource vs fetch](#eventsource)）——因为 `EventSource` **不支持 POST 请求和自定义请求头**，而我们需要传 Authorization。

#### 常见误区

1. **以为实时通信就必须 WebSocket。** 单向推送用 SSE 更简单。
2. **以为 SSE 只能传文本就不能传 JSON。** 可以把 JSON 字符串放进 `data:` 里，只是不能传二进制。
3. **以为用了 SSE 就不该再用 POST。** 完全可以混用：推送走 SSE，提交走 POST。

#### 面试能怎么讲

> "SSE 和 WebSocket 都能做实时推送，区别在于 SSE 是单向、基于 HTTP，WebSocket 是双向、独立协议。我们选 SSE 是因为 AI 流式输出是纯单向的——只需要模型往用户推，用户的新问题走普通 POST 就行；而且 SSE 基于 HTTP，网关和代理都天然支持，浏览器还自带自动重连，实现成本远低于 WebSocket。WebSocket 更适合聊天室、协同编辑这类真正需要双向的场景。"

相关词条：[#sse-protocol](#sse-protocol)、[#eventsource](#eventsource)

---

<a id="chunked"></a>

### Transfer-Encoding: chunked

**一句话定义**：HTTP 的一种传输编码，允许**边生成边发送**，不需要提前知道响应总长度。

#### 为什么需要它

普通 HTTP 响应要在头里声明 `Content-Length: 342`——服务器必须先知道总共多少字节。

但 AI 流式输出时，**服务器根本不知道最终会有多少字**。chunked 就是为解决这个而生的：把响应切成若干块，每块自带长度，最后发一个长度为 0 的块表示结束。

#### 它内部怎么工作

```
HTTP/1.1 200 OK
Content-Type: text/event-stream
Transfer-Encoding: chunked      ← 注意：不再有 Content-Length

5                               ← 第 1 块长度（十六进制 5 = 5 字节）
hello                           ← 第 1 块数据
6
 world
0                               ← 长度为 0 的块 = 结束
                                ← 空行
```

每块的格式：`十六进制长度\r\n 数据 \r\n`，以 `0\r\n\r\n` 结束。

#### 在本项目里怎么用

Spring 的 `SseEmitter` 在写 SSE 响应时会自动使用 chunked——你不需要手动配置。

这也是"流式"能成立的技术基础：每收到一个 token，写一帧并 flush，**不用攒够再发**。

想亲眼验证，用 curl 加 `-v` 看响应头：

```powershell
curl.exe -N -v -X POST http://localhost:8080/api/v1/chat/stream `
  -H "Content-Type: application/json" -H "Authorization: 你的token" `
  --data-binary @req.json
```

#### 常见误区

1. **以为 chunked 是 SSE 专用的。** 不是，它是 HTTP 通用机制，普通响应也能用。
2. **以为用了 chunked 客户端就必须特殊处理。** 不用，HTTP 库会自动解码，应用层看到的是完整的数据流。
3. **和 `Content-Encoding: gzip` 混淆。** 前者是传输编码（分块），后者是内容编码（压缩），两回事。

#### 面试能怎么讲

> "chunked 是 HTTP 的分块传输编码，允许服务器在不知道总长度的情况下边生成边发，每块带自己的长度，以长度 0 的块结束。它是流式输出的技术基础——AI 生成多少字事先不知道，用 chunked 就能收到一个 token 发一帧。Spring 的 SseEmitter 会自动使用它。"

相关词条：[#sse-protocol](#sse-protocol)、[#buffering](#buffering)

---

<a id="content-type"></a>

### Content-Type 与字符集

**一句话定义**：告诉对方"我发的数据是什么格式、用什么编码"的 HTTP 头部。

#### 为什么需要它

收到一串字节，不告诉你格式，你无法解析。Content-Type 就是这个声明。

**搞错的最常见后果就是中文乱码**——声明 UTF-8 实际发 GBK，或者反过来。

#### 它内部怎么工作

常见取值：

| Content-Type | 用途 | 本项目 |
|---|---|---|
| `application/json` | JSON 数据 | 所有 REST 接口 |
| `text/event-stream` | SSE 流 | `/chat/stream` |
| `application/x-www-form-urlencoded` | 表单提交 | （本项目用 JSON） |
| `multipart/form-data` | 文件上传 | （未使用） |
| `text/plain` | 纯文本 | （未使用） |

**字符集**通过 `charset` 参数声明：

```
Content-Type: application/json;charset=UTF-8
```

Spring Boot 默认对 JSON 使用 UTF-8，所以通常不用显式配。但要注意：

- **文件读写**时编码要显式指定（PowerShell 写文件默认是 GBK 或带 BOM 的 UTF-8）
- **命令行工具**传参时编码取决于终端（这就是 PowerShell 传中文给 curl 容易乱码的原因）

#### 在本项目里怎么用

1. **流式接口**：`produces = MediaType.TEXT_EVENT_STREAM_VALUE`（即 `text/event-stream`）
2. **其他接口**：Spring 根据返回对象自动设为 `application/json`
3. **前端解码**：`new TextDecoder('utf-8')` 配合 `{stream: true}` 增量模式

**中文乱码的排查顺序**：

1. 后端返回是否 UTF-8（Spring 默认就是）
2. 前端 `TextDecoder` 是否用了增量模式 `{stream: true}`——**不用 incremental 模式时，跨块的多字节字符会被截断成乱码**
3. 数据库连接的 `characterEncoding=utf8`（本项目 JDBC URL 里显式配了 `characterEncoding=UTF-8`）

#### 常见误区

1. **以为 JSON 默认就是 UTF-8 所以不用管。** 是默认，但文件读写、命令行传参这些环节仍可能出错。
2. **前端 `TextDecoder` 不传 `{stream: true}`。** 流式场景下会偶发乱码——只在多字节字符恰好跨块时出现，极难排查。
3. **分不清 `Content-Type`（我发的格式）和 `Accept`（我想要的格式）。**

#### 面试能怎么讲

> "Content-Type 声明数据格式和字符集，搞错最常见的后果是中文乱码。我们项目流式接口是 text/event-stream，其他是 application/json。踩过的坑在前端：TextDecoder 必须用增量模式 `{stream:true}`，否则多字节的中文字符在跨块时会被截断，出现偶发乱码——这种 bug 很难复现和排查。"

相关词条：[#http-basics](#http-basics)、[#readable-stream](#readable-stream)

---

<a id="http-status"></a>

### HTTP 状态码

**一句话定义**：响应头里的三位数字，表示这次请求的处理结果。

#### 为什么需要它

是排查问题的第一手线索——看到状态码就能把问题范围缩小一半。

#### 它内部怎么工作

| 类别 | 含义 | 常见码 |
|---|---|---|
| **1xx** | 信息，继续处理 | 100 Continue |
| **2xx** | 成功 | **200 OK**、201 Created、204 No Content |
| **3xx** | 重定向 | 301 永久、302 临时、304 未修改（缓存） |
| **4xx** | **客户端错误** | **400 参数错**、**401 未认证**、**403 无权限**、**404 不存在**、405 方法不允许、429 限流 |
| **5xx** | **服务器错误** | **500 内部错误**、502 网关错、503 不可用、504 网关超时 |

**最容易混淆的三兄弟**：

| 码 | 含义 | 典型场景 |
|---|---|---|
| **401 Unauthorized** | **没登录 / 认证失败** | 没带 token 或 token 失效 |
| **403 Forbidden** | **登录了但没权限** | 普通用户访问管理员接口（⚠️ 本项目未实际产生 403，越权统一走 404"会话不存在"） |
| **404 Not Found** | 资源不存在 | 路径写错、或刻意隐藏存在性 |

#### 在本项目里怎么用

本项目用 Sa-Token + 全局异常处理器统一映射：

| 场景 | 状态码 | 由谁产生 |
|---|---|---|
| 成功 | 200（`code:200`） | 正常返回（`R.ok(data)`） |
| 业务异常（如"问题不能为空"） | 200 + `code:400` | `BusinessException` → 全局处理器 |
| **未登录** | 200 + `code:401` | `NotLoginException` → `ResultCode.UNAUTHORIZED` |
| **参数解析失败** | 200 + `code:400` | `HttpMessageNotReadableException` |
| 未知异常 | 200 + `code:500` | `Exception` 兜底 |

**注意一个重要现象**：这个项目**几乎所有响应都是 HTTP 200**，真正的业务状态放在 JSON 的 `code` 字段里。

```jsonc
{ "code": 400, "msg": "请求参数错误", "data": null }
```

这是国内很常见的"业务码"风格。它简化了前端处理（不用区分 HTTP 错误和业务错误），但也意味着**排查时不能只看 HTTP 状态码，要看 `code`**。

**流式接口是例外**：它一旦开始就不再有 `code` 包装，连接关闭即结束。

#### 常见误区

1. **只看 HTTP 状态码不看业务 code。** 本项目里 HTTP 200 也可能表示业务失败。
2. **以为 401 是"没权限"。** 401 是没认证，403 才是没权限。
3. **在流式接口里指望错误码。** 200 已发出，改不了（见 [流中的错误处理](#flux-error)）。

#### 面试能怎么讲

> "状态码分五类，最常混淆的是 401 和 403——401 是没认证（没带 token 或失效），403 是认证了但没权限。我们项目有个特点：响应几乎都是 HTTP 200，真正的业务状态放在 JSON 的 code 字段里，由全局异常处理器统一映射。这样前端处理简单，但排查时要注意看 code 而不是只看 HTTP 状态码。"

相关词条：[#http-basics](#http-basics)、[#auth-token](#auth-token)

---

<a id="cors"></a>

### CORS 跨域

**一句话定义**：浏览器的安全策略——**默认禁止**网页向"不同源"的服务器发 AJAX 请求，除非服务器明确允许。

#### 为什么需要它

同源策略是浏览器最重要的安全机制之一：没有它，任何网站都能拿着你的登录态去访问你的银行页面。

但前后端分离开发时，前端 `localhost:5173`、后端 `localhost:8080`，**端口不同就是跨域**，必须配 CORS。

#### 它内部怎么工作

**同源** = 协议 + 域名 + 端口三者完全相同。

```
http://localhost:5173  vs  http://localhost:8080   → 端口不同，跨域
http://a.com          vs  https://a.com           → 协议不同，跨域
http://a.com          vs  http://b.com            → 域名不同，跨域
```

**简单请求**直接发，服务器在响应头里声明允许：

```http
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Credentials: true
```

**非简单请求**（如带自定义头 `Authorization`、或 `Content-Type: application/json`）会先发一个 **`OPTIONS` 预检请求**：

```http
OPTIONS /api/v1/chat HTTP/1.1
Origin: http://localhost:5173
Access-Control-Request-Method: POST
Access-Control-Request-Headers: authorization,content-type
```

服务器必须正确响应预检，浏览器才会发真正的请求。

#### 在本项目里怎么用

`com.aimall.config.WebConfig` 里配置了 CORS。核心要点：

| 配置项 | 作用 | 注意 |
|---|---|---|
| `allowedOriginPatterns` | 允许的来源 | 用 patterns 而非 origins 才能配合 credentials |
| `allowedMethods` | 允许的方法 | 要包含 `OPTIONS`（预检） |
| `allowedHeaders("*")` | 允许的请求头 | 本项目放行全部（含 `Authorization`） |
| `allowCredentials(true)` | 允许携带凭证 | 为 true 时 `allowedOrigins` 不能用 `*`，必须用 patterns |
| `maxAge(3600)` | 预检结果缓存秒数 | 缓存后不再每次发 OPTIONS |

**本项目实际配置**（`WebConfig.addCorsMappings`）：`allowedOriginPatterns` 放行 localhost/127.0.0.1 任意端口 + 生产域名，`allowedHeaders("*")` 放行全部头，`allowCredentials(true)`，`maxAge(3600)`。

**通用踩坑**：配了 CORS 但还是报跨域，常见原因有——`allowedHeaders` 没包含 `Authorization`（token 传不过去）；`allowCredentials(true)` 却用了 `allowedOrigins("*")`（两者冲突会报错）；没配 `maxAge` 导致每次请求都发预检、拖慢首次请求。

#### 常见误区

1. **以为跨域是后端的问题。** 是浏览器的限制，后端只是"声明允许"。curl 和 Postman 不受此限制。
2. **用 `allowedOrigins("*")` 又开 `allowCredentials(true)`。** 会报错，两者不能共存。
3. **忘了放行 `OPTIONS`。** 带自定义头的请求会卡在预检。

#### 面试能怎么讲

> "CORS 是浏览器的同源策略限制，前后端分离必须配。关键是非简单请求（带自定义头或 JSON 类型）会先发 OPTIONS 预检，后端要正确响应。我们项目的注意点是 allowedHeaders 必须放行 Authorization——因为 token 就在这个头里，不放行会导致预检失败。另外开了 allowCredentials 就不能用通配符 `*` 配 origins，得用 allowedOriginPatterns。"

相关词条：[#http-basics](#http-basics)、[#auth-token](#auth-token)

---

<a id="json-schema"></a>

### JSON Schema

**一句话定义**：用 JSON 描述"另一个 JSON 应该长什么样"的规范——字段有哪些、什么类型、是否必填。

#### 为什么需要它

**Function Calling 完全建立在它之上**：你告诉模型"有个工具叫 searchProduct，参数要符合这个 Schema"，模型才能正确生成调用参数。

第 4.2 节里 Spring AI 从 `@Tool` 注解生成的那段 JSON，就是 JSON Schema。

#### 它内部怎么工作

```jsonc
{
  "type": "object",
  "properties": {
    "keyword": {
      "type": "string",
      "description": "关键词，如'耳机'"        // ← description 是关键
    },
    "categoryId": {
      "type": "integer",
      "description": "分类id，可不传"
    },
    "page":       { "type": "integer", "description": "页码，默认1" },
    "pageSize":   { "type": "integer", "description": "每页条数，默认10" }
  },
  "required": []                              // ← 都可选，所以是空数组
}
```

常用关键字：

| 关键字 | 作用 |
|---|---|
| `type` | 类型：object / string / integer / number / boolean / array |
| `properties` | 对象有哪些字段 |
| `required` | 必填字段列表 |
| `description` | **字段说明（模型靠它理解语义）** |
| `enum` | 枚举，限定可选值 |
| `items` | 数组元素的 schema |

#### 在本项目里怎么用

`ProductSearchTool` 的注解 → Spring AI 自动生成 Schema → 发给模型：

```java
@Tool(description = "搜索本店在售商品：可按关键词、分类过滤，返回商品列表(名称/副标题/起售价)与总数。"
        + "当用户问商品、价格、或要找某类商品时调用，别自己编造商品。")
public String searchProduct(
        @ToolParam(required = false, description = "关键词，如'耳机'") String keyword,
        @ToolParam(required = false, description = "分类id，可不传") Long categoryId,
        @ToolParam(required = false, description = "页码，默认1") Integer page,
        @ToolParam(required = false, description = "每页条数，默认10") Integer pageSize) {
```

**两个注解各有分工**：

- `@Tool(description=...)` → 工具的**说明书**，决定模型"**要不要调**"
- `@ToolParam(description=...)` → 每个参数的说明，决定模型"**怎么填**"

**`required: []` 是踩坑后的设计**（第 4.6 节）：四个参数全部 `required=false`，因为必填时模型会在用户没提分类的情况下硬填 `categoryId=0` 交差，导致查询为空。

#### 常见误区

1. **以为 `description` 是给程序员看的注释。** 在 Function Calling 场景下，它是**发给模型的唯一说明**，直接决定调用质量。
2. **把所有参数设为必填。** 模型会编造值来"交差"（见上）。
3. **写得太简略。** "搜索商品"四个字不够，要写清"返回什么 + 什么时候该调"。

#### 面试能怎么讲

> "JSON Schema 用来描述 JSON 的结构，Function Calling 里它定义工具的参数契约。关键认知是：Schema 里的 description 不是给程序员看的注释，而是发给模型的唯一说明，直接决定模型会不会调、参数填得对不对。我们踩过坑——参数设成必填时模型会硬填 categoryId=0 交差导致查不到，改成全部可选加方法内兜底才稳定。"

相关词条：[#function-calling](#function-calling)、[#tool-schema](#tool-schema)

---

<a id="base64"></a>

### Base64 编码

**一句话定义**：把二进制数据转成**纯文本**的编码方式——用 64 个可打印字符表示任意字节。

#### 为什么需要它

**JSON 只能装文本，装不了二进制。** 想在 JSON 里传图片，必须先把它变成字符串，Base64 就是干这个的。

#### 它内部怎么工作

核心规则：**每 3 个字节（24 位）→ 拆成 4 组，每组 6 位 → 每组映射成一个字符**

```
原始 3 字节：  01001101  01100001  01101110   ("Man")
拆成 4×6 位：  010011  010110  000101  101110
查表得：       T       W       F       u
结果：         "TWFu"
```

**代价：体积膨胀约 1/3**（3 字节变成 4 字符，4/3 ≈ 1.333）

```
原始图片 200KB  →  base64 后约 267KB
```

不足 3 字节的尾部用 `=` 补位，所以你常看到 base64 以 `=` 或 `==` 结尾。

**URL 安全变体**：标准 Base64 用 `+` 和 `/`，在 URL 里有特殊含义，所以有把 `+` → `-`、`/` → `_` 的 URL-safe 变体。Java 里对应 `Base64.getUrlEncoder()`。

#### 在本项目里怎么用

**起点（前端）**：

```javascript
canvas.toDataURL('image/jpeg', 0.8)
// 产出 "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ..."
//       ↑ 这就是 data URL：data: + MIME + ;base64, + 编码结果
```

**终点（后端）**：

```java
byte[] bytes = decodeImage(req.getImage());   // 剥掉前缀后 Base64 解码
```

**再编码（发给模型）**：框架把字节重新 Base64 编码，拼成 `data:image/jpeg;base64,xxx` 发给模型。

所以整条链路上 Base64 **编解码了两次**（前端编码 → 后端解码 → 再编码）。这是 V1 简化方案的代价，改成对象存储传 URL 就能完全省掉。

#### 常见误区

1. **以为 Base64 是加密。** 完全不是，它是编码，任何人都能解回原文。**绝不能用它存敏感信息**。
2. **以为有压缩效果。** 相反，它会让数据变大 1/3。
3. **忘记处理换行。** MIME 规范要求每 76 字符插换行，Java 的 `Base64.getDecoder()` 遇到换行会抛异常（本项目 `decodeImage` 有兜底处理，见第 5.3 节）。

#### 面试能怎么讲

> "Base64 是把二进制转成文本的编码，原理是每 3 字节拆成 4 组 6 位再查表，代价是体积膨胀约 1/3。它解决的问题是 JSON 装不了二进制——我们传图片给模型就得靠它。要注意它不是加密，是编码，任何人都能解回原文；另外 MIME 规范会插入换行，Java 的严格解码器遇到换行会抛异常，我们代码里对这种情况做了兜底。"

相关词条：[#dataurl-blob](#dataurl-blob)、[#vision-token](#vision-token)

---

<a id="auth-token"></a>

### Token 认证与 Authorization 头

**一句话定义**：登录成功后服务器发一个凭证字符串，之后每次请求带上它来证明"我是谁"。

#### 为什么需要它

HTTP 是**无状态**的——服务器不会记得你上一次请求是谁。所以每次请求都必须自带身份凭证。

#### 它内部怎么工作

```
① 登录：POST /api/v1/auth/login {username, password}
        ↓ 验证通过
② 服务器生成 token（本项目是 UUID），存起来 {token → userId}
        ↓ 返回
③ 前端保存（本项目 localStorage）
        ↓ 之后每次请求
④ 请求头带上：Authorization: 33d60866-cdb6-4179-8508-163022e33e10
        ↓
⑤ 服务器拦截器查表：这个 token 对应 userId=1 → 放行
```

**Authorization 头的常见格式**：

```
Authorization: Bearer eyJhbGciOi...     ← JWT 常用，带 Bearer 前缀
Authorization: 33d60866-cdb6-...        ← 本项目，直接是 token 值
```

本项目用 Sa-Token，默认 `token-name: Authorization`，`token-style: uuid`。注释里特别说明 Sa-Token **兼容 "Bearer " 前缀**。

#### 在本项目里怎么用

- `StpUtil.login(userId)` 发卡
- `StpUtil.getLoginIdAsLong()` 读卡（内部从 ThreadLocal 里的 request 取，见第 9 个知识点）
- 拦截器自动校验，失败抛 `NotLoginException` → 全局处理器转 `code: 401`

**测试时怎么拿 token**：先调登录接口，从响应里复制 `data` 里的 token 值。

#### 常见误区

1. **把 token 放在 URL 参数里。** 会被日志记录、被浏览器历史保存，不安全。
2. **混淆 token 和 session。** Session 靠 Cookie 自动带，token 要前端显式加到请求头。
3. **前端存 localStorage 不考虑 XSS。** 生产环境要评估风险（HttpOnly Cookie 更安全，但本项目为简单用了 localStorage）。

#### 面试能怎么讲

> "HTTP 无状态，所以每次请求都要自带凭证。我们项目用 Sa-Token：登录成功后服务端存 token 到 userId 的映射，返回 uuid 形式的 token，前端存 localStorage，之后的请求放在 Authorization 头里，拦截器查表确认身份，失败统一返回 401。Sa-Token 还能兼容 Bearer 前缀。"

相关词条：[#http-status](#http-status)、[#cors](#cors)

---

<a id="buffering"></a>

### 缓冲与流式传输（curl -N）

**一句话定义**：数据在传输途中被暂存起来"凑够一批再发"，就是缓冲；关掉它才能看到实时效果。

#### 为什么需要它

缓冲能大幅提升吞吐量（减少系统调用次数），**但它会毁掉流式体验**——数据被攒着不发，你看到的就不是"一点一点出来"，而是最后一次性全出来。

#### 它内部怎么工作

**你可能遇到的好几层缓冲**：

| 层 | 表现 | 怎么关 |
|---|---|---|
| **curl 输出缓冲** | 数据到了但没打印 | `curl -N`（`--no-buffer`） |
| **代理/网关缓冲** | Nginx 默认会缓冲代理响应 | `proxy_buffering off;` |
| **应用层缓冲** | 框架攒够再 flush | Spring 的 SseEmitter 默认每写一帧就 flush |
| **浏览器缓冲** | 收到但还没渲染 | 前端逐块处理即可 |

#### 在本项目里怎么用

**测试流式接口必须加 `-N`**：

```powershell
curl.exe -N -X POST http://localhost:8080/api/v1/chat/stream `
  -H "Content-Type: application/json" `
  -H "Authorization: 你的token" `
  --data-binary @req.json
```

不加 `-N` 的现象：等 20 秒，然后**一次性**把全部内容刷出来——你会误以为流式没生效。

**这个项目调试时的经典误判**：

```
"我调 /chat/stream 没看到打字机效果，是不是代码有问题？"
→ 90% 的情况是没加 -N，或者中间有代理在缓冲
```

#### 常见误区

1. **以为不加 `-N` 也能看到效果。** curl 默认会缓冲输出。
2. **忘了中间还有 Nginx。** 生产环境流式不生效，多半是 `proxy_buffering` 没关。
3. **用 Postman/Apifox 测流式。** 它们默认会等响应结束才渲染，容易让你误判"流式没生效"——用 `curl -N` 或直接看响应流更可靠。

#### 面试能怎么讲

> "流式输出要小心各层缓冲：curl 要加 -N 关掉输出缓冲，不然会一次性刷出来让人误以为流式没生效；生产环境还要注意 Nginx 的 proxy_buffering 默认是开的，会缓冲代理响应导致流式失效。我们调试时踩过这个——看着没效果，实际是工具的问题不是代码的问题。"

相关词条：[#sse-protocol](#sse-protocol)、[#chunked](#chunked)

---

<a id="llm"></a>

### 大语言模型 LLM 是什么

**一句话定义**：一个在海量文本上训练出来的神经网络，核心能力只有一个——**根据前文预测下一个字**。

#### 为什么需要理解这一点

几乎所有你困惑的 AI 现象，都能从"预测下一个字"推导出来：

- 为什么它没有记忆？因为它只接收你这次发来的文本
- 为什么会胡编乱造？因为它只追求"通顺"，不追求"真实"
- 为什么多轮对话要重发历史？因为它每次都是"第一次见你"
- 为什么 temperature 调低更稳定？因为它在概率分布里选最稳妥的那个字

#### 它内部怎么工作（极简版）

```
输入："今天天气"
   ↓ 模型内部计算（Transformer，几十亿到万亿参数）
输出：所有可能下一个字的概率分布
   "很"  35%
   "真"  20%
   "不"  15%
   ...（词表几万到十几万个候选）
   ↓ 按策略选一个（受 temperature 影响）
输出："很"
   ↓ 把"很"拼回输入，再预测下一个
   "今天天气很" → "好"
```

**它每次只吐一个字（token），然后把自己刚吐的字拼回去再吐下一个**——这就是为什么生成需要时间，也是为什么能"流式输出"（每生成一个就能立刻发给你）。

#### 三个必须分清的概念

| 概念 | 是什么 | 本项目用的 |
|---|---|---|
| **模型**（Model） | 训练好的那套参数，如 GPT-4、DeepSeek-V3 | `deepseek-v4-flash-vision-exp` |
| **厂商/服务** | 提供模型调用的公司，如 OpenAI、DeepSeek、阿里 | DeepSeek |
| **框架** | 帮你调用模型的代码库 | Spring AI |

换模型 ≠ 换框架。本项目 base-url 从 DeepSeek 官方换到中转站，只改了两行 yml，Java 代码一行没动——这就是框架的价值。

#### 常见误区

1. **以为 AI "知道"什么。** 它不知道任何事，只是在算概率。你觉得它"知道"，是因为训练数据里有很多相关内容。
2. **以为它在"思考"。** 对非推理型（non-reasoning）模型，它不是先想好再说，而是边说边算。但要注意：推理型模型（如 DeepSeek-R1、o1）会**先产出内部思维链再回答**，两者机制不同。
3. **以为问同一个问题会得到完全一样的答案。** 默认有随机性（temperature > 0），每次都可能不同。

#### 面试能怎么讲

> "大模型的本质是根据前文预测下一个 token，每次只生成一个然后拼回去继续。理解这一点就能解释很多现象：它没有记忆、会编造内容、多轮对话要靠我们重发历史。选型上我会区分模型、厂商和框架三层——我们用的是 DeepSeek 的模型，通过 Spring AI 框架调用，换厂商只改配置。"

相关词条：[#token](#token)、[#inference](#inference)、[#hallucination](#hallucination)

---

<a id="token"></a>

### Token：模型的计量单位

**一句话定义**：模型处理文本的最小单位，**不等于字、不等于词**，大致是"一个词缀/常见词组"。

#### 为什么需要它

模型不认识"字"，只认识数字。它需要把文本切成一个个编号才能计算，这些编号单元就是 token。

**同时，token 就是钱**：所有大模型 API 都按 token 计费，分输入（prompt_tokens）和输出（completion_tokens）两类。

#### 它内部怎么工作

英文大致规律：**1 token ≈ 4 个字符 ≈ 0.75 个单词**

```
"Hello world"        → ["Hello", " world"]        = 2 tokens
"unbelievable"       → ["un", "believ", "able"]   = 3 tokens
```

中文规律：**约 1 个汉字 ≈ 1~2 个 token**（现代模型对中文做了优化，常见字基本 1 字 1 token；生僻字、emoji、特殊符号才可能拆成 2~3 个。具体取决于模型的分词器，做成本估算时以对应模型的分词器实测为准）

```
"推荐一款降噪耳机"    → 大约 6~10 tokens
```

**判断 token 数最可靠的方式是用模型的分词器实际跑一遍**，不要凭字数估算。

计费示意（以响应里的 `usage` 字段为准）：

```jsonc
"usage": {
  "prompt_tokens": 52,        // 输入：system prompt + 历史 + 本次问题
  "completion_tokens": 89     // 输出：AI 的回答
}
```

**为什么输出比输入贵？** 因为输入可以并行计算，输出必须一个一个生成（前面说过，它是逐个预测的）。

#### 在本项目里怎么用

本项目几个和 token 直接相关的设计：

1. **商品数据不塞进 prompt**（第 4 章）：全量注入会导致 prompt_tokens 爆炸，改成工具按需查询
2. **历史消息全量带上**（第 3 章）：这是**债务**——对话越长 prompt_tokens 越多，成本线性增长
3. **图片按尺寸计 token**（见 [图片的 token 计费](#vision-token)）：这是前端必须压缩的原因
4. **工具返回值精简**：`ProductSearchTool` 只返回 name/subTitle/minPrice/id 四个字段，注释里明确写了"工具返回值也是要计入 token 的，能省则省"

#### 常见误区

1. **以为 token = 字数。** 差得远，尤其中文和英文差异很大。
2. **只算输入不算输出。** 输出往往更贵（生成慢、占用算力多）。长回答比长问题烧钱。
3. **忽略历史累积。** 多轮对话的 prompt_tokens 是**累加**的，第 20 轮的请求带上前 19 轮全部内容。

#### 面试能怎么讲

> "token 是模型处理文本的最小单位，也是计费单位，中文一般一个字一到两个 token。我们项目有几个省 token 的设计：商品数据不塞 prompt 而是工具按需查、工具返回值只挑回答用得上的四个字段、图片按尺寸计费所以前端先压缩。还有个待优化点是历史消息目前全量带，对话长了 prompt 会线性膨胀，后面可以改滑窗或摘要。"

相关词条：[#tokenizer](#tokenizer)、[#context-window](#context-window)、[#vision-token](#vision-token)

---

<a id="tokenizer"></a>

### 分词器 Tokenizer

**一句话定义**：把文本切成 token 并编号的那道工序，模型的前置处理环节。

#### 为什么需要它

模型是数学函数，只能吃数字。分词器负责"文本 ↔ 数字"的双向翻译。

#### 它内部怎么工作

现代模型普遍用 **BPE（Byte Pair Encoding，字节对编码）** 类算法。核心思路：

1. 从单个字符起步
2. 统计语料里最常一起出现的相邻组合，合并成一个新 token
3. 反复迭代，直到词表达到预定大小（几万到十几万）

结果就是：**常见词整体是一个 token，罕见词被拆成词缀**

```
"playing"  → ["play", "ing"]          常见的组合会合并
"DeepSeek" → ["Deep", "Seek"]         专有名词常被拆
```

分词器带来的有趣现象：

- **AI 不擅长数字母**：因为它看到的不是字母序列，而是 token 序列。问"strawberry 里有几个 r"，容易答错
- **中文通常更费 token**：同样意思的内容，中文的 token 数常高于英文
- **不同模型分词器不同**：同一段文本在 GPT 和 DeepSeek 上 token 数不一样，价格不能直接比

#### 在本项目里怎么用

业务代码**不需要直接调分词器**，但要知道：

- 想精确估算成本，要用对应模型的分词器工具
- 写 prompt 时，**中英文的"性价比"不同**
- 工具返回的 JSON 字段名也会占 token——这就是 `ProductSearchTool` 只返回 4 个短字段的原因之一

#### 常见误区

1. **以为换个模型 token 数一样。** 不同分词器结果不同，切换模型要重新评估成本。
2. **以为空格不算 token。** 算，空格也会编码。

#### 面试能怎么讲

> "分词器负责文本和 token 编号的双向转换，现代模型多用 BPE 算法——把高频相邻字符组合合并成 token。所以常见词是一个 token，生僻词会被拆成好几段。这也解释了为什么 AI 不擅长数字母：它看到的根本不是字母序列。我们项目不直接调分词器，但做成本估算和 prompt 优化时要考虑它。"

相关词条：[#token](#token)、[#context-window](#context-window)

---

<a id="context-window"></a>

### 上下文窗口 Context Window

**一句话定义**：模型一次能"看到"的 token 上限，包含**输入 + 输出**的全部内容。

#### 为什么需要它

模型的注意力机制需要同时处理所有输入 token，**能处理多少是有物理上限的**。超出窗口的内容它真的看不到。

#### 它内部怎么工作

```
┌─────────────── 上下文窗口（如 64K tokens）───────────────┐
│ system prompt │ 历史对话 │ 本次问题 │ ←  输出也占这里 →  │
└──────────────────────────────────────────────────────────┘
```

关键点：

1. **输出也占用窗口**。输入占了 60K，剩下 4K 就是回答的极限长度
2. **超出会怎样**：轻则被截断（前面的内容被丢弃），重则直接报错
3. **"窗口内"不等于"都能用好"**：长上下文的"大海捞针"问题——内容放在开头和结尾容易被注意到，放中间容易被忽略

**不同模型窗口差异巨大**：

| 类型 | 典型窗口 |
|---|---|
| 早期 GPT-3.5 | 4K / 16K |
| GPT-4o、DeepSeek 等主流 | 64K~128K（以厂商当期文档为准） |
| 部分长文本模型 | 1M+ |

#### 在本项目里怎么用

**这是本项目的一个真实隐患**：`toAiHistory()` 查出**全部**历史，不带任何限制。

```java
.messages(toAiHistory(conv.getId()))   // 第 20 轮时，前 19 轮全带上
```

对话越长，prompt_tokens 越多，直到：

- 成本飙升（每轮都重发全部历史）
- 最终撞上窗口上限，请求失败

业界常见的应对策略：

| 策略 | 做法 | 特点 |
|---|---|---|
| 全量历史 | 每次带全部 | 简单，但会撑爆 ← **本项目现状** |
| **滑窗** | 只带最近 N 轮 | 最常用，性价比高 |
| 摘要压缩 | 早期对话总结成一段话 | 保留长期记忆 |
| 向量检索 | 按相关性取历史片段 | 适合超长历史 |

最小改动的滑窗（SQL 层面）：

```sql
SELECT * FROM (
    SELECT * FROM t_message
    WHERE conversation_id = #{conversationId}
    ORDER BY id DESC
    LIMIT 20                    -- 最近 20 条 ≈ 10 轮
) t
ORDER BY id ASC                 -- 外层必须再正序：模型要的是旧→新
```

#### 常见误区

1. **以为窗口是"对话条数"限制。** 是 token 数，不是条数。长消息占得多。
2. **以为窗口足够大就可以无脑塞。** 又贵又慢，还可能有"大海捞针"问题。

#### 面试能怎么讲

> "上下文窗口是模型一次能处理的 token 上限，输入输出都算。我们项目目前是每次带全量历史，这在短对话没问题，但对话长了 prompt 会线性膨胀，成本和超限风险都上升。演进方案是滑窗只带最近 N 轮，或者对早期对话做摘要压缩——具体选哪个取决于产品能不能接受'超过 N 轮就忘掉开头'。"

相关词条：[#token](#token)、[#prompt](#prompt)

---

<a id="prompt"></a>

### Prompt 与提示词工程

**一句话定义**：你发给模型的全部输入文本，以及"如何把这些文本组织好"的方法论。

#### 为什么需要它

模型是"预测下一个字"的机器，**你给什么前文，它就往什么方向续写**。前文（prompt）的质量直接决定输出质量。

同一个模型，prompt 写得好不好，效果可能天差地别。

#### 它内部怎么工作：最小方法论

一个有效的 prompt 通常包含三到五个要素：

| 要素 | 作用 | 本项目示例 |
|---|---|---|
| **角色** | 限定身份和语气 | "你是「AI 种草助手」，商城导购" |
| **任务/行为约束** | 说清要做什么、怎么做 | "先调用 searchProduct 工具按需搜索，再基于返回结果如实回答" |
| **输出格式** | 要求特定结构 | （本项目未强制，但常用） |
| **红线** | 明确禁止什么 | "商品库里没有就坦诚说明，不要编造不存在的商品" |
| **示例**（可选） | 给几个例子让它照着做 | （少样本学习） |

本项目文本链路的 system prompt（第 2.2 节）：

```
你是「AI 种草助手」，商城导购。用户询问商品/价格/找某类商品时，
先调用 searchProduct 工具按需搜索，再基于返回结果如实回答（给名称、价格、卖点）。
商品库里没有就坦诚说明，不要编造不存在的商品或参数。
```

三要素齐全：角色（导购）+ 行为约束（先查工具再答）+ 红线（不许编造）。

**几个实用技巧**：

1. **具体 > 抽象**："推荐 300-500 元的降噪耳机" 好过 "推荐耳机"
2. **给退路**：明确说"不知道就说不知道"，能显著减少幻觉
3. **约束输出格式**：要 JSON 就明确给 JSON 示例
4. **迭代**：prompt 是调出来的，不是一次写对的

#### 在本项目里怎么用

- `ChatServiceImpl.textSystemPrompt()`：文本链路（带工具引导）
- `ChatServiceImpl.visionSystemPrompt()`：视觉链路（识图任务）
- 两者**刻意分开**——因为视觉模型不挂工具，prompt 里不能提"调用工具"

#### 常见误区

1. **以为 prompt 越长越好。** 冗长且无结构的 prompt 反而稀释重点，还费钱。
2. **以为写一次就够了。** prompt 需要针对实际输出反复调整，这是工程活。
3. **把所有规则堆在 system prompt。** 工具该不该调用，主要靠工具的 `description`（见 [工具 Schema 与 description 写法](#tool-schema)）。

#### 面试能怎么讲

> "prompt 工程我用的最小方法论是三要素：角色、行为约束、红线。我们项目的 system prompt 就是这三样——先说你是导购，再要求先查工具再回答，最后明确不许编造。实践里体会最深的是'给退路'很重要，明确告诉模型不知道就说不知道，幻觉会明显减少。另外 prompt 是迭代出来的，不是一次写对的。"

相关词条：[#system-prompt](#system-prompt)、[#hallucination](#hallucination)、[#tool-schema](#tool-schema)

---

<a id="system-prompt"></a>

### system / user / assistant 三种角色

**一句话定义**：OpenAI 协议给每条消息打的"身份标签"，模型据此理解这段文本的地位。

#### 为什么需要它

如果没有角色区分，模型无法判断哪句是"规则"、哪句是"用户说的"、哪句是"我（AI）之前说过的"。

#### 它内部怎么工作

| 角色 | 地位 | 什么时候用 | 特点 |
|---|---|---|---|
| **system** | 全局规则（约定上承载，模型通常更倾向遵循，但非强制） | 对话开始时发一次 | 定义 AI 的人设和约束 |
| **user** | 用户说的话 | 每轮提问 | 模型要回应的对象 |
| **assistant** | AI 之前说过的话 | 多轮对话的历史 | 让模型记得自己说过什么 |
| **tool**（Function Calling 用） | 工具执行结果 | 第二轮请求 | 带 `tool_call_id` 关联 |

一次完整的请求结构：

```jsonc
{
  "messages": [
    { "role": "system",    "content": "你是「AI 种草助手」..." },
    { "role": "user",      "content": "有没有降噪耳机" },
    { "role": "assistant", "content": "有的，推荐 AirSound Pro..." },
    { "role": "user",      "content": "它续航多久" }        // 本次新问题
  ]
}
```

**为什么 assistant 消息要回传？** 因为模型没有记忆。你不告诉它"你上次推荐了 AirSound Pro"，它根本不知道自己说过什么。

#### 在本项目里怎么用

`toAiHistory()` 干的就是这件事（第 3.3 节）：

```java
ChatMessage.ROLE_USER.equals(m.getRole())
        ? new UserMessage(m.getContent())          // 用户发言 → UserMessage
        : new AssistantMessage(m.getContent())     // AI 发言 → AssistantMessage
```

对应到实体 `ChatMessage` 的常量：

```java
public static final String ROLE_USER = "user";
public static final String ROLE_ASSISTANT = "assistant";
```

注释里特意说明了：**取值刻意与 OpenAI 协议的 role 一致**，这样从 DB 记录转成协议消息时不需要维护一张"角色翻译表"。

而 system prompt 是每次请求单独加的（`.system(textSystemPrompt())`），不存数据库。

#### 常见误区

1. **把规则写在 user 消息里。** 应该放 system，它的优先级更高更稳定。
2. **以为 system 只发一次就永远生效。** 每次请求都要重新带上，模型没有记忆。
3. **忘记回传 assistant 历史。** 模型会"失忆"，重复推荐同一件商品。

#### 面试能怎么讲

> "协议里消息分四种角色：system 定规则、user 是用户输入、assistant 是 AI 的历史回答、tool 是工具结果。因为模型没有记忆，每次请求都要把 system 和历史消息全部重发。我们项目存消息的 role 字段刻意用和协议一致的取值，这样 DB 记录转协议消息时不需要翻译表。"

相关词条：[#prompt](#prompt)、[#function-calling](#function-calling)

---

<a id="temperature"></a>

### temperature 与 top_p

**一句话定义**：控制模型输出**随机程度**的参数——低则稳定保守，高则发散有创意。

#### 为什么需要它

模型每次预测都给出一个概率分布。选哪个字？

- 永远选概率最高的 → 输出确定但呆板，同样的输入永远同样的输出
- 完全随机 → 胡言乱语
- 中间地带 → 需要你来调

#### 它内部怎么工作

**temperature（温度）**：除以模型原始的 logits（未归一化的对数概率）再 softmax

```
softmax:  p_i = exp(z_i / T) / Σ_j exp(z_j / T)

T→0   分布趋近 one-hot（等价取概率最高的字）→ 更确定
T=1   原始分布
T>1   分布被拉平 → 更随机
```

**top_p（核采样）**：另一种思路——只在累积概率达到 p 的候选里选

```
top_p = 0.1 → 只在概率最高的少数几个候选里选
top_p = 0.9 → 候选池大得多
```

**实践建议：两个参数一般只调一个**，同时调容易互相干扰。OpenAI 官方建议优先用 temperature。

| 场景 | 推荐值 | 理由 |
|---|---|---|
| 信息抽取、分类、代码生成 | 0 ~ 0.3 | 要确定性 |
| 常规问答、对话 | 0.5 ~ 0.8 | 平衡 |
| 创意写作、头脑风暴 | 0.9 ~ 1.2 | 要发散 |

#### 在本项目里怎么用

**两条链路用了不同的值**，这是有意的：

```java
// 文本链路：application.yml 配置
temperature: 0.7          // 推荐商品需要一点灵活度，话术才不死板

// 视觉链路：代码里按次覆盖
.options(OpenAiChatOptions.builder()
        .model(VISION_MODEL)
        .temperature(0.5)    // 识图要稳，温度更低
        .build())
```

**为什么识图温度更低？** 识别任务是"客观描述"，要准确不要发挥；推荐话术需要一点变化才自然。

#### 常见误区

1. **以为 temperature 是"准确度"旋钮。** 它是**发散度**旋钮。调低不会让答案更正确，只会更保守（可能稳定地答错）。
2. **以为 temperature = 0 就完全确定。** 大部分情况下接近确定，但由于浮点计算和批处理，仍可能有微小差异。
3. **两个参数同时乱调。**

#### 面试能怎么讲

> "temperature 控制采样时的随机程度，本质是缩放概率分布——低温放大差距让输出确定，高温缩小差距让输出发散。要注意它是发散度旋钮不是准确率旋钮，调低不会让答案更对。我们项目两条链路取值不同：文本推荐 0.7 让话术自然，视觉识别 0.5 要的是准确稳定。"

相关词条：[#max-tokens](#max-tokens)、[#prompt](#prompt)

---

<a id="max-tokens"></a>

### max_tokens / n / stop 等参数

**一句话定义**：除 temperature 外，控制生成行为的常用请求参数。

#### 为什么需要它们

光有 prompt 和 temperature 不够，还需要控制"生成多长""生成几个""什么时候停"。

#### 它内部怎么工作

| 参数 | 作用 | 说明 |
|---|---|---|
| `max_tokens` | 本次生成的最大 token 数 | **硬上限**，撞上就截断（`finish_reason: "length"`） |
| `n` | 生成几个候选答案 | 默认 1，配合 `choices` 数组使用 |
| `stop` | 遇到指定字符串就停 | 可以是字符串或数组 |
| `presence_penalty` | 惩罚"已出现过的词" | 正值鼓励聊新话题 |
| `frequency_penalty` | 惩罚"出现频次高的词" | 正值减少重复啰嗦 |
| `stream` | 是否流式返回 | true 时走 SSE |
| `tools` | 可用工具列表 | 见 [Function Calling](#function-calling) |
| `seed` | 随机种子 | 相同时输出更可复现（不保证） |

**`finish_reason` 与 `max_tokens` 的关系**（排错要点）：

```
"finish_reason": "stop"      → 模型正常说完
"finish_reason": "length"    → 撞到 max_tokens 被硬截断！
"finish_reason": "tool_calls" → 模型要调工具
"finish_reason": "content_filter" → 被内容安全策略拦截
```

**回答莫名缺尾巴时，第一件事就是查 `finish_reason` 是不是 `length`。**

#### 在本项目里怎么用

本项目通过 Spring AI 的 `OpenAiChatOptions` 设置：

```java
.options(OpenAiChatOptions.builder()
        .model(VISION_MODEL)
        .temperature(0.5)
        .build())
```

大部分参数用默认值（`application.yml` 里的 model + temperature），只在视觉链路按次覆盖。

`n` 参数默认 1 —— 这就是为什么 `choices` 数组"几乎总是长度为 1"（第 0.3 节讲过）。

#### 常见误区

1. **以为 `max_tokens` 是"期望长度"。** 它是上限不是目标，模型可能提前说完。
2. **回答被截断却去改 prompt。** 先看 `finish_reason`。
3. **把 `max_tokens` 设得极大。** 不会让回答变长，只会让失控的回答更长（更费钱）。

#### 面试能怎么讲

> "常用的还有 max_tokens、n、stop 这几个。max_tokens 是生成的硬上限，撞上会被截断，响应里 `finish_reason` 会变成 `length` 而不是 `stop`——排查'回答缺尾巴'时先看这个字段。n 控制生成几个候选，默认 1，这也是为什么 choices 数组几乎总是只有一个元素。"

相关词条：[#temperature](#temperature)、[#context-window](#context-window)

---

<a id="hallucination"></a>

### 幻觉 Hallucination

**一句话定义**：模型生成**看似合理但与事实不符**的内容，而且说得非常自信。

#### 为什么需要重视

因为模型的目标是"生成通顺的续写"，不是"陈述事实"。当它不知道答案时，最"通顺"的做法往往是——**编一个**。

对电商这种场景，幻觉的后果很直接：推荐了不存在的商品、报了错误的价格、承诺了没有的功能。

#### 它内部怎么工作：为什么会产生幻觉

1. **训练数据里没有相关知识**，但模型必须给出"下一个字"
2. **参数知识会过时**：模型训练截止日期之后的事它不知道
3. **提示误导**：问题里隐含错误前提，模型会顺着说
4. **采样随机性**：temperature 高时更容易偏离

#### 怎么缓解（本项目用了两层 + 一处稳定性考量）

| 层 | 做法 | 本项目实现 |
|---|---|---|
| **给真实数据** | 让模型基于查到的真实结果回答 | `searchProduct` 工具查 MySQL |
| **prompt 红线** | 明确禁止编造 | "商品库里没有就坦诚说明，不要编造不存在的商品或参数" |

**最有效的是第一层**——这就是为什么第 4 章要从"全量塞进 prompt"改成"工具调用"。有了真实数据，模型不需要编。

> ⚠️ **降温不算抗幻觉手段**。降低 temperature 只压缩表达的发散度，让输出更稳定，**不会提升事实性**——模型仍可能稳定地重复同一个错误说法（见下方误区 2）。视觉链路用 0.5 是出于**识别准确性与输出稳定性**的考虑，别在面试里把它说成抗幻觉的第三层。

**进阶方案**：

- **RAG**：检索相关文档喂给模型（见 [#rag](#rag)）
- **引用溯源**：要求模型标注答案来源，便于核验
- **结构化输出约束**：强制 JSON schema，减少自由发挥

#### 常见误区

1. **以为换个更大的模型就没幻觉了。** 会减少，但不会消失。
2. **以为 temperature 调到 0 就没幻觉了。** 减少随机性不等于增加事实性。
3. **只在 prompt 里说"不要编造"就够了。** 这只是压制，没有给真实数据时模型还是可能编。

#### 面试能怎么讲

> "幻觉是模型生成了看似合理但不符合事实的内容，根源在于它的目标是生成通顺文本而不是陈述事实。我们用了两层来缓解：第一层也是最有效的，是通过工具调用给模型真实数据，有据可查它就不用编；第二层是 system prompt 里明确红线，不知道就说不知道。另外识别任务用了较低温度，但那是为了输出稳定，不是抗幻觉手段。根本解法还是让模型'看着数据说话'。"

相关词条：[#rag](#rag)、[#function-calling](#function-calling)、[#prompt](#prompt)

---

<a id="openai-protocol"></a>

### OpenAI 兼容协议

**一句话定义**：OpenAI 定义的聊天接口格式（`/v1/chat/completions`），已成为**事实上的行业标准**。

#### 为什么需要它

OpenAI 最早把这个接口做出来并被广泛采用，后来者（DeepSeek、通义、智谱、 moonshot 等）为了让开发者零成本迁移，**都实现了同样的接口格式**。

这意味着：**你学会一套协议，就能调用几乎所有主流模型**。

#### 它内部怎么工作

统一的三要素：

| 要素 | 值 |
|---|---|
| 端点 | `POST {base-url}/v1/chat/completions`（其中 `{base-url}` 指厂商根路径，不含 `/v1`） |
| 认证 | `Authorization: Bearer {api-key}` |
| 请求/响应 | 固定的 JSON 结构 |

注意 **base-url 的路径约定差异**（本项目踩过）：

```
DeepSeek 官方:  https://api.deepseek.com
                Spring AI 会自动拼接 /v1/chat/completions

某些中转站:     https://opencode.ai/zen/go
                （不带 /v1，因为站方路径规则不同）
```

**在 yml 里配 `base-url` 时不要带 `/v1`** ——Spring AI 会自动拼。这是本项目注释里特意提示的点。

Spring AI 的 `base-url` 配置：

```yaml
spring:
  ai:
    openai:
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY:}
```

#### 在本项目里怎么用

本项目从 DeepSeek 官方换成中转站时，**只改了 yml 两行，Java 代码一行没动**。这就是"协议标准化 + 框架抽象"的价值。

需要注意的兼容差异（不同厂商可能有细微差别）：

- 是否支持 `tools` / function calling
- 是否支持 `stream` 流式
- 是否支持视觉输入（`image_url`）
- 模型 naming 规则不同

#### 常见误区

1. **以为"兼容"就是"完全一样"。** 各家在细节上有差异（比如工具调用的稳定性、视觉支持），换厂商后要重新验证。
2. **base-url 多写了 `/v1`。** 会导致 `/v1/v1/chat/completions` 404。

#### 面试能怎么讲

> "OpenAI 的 chat/completions 接口已经是事实标准，主流厂商都实现了兼容格式，所以学会一套协议就能调用各家模型。我们项目从 DeepSeek 官方切到中转站只改了两行 yml 配置，Java 代码零改动——这也是选 Spring AI 这类框架的价值。要注意'兼容'不等于'完全一致'，工具调用和视觉支持的稳定性各家有差异，换厂商要重新验证。"

相关词条：[#model-providers](#model-providers)、[#function-calling](#function-calling)

---

<a id="model-providers"></a>

### 常见模型厂商与选型

**一句话定义**：提供大模型 API 服务的公司，以及选型时该看哪些维度。

#### 为什么需要它

"用哪个模型"是个真实的工程决策，影响成本、效果、稳定性，甚至合规。

#### 它内部怎么工作：主要玩家

| 厂商 | 代表模型 | 特点 |
|---|---|---|
| **OpenAI** | GPT-4o、GPT-4.1 | 能力最强，生态最成熟，价格较高 |
| **DeepSeek** | DeepSeek-V3、R1 | 性价比极高，国产，本项目选用 |
| **Anthropic** | Claude 系列 | 长文本、代码能力强 |
| **阿里通义** | Qwen 系列 | 国产，开源生态好 |
| **智谱** | GLM 系列 | 国产 |
| **月之暗面** | Kimi | 长文本见长 |
| **Google** | Gemini | 多模态原生 |

另有**中转/聚合服务**（如 OpenRouter、各类中转站）：一个 API Key 访问多家模型，常用于降低成本或绕过地区限制。本项目注释里提到的 `opencode.ai/zen/go` 就属于这类。

#### 选型该看什么

| 维度 | 说明 |
|---|---|
| **能力** | 任务能不能做好（分类/生成/推理/代码） |
| **价格** | 按 token 计费，输入输出单价不同 |
| **上下文窗口** | 能处理多长 |
| **速度** | 首 token 延迟、生成速度（直接影响体验） |
| **稳定性** | 限流策略、可用性 |
| **合规** | 数据出境、备案要求（国内业务必须考虑） |
| **特定能力** | 是否支持工具调用、视觉、流式 |

#### 在本项目里怎么用

- 文本链路 + 视觉链路配置的都是 `deepseek-v4-flash-vision-exp`（**注意**：yml 默认 model 与 `VISION_MODEL` 常量值相同，所以视觉链路的 `.model(...)` 覆盖实际是**无效覆盖**，两条链路跑的是同一个模型；视觉链路的真实差异只有 temperature）
- yml 里通过 `base-url` 切换，代码不感知具体厂商
- 视觉模型与文本模型**分开配置**（因为不是所有模型都支持多模态）

#### 常见误区

1. **只看能力不看价格。** 高频调用场景下，价格可能是决定性因素。
2. **以为贵的模型一定更适合你的场景。** 简单任务用便宜模型完全够，要在真实数据上评测。
3. **忽略合规。** 国内业务使用境外模型要评估数据出境风险。

#### 面试能怎么讲

> "选型我会看能力、价格、上下文长度、速度、稳定性、合规这几个维度，另外还要考虑它是否支持我需要的能力（工具调用、视觉、流式）。我们项目用 DeepSeek，性价比高；配置通过 base-url 切换，代码不绑定厂商，方便随时换。我认为选型要在自己的真实数据上评测，不能只看榜单。"

相关词条：[#openai-protocol](#openai-protocol)、[#multimodal](#multimodal)

---

<a id="inference"></a>

### 训练 vs 推理

**一句话定义**：训练是"造模型"（花钱造大脑），推理是"用模型"（每次调用都算一次）。

#### 为什么需要区分

因为这决定了**什么能改、什么不能改**：

- 你（应用开发者）**几乎不会做训练**，只会做推理
- 遇到"模型不知道我们店的商品"这类问题，**不能靠训练解决**（成本和技术门槛都太高）

#### 它内部怎么工作

| 维度 | 训练 Training | 推理 Inference |
|---|---|---|
| 干什么 | 用海量数据调整模型参数 | 用固定参数做前向计算 |
| 成本 | 极高（百万到亿美元级） | 按 token 付费 |
| 耗时 | 数天到数月 | 毫秒到秒级 |
| 谁做 | 模型厂商 | 应用开发者（你） |
| 产出 | 一个新的模型 | 一次回答 |

**中间地带**：微调（Fine-tuning）

如果确实需要让模型学会特定领域知识或风格，可以做**微调**——在预训练模型基础上用少量数据继续训练。

但要注意：

- 微调擅长教"**格式和风格**"，不擅长注入"**知识**"
- 注入知识更好的方式是 **RAG**（见 [#rag](#rag)）
- 微调后模型要单独部署，成本和复杂度都上升

#### 在本项目里怎么用

**本项目完全没做训练/微调**，用的是：

1. 现成模型（DeepSeek）
2. prompt 工程（system prompt）
3. **工具调用**（`searchProduct` 查真实商品）

第 4 章"先撞一次南墙"讲的就是这个认知转变：早期想把商品全量塞进 prompt（token 爆炸、数据过期），正确解法是让模型自己调工具查库。

#### 常见误区

1. **以为"让模型学会我们的数据"要训练。** 不需要，用工具或 RAG。
2. **以为微调能解决知识过时。** 微调后知识依然会过时，RAG 才能实时。
3. **以为 prompt 里塞很多例子就是在"训练"。** 那是"少样本提示"（In-context Learning），模型参数没变，下次请求不带上就失效了。

#### 面试能怎么讲

> "训练是调参数造模型，成本极高，是厂商做的事；推理是用固定参数做计算，就是我们每次 API 调用。应用开发者基本只做推理。要让模型'知道'我们的业务数据，我不会去微调——微调擅长教格式和风格，注入知识更好的方式是 RAG 或工具调用。我们项目就是用工具查真实商品数据，既实时又省 token。"

相关词条：[#rag](#rag)、[#function-calling](#function-calling)、[#llm](#llm)

---

<a id="multimodal"></a>

### 多模态与视觉模型

**一句话定义**：能同时处理**文字以外**的输入（图片、音频、视频）的模型。

#### 为什么需要它

纯文本模型只能读文字。用户发一张商品图片问"这是什么"，文本模型根本收不到图——**必须用支持视觉输入的模型**。

#### 它内部怎么工作

视觉模型（Vision / VLM）的输入结构不同：

```jsonc
{
  "role": "user",
  "content": [                                    // ← 数组，不是字符串
    { "type": "text", "text": "这图里是什么商品？" },
    { "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,..." } }
  ]
}
```

模型内部把图片切成小块（patch），编码成类似 token 的向量，和文本 token 一起送进 Transformer。

**重要限制**：

- 不是所有模型都支持视觉。**纯文本模型收到图片会报错**
- **本项目选用的视觉模型实测挂工具会乱**（这是第 4 章案例四的真实事故），但**不要外推成"所有视觉模型都不支持工具调用"**——近年多模态旗舰（GPT-4o/4.1、Claude、Gemini）的工具调用已经相当强了
- 图片按尺寸计 token，很贵（见 [图片的 token 计费](#vision-token)）

#### 在本项目里怎么用

本项目用了**两个 ChatClient**（第 4.7 节解释原因）：

```java
private final ChatClient chatClient;         // 文本链路：带 searchProduct 工具
private final ChatClient visionChatClient;   // 视觉链路：不带工具
```

分流逻辑（第 5 章）：

```java
if (req.hasImage()) {
    // 视觉链路：visionChatClient + 低温度（model 值与 yml 默认相同，实测为无效覆盖）
    answer = visionChatClient.prompt()
            .system(visionSystemPrompt())
            .messages(toAiHistory(conv.getId()))
            .messages(List.of(buildUserMessage(req)))
            .options(OpenAiChatOptions.builder()
                    .model(VISION_MODEL).temperature(0.5).build())
            .call().content();
} else {
    // 文本链路：chatClient + 工具
}
```

**为什么视觉链路不用工具？** 这是第 4 章案例四的真实事故：视觉模型收到"工具说明书"后开始胡言乱语。所以视觉链路刻意"空手"。

#### 常见误区

1. **以为一个模型能干所有事。** 视觉能力要单独确认，且视觉模型往往工具调用能力弱。
2. **以为图片 URL 随便传。** 需要公网可访问，或转成 base64 data URL。
3. **忽略图片成本。** 图片 token 消耗远大于文字。

#### 面试能怎么讲

> "多模态指模型能处理文字以外的输入，我们项目用了视觉模型识别商品图片。要注意两点：一是不是所有模型都支持视觉，纯文本模型收到图片会直接报错；二是视觉模型对工具调用的支持普遍较弱，所以我们拆成两个 ChatClient——文本链路挂工具，视觉链路不挂，按有没有图分流。这也是我们踩过坑之后的设计。"

相关词条：[#vision-token](#vision-token)、[#model-providers](#model-providers)、[#media](#media)

---

<a id="vision-token"></a>

### 图片的 token 计费

**一句话定义**：图片不像文字按字数计费，而是**按尺寸和分块数**计费，通常比文字贵得多。

#### 为什么需要它

这直接决定了前端**必须压缩图片**——不压就是烧钱。

#### 它内部怎么工作

一般规律：**把图片缩放到能看懂内容的最小尺寸**

常见做法（以 OpenAI 某代的规则为例，**具体以厂商当期文档为准**——OpenAI 现行已改为按 32×32 patch 计算）：

| 模式 | 处理方式 | token 消耗 |
|---|---|---|
| 低分辨率 | 缩放成 512×512，算 1 块 | ~85 tokens |
| 高分辨率 | 按 512×512 切成多块 | 每块 ~170 tokens + 基础 85 tokens |

举例：一张 1024×1024 的图在高分辨率模式下切成 4 块，约 4×170 + 85 ≈ 765 tokens。

**而 765 tokens 相当于五六百个汉字**——一张图的代价相当于发了一大段文章。

再叠加 base64 传输的代价：

```
原始图片 200KB
  ↓ base64 编码
约 267KB 文本（膨胀约 1/3）
  ↓ 发给模型
按尺寸折算成 tokens 计费
```

#### 在本项目里怎么用

前端 `Chat.vue` 的压缩逻辑（第 5.2 节）：

```javascript
const maxSize = 1280              // 最长边压到 1280px
canvas.toDataURL('image/jpeg', 0.8)   // 转成 JPEG，质量 0.8
```

**为什么是这两个值？**

- **1280px**：再小可能影响识别准确率，再大纯属浪费 token——这是个平衡点
- **JPEG + 0.8**：JPEG 比 PNG 小得多；质量 0.8 肉眼几乎无损，但体积能省一半以上
- **白底**：PNG 转 JPEG 时透明区域会变黑，所以先铺白底

#### 常见误区

1. **以为图片按文件大小计费。** 是按**尺寸**（像素）折算，不是 KB。不过压缩后尺寸和体积通常一起下降。
2. **以为传原图识别更准。** 超过一定尺寸后，细节增加带来的准确率提升微乎其微，成本却线性上升。
3. **历史图片可以一直带着。** 每轮都重发图片 = 每轮都重新付费（见 [上下文窗口](#context-window)）。

#### 面试能怎么讲

> "图片按尺寸折算 token，一张 1024 的图可能耗掉几百 token，相当于几百个字，比文字贵得多。所以前端必须压缩——我们压到最长边 1280px、JPEG 质量 0.8，再加白底处理透明通道。这个值是平衡点：再小影响识别，再大纯烧钱。另外历史图片我们不重复传给模型，只留文字进上下文，也是为了控制成本。"

相关词条：[#multimodal](#multimodal)、[#token](#token)、[#context-window](#context-window)

---

<a id="function-calling"></a>

### Function Calling 原理

**一句话定义**：模型不直接执行代码，而是**"点菜"**——告诉框架"我要调这个方法、参数是这些"，由框架执行后把结果送回去。

#### 为什么需要它

大模型不知道你店里的商品。早期做法是**把商品全量塞进 prompt**（第 4.1 节"先撞一次南墙"），四个致命伤：

| 问题 | 说明 |
|---|---|
| token 爆炸 | 商品越多越贵 |
| 数据过期 | prompt 是快照，商品改价了 AI 不知道 |
| 模型"翻找"易看漏 | 信息量大时检索能力下降 |
| 无法分页聚合 | 复杂的筛选排序做不了 |

Function Calling 让模型**按需查询**——需要时自己调用工具拿真实数据。

#### 它内部怎么工作：两轮 HTTP

```
【第 1 轮请求】messages + tools（工具清单）
        ↓
【第 1 轮响应】"我要调 searchProduct({keyword:'降噪'})"
        ↓ 模型返回的是 tool_calls，不是 content
【框架执行】在你的 JVM 里反射调用真实方法 → 查 MySQL
        ↓
【第 2 轮请求】messages + 上一条 assistant 消息 + role=tool 的结果
        ↓
【第 2 轮响应】基于真实数据的最终回答
```

**关键：模型只"点菜"，做菜的是你的代码。**

第 1 轮响应的特殊之处（完整报文见主笔记附录 A.3）：

```jsonc
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": null,                        // ← 没有正文！
      "tool_calls": [{
        "id": "call_abc123",
        "function": {
          "name": "searchProduct",
          "arguments": "{\"keyword\":\"降噪\"}"  // ← 字符串化的 JSON
        }
      }]
    },
    "finish_reason": "tool_calls"             // ← 不是 stop
  }]
}
```

四个新手必踩的点：

1. `content` 是 **null**，别去取它
2. `arguments` 是**字符串**不是对象，要反序列化
3. `finish_reason` 是 **`tool_calls`** 不是 `stop`
4. 第 2 轮必须**原样带回** assistant 消息，且 `tool_call_id` 要对上

#### 在本项目里怎么用

`ProductSearchTool.searchProduct()` 被 `@Tool` 标记，`AiConfig` 里通过 `defaultTools(searchProduct)` 注册到 `chatClient`。

业务代码里**看不到任何"调用工具"的代码**：

```java
answer = chatClient.prompt()
        .system(textSystemPrompt())
        .messages(toAiHistory(conv.getId()))
        .user(req.getMessage())
        .call()
        .content();
```

因为整个两轮编排由 Spring AI 自动完成——这也是为什么要选框架而不是自己写 HTTP。

#### 常见误区

1. **以为模型执行了代码。** 没有，它只输出"我要调什么"，执行在你的 JVM 里。
2. **以为工具参数可信。** 模型是"不可信的外部调用者"，可能传 null、0、负数，方法体内必须防御（见第 4.3 节）。
3. **以为一定只调一次。** 模型可能连续调用多个工具，或同一个工具调多次。

#### 面试能怎么讲

> "Function Calling 是两轮 HTTP：第一轮带上工具清单，模型返回 tool_calls（方法名+参数）而非正文；框架在 JVM 里执行真实方法、查数据库，把结果作为 role=tool 的消息发回；第二轮模型基于真实数据生成回答。关键是**模型只点菜，执行的是框架**。我们项目里商品查询走的就是这条路，业务代码一行调用工具的代码都没有，全由 Spring AI 编排。"

相关词条：[#tool-schema](#tool-schema)、[#springai-message](#springai-message)、[#agent](#agent)

---

<a id="tool-schema"></a>

### 工具 Schema 与 description 写法

**一句话定义**：给模型的**工具说明书**——决定模型"要不要调这个工具"以及"参数怎么填"。

#### 为什么需要它

模型看不到你的 Java 代码，它判断要不要调工具，**唯一依据是这段 description 文字**。

写得好不好，直接决定功能是否可用。

#### 它内部怎么工作

对比好坏两种写法：

```java
// ❌ 差：太简略，模型不知道什么时候该调
@Tool(description = "搜索商品")

// ✅ 好：说清"返回什么" + "什么时候该调" + "红线"
@Tool(description = "搜索本店在售商品：可按关键词、分类过滤，返回商品列表(名称/副标题/起售价)与总数。"
        + "当用户问商品、价格、或要找某类商品时调用，别自己编造商品。")
```

**好 description 的三要素**：

| 要素 | 作用 | 示例 |
|---|---|---|
| **返回什么** | 让模型知道能拿到什么 | "返回商品列表(名称/副标题/起售价)与总数" |
| **何时该调** | 触发条件 | "当用户问商品、价格、或要找某类商品时调用" |
| **红线** | 压制幻觉 | "别自己编造商品" |

参数 description 同理，要写清格式和语义：

```java
@ToolParam(required = false, description = "关键词，如'耳机'") String keyword,
@ToolParam(required = false, description = "分类id，可不传") Long categoryId,
```

#### 在本项目里怎么用

**血泪教训一：description 太简略导致模型不调工具**

第 4.6 节案例二：最初 description 只写"搜索商品"，结果模型该调不调，开始自己编商品。补全说明后解决。

**血泪教训二：参数必填导致模型编造值**

```java
// ❌ 最初：参数默认必填
// 后果：用户没提分类时，模型硬填 categoryId=0 交差 → 查询为空 → AI 说"没找到"
@ToolParam(description = "分类id") Long categoryId

// ✅ 修复：全部设为可选
@ToolParam(required = false, description = "分类id，可不传") Long categoryId
```

配合方法体内的防御：

```java
query.setCategoryId(categoryId != null && categoryId > 0 ? categoryId : null);
query.setPage(page == null || page < 1 ? 1 : page);
query.setPageSize(pageSize == null || pageSize < 1 ? 10 : pageSize);
```

**返回值也要精简**——工具返回值会计入 token：

```java
// 只挑回答用得上的 4 个字段
m.put("name", vo.getSpuName());
m.put("subTitle", vo.getSubTitle());
m.put("minPrice", vo.getMinPrice());
m.put("id", vo.getId());
```

#### 常见误区

1. **把 description 当普通注释随手写。** 它是发给模型的唯一说明。
2. **参数设必填。** 模型会编造值来交差。
3. **返回全量字段。** 浪费 token，还可能干扰模型。

#### 面试能怎么讲

> "工具的 description 不是给程序员看的注释，而是发给模型的唯一说明书，必须写清三件事：返回什么、什么时候该调、什么不该做。我们踩过两次坑：一次是 description 只写'搜索商品'四个字，模型该调不调开始编商品；另一次是参数设成必填，模型在用户没提分类时硬填 categoryId=0 导致查不到。改成全部可选加方法内兜底才稳定，另外返回值只挑必要字段省 token。"

相关词条：[#function-calling](#function-calling)、[#json-schema](#json-schema)、[#hallucination](#hallucination)

---

<a id="rag"></a>

### RAG 检索增强生成

**一句话定义**：先从知识库**检索**出相关资料，再把这些资料**塞进 prompt** 让模型基于它们回答。

#### 为什么需要它

解决两个核心问题：

1. **模型不知道你的私有数据**（公司内部文档、商品库）
2. **知识会过时**（模型训练截止日期之后的事它不知道）

相比"把所有数据塞进 prompt"，RAG 只塞**相关的**那一小部分——既省 token 又更准确。

#### 它内部怎么工作

```
【离线：准备阶段】
文档 → 切分成小块(chunk) → 每块转成向量(embedding) → 存入向量库

【在线：问答阶段】
用户提问 → 转成向量 → 在向量库里找最相似的 Top-K 块
        → 把这些块拼进 prompt
        → 模型基于这些资料生成回答
```

对比三种"让模型知道业务数据"的方案：

| 方案 | 实时性 | token 成本 | 适合 |
|---|---|---|---|
| 全量塞 prompt | ❌ 快照 | 极高 | 数据量极小时 |
| **工具调用** | ✅ 实时 | 低（按需） | 结构化数据、精确查询 |
| **RAG** | ✅ 可近实时 | 中（Top-K） | 非结构化文档、语义匹配 |

#### 在本项目里怎么用

**本项目 V1 没有用 RAG**，用的是工具调用（商品是结构化数据，精确查询足够）。

但第 4.8 节和面试 Q9 都提到了演进方向：**当商品到 10 万级时，"通勤想安静听歌"这类语义需求，关键词检索匹配不上，就要上 RAG。**

**RAG 和工具调用不是替代关系，可以组合**：

- 工具负责**精确查**（按 ID、按分类、按价格区间）
- RAG 负责**懂语义**（"适合送女朋友的礼物"）

#### 常见误区

1. **以为 RAG 就是向量搜索。** 检索只是前半段，后面"怎么把资料组织进 prompt、怎么让模型忠实于资料"同样重要。
2. **以为上了 RAG 就没幻觉了。** 会大幅减少，但模型仍可能无视检索到的资料。
3. **忽略切分策略。** chunk 切得不好，检索质量会很差（见 [分块策略](#chunking)）。

#### 面试能怎么讲

> "RAG 是检索加生成：离线把文档切块转向量存进向量库，在线把提问也转向量、找最相似的 Top-K 块拼进 prompt，让模型基于真实资料回答。它解决模型不知道私有数据和知识过时的问题。我们项目 V1 用的是工具调用（商品是结构化数据，精确查询足够），规划里商品到十万级、需要理解'通勤想安静听歌'这类语义需求时再上 RAG。我认为两者是互补的——工具做精确查询，RAG 做语义匹配。"

相关词条：[#embedding](#embedding)、[#vector-db](#vector-db)、[#semantic-search](#semantic-search)

---

<a id="embedding"></a>

### Embedding 向量

**一句话定义**：把文本（或图片）转成**一串数字（向量）**，使得"意思相近的内容，向量也相近"。

#### 为什么需要它

计算机不懂语义，但会算距离。把文本变成向量后，"相似度"就变成一个数学问题：**两个向量之间的距离**。

#### 它内部怎么工作

```
"降噪耳机"     → [0.23, -0.11, 0.87, ..., 0.42]   （如 1536 维）
"主动降噪耳罩" → [0.25, -0.09, 0.83, ..., 0.39]   ← 和上面很接近
"红烧牛肉面"   → [-0.71, 0.52, -0.13, ..., 0.08]  ← 离得很远
```

相似度常用**余弦相似度**（看两个向量的夹角，忽略长度）：

```
相似度 = cos(θ)，范围 -1 ~ 1，越接近 1 越相似
```

**关键特性**：

- 语义相近的文本，向量距离近——**即使没有共同的词**
- "降噪耳机" 和 "戴着很安静的听歌设备" 没有任何共同词，但向量会很接近

**Embedding 模型和生成模型是两回事**：

| | 生成模型（LLM） | Embedding 模型 |
|---|---|---|
| 输入输出 | 文本 → 文本 | 文本 → 向量 |
| 用途 | 生成回答 | 计算相似度、检索 |
| 例子 | DeepSeek-V3 | text-embedding-3 |

#### 在本项目里怎么用

**V1 没有用到**，这是 V2 的规划（商品描述 embedding 入向量库）。

需要注意的成本点：embedding 也要调 API、也计费，但比生成便宜得多。

#### 常见误区

1. **以为向量能看懂。** 那一串数字对人无意义，只能算距离。
2. **以为不同模型的向量可以混用。** 不行，必须用**同一个 embedding 模型**生成的向量才能互相比较。
3. **以为维度越高越好。** 维度高信息多但存储和计算成本也高，要权衡。

#### 面试能怎么讲

> "Embedding 把文本转成向量，让语义相似度变成可计算的距离，常用余弦相似度。它的价值在于语义相近的文本向量也相近，即使没有共同词——'降噪耳机'和'戴着很安静的听歌设备'能匹配上。要注意的是不同 embedding 模型生成的向量不能混用，必须用同一个模型。我们项目 V1 没用到，规划里商品量上来后上 RAG 时会引入。"

相关词条：[#rag](#rag)、[#vector-db](#vector-db)、[#semantic-search](#semantic-search)

---

<a id="vector-db"></a>

### 向量数据库

**一句话定义**：专门存储和检索向量的数据库，核心能力是"找最相似的 K 个向量"。

#### 为什么需要它

传统数据库的 `WHERE` 和 `LIKE` 做的是**精确匹配和关键词匹配**，无法做"语义相似"查询。而向量相似度计算在高维空间里很慢，需要专门的索引结构（如 HNSW、IVF）。

#### 它内部怎么工作

```
存储：  { id: 1, vector: [0.23, -0.11, ...], metadata: {name: "AirSound Pro", price: 399} }
        { id: 2, vector: [0.19, -0.31, ...], metadata: {...} }

查询：  给一个向量 → 返回最相似的 K 条（近似最近邻搜索，ANN）
```

**"近似"很重要**：为了速度，向量库不保证找到的一定是全局最相似的，而是**足够接近**的。这是精度与速度的权衡。

常见选择：

| 类型 | 代表 | 特点 |
|---|---|---|
| 专用向量库 | Milvus、Qdrant、Weaviate | 性能强，功能专 |
| 传统库扩展 | **PostgreSQL + pgvector**、Redis | 复用现有设施，够用 |
| 轻量库 | Chroma、FAISS | 适合原型和小规模 |

#### 在本项目里怎么用

**V1 未使用**。如果 V2 上 RAG，考虑到项目已用 MySQL，一个务实的选项是：

- 数据量不大（几万条）→ **pgvector**（如果换 PG）或独立向量服务
- 也可以用 MySQL 8 配合向量插件，但生态不如 PG 成熟

Spring AI 提供了 `VectorStore` 抽象（见 [VectorStore 向量存储](#vectorstore-springai)），换实现只改配置。

#### 常见误区

1. **以为一定要上专用向量库。** 数据量小时，pgvector 这类扩展完全够用，还能省一个组件。
2. **以为向量库存的是原文。** 存的是向量 + 元数据，原文通常存在原库里，用 ID 关联。
3. **忽略了"近似"的含义。** 结果不保证 100% 精确。

#### 面试能怎么讲

> "向量库专用于存储和检索高维向量，核心是近似最近邻搜索（ANN），为了速度牺牲一点精度。选型上我不迷信专用向量库——数据量小时 pgvector 这类传统库的扩展完全够用，还能少维护一个组件。要注意它存的是向量和元数据，原文通常还在原库里靠 ID 关联。"

相关词条：[#rag](#rag)、[#embedding](#embedding)、[#vectorstore-springai](#vectorstore-springai)

---

<a id="semantic-search"></a>

### 语义检索 vs 关键词检索

**一句话定义**：关键词检索匹配"字面相同的词"，语义检索匹配"意思相近的内容"。

#### 为什么需要它

这是面试 Q9 的核心，也是"商品量上来后关键词检索不够用"的原因。

#### 它内部怎么工作

| 维度 | 关键词检索 | 语义检索 |
|---|---|---|
| 原理 | 倒排索引，匹配词项 | 向量距离 |
| 匹配 | **字面**相同 | **语义**相近 |
| 同义词 | ❌ 需维护同义词表 | ✅ 天然支持 |
| 精确匹配 | ✅ 强（查订单号、SKU） | ❌ 弱 |
| 可解释性 | ✅ 能说清为什么命中 | ❌ 黑盒 |
| 成本 | 低 | 高（要 embedding） |

举个例子：

```
用户搜："通勤想安静听歌"

关键词检索：匹配"通勤""安静""听歌" → 商品标题里没这些词 → 0 结果
语义检索：  理解成"降噪耳机" → 命中 ✓
```

反过来：

```
用户搜："订单号 20240901001"

关键词检索：精确命中 ✓
语义检索：  可能匹配到一堆相似但不相关的 → ✗
```

#### 在本项目里怎么用

`ProductSearchTool` 现在是**关键词检索**（MySQL 的 LIKE 匹配商品名）。

V2 规划是引入 RAG 做语义检索。**最佳实践是两者结合（混合检索）**：

1. 语义检索召回候选（解决"查不到"）
2. 关键词/结构化条件过滤（解决"不精确"）
3. 可选：重排序提升精度

#### 常见误区

1. **以为语义检索全面优于关键词检索。** 精确查询场景（订单号、ID）关键词检索更好。
2. **以为上了语义检索就可以去掉关键词检索。** 混合检索效果最好。

#### 面试能怎么讲

> "关键词检索匹配字面，语义检索匹配意思，各有优势：查订单号、SKU 这类精确查询关键词更强，理解'通勤想安静听歌'这种口语化需求要靠语义。我们项目现在是关键词检索，规划里商品量上来后会引入 RAG 做语义，但不会完全替代——最佳实践是混合检索，语义负责召回、关键词负责精确过滤。"

相关词条：[#rag](#rag)、[#rerank](#rerank)、[#function-calling](#function-calling)

---

<a id="chunking"></a>

### 分块策略 Chunking

**一句话定义**：把长文档切成小块再转向量——切分方式直接决定 RAG 的检索质量。

#### 为什么需要它

- 模型有**上下文窗口**限制，塞不下整篇长文档
- 大块内容语义混杂，检索时相关性被稀释
- 小块又可能切断完整语义

#### 它内部怎么工作

| 策略 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **固定长度** | 每 N 个字符切一块 | 简单 | 可能切断句子 |
| **固定长度 + 重叠** | 相邻块重叠一部分 | 缓解切断问题 | 有冗余，成本上升 |
| **按结构切** | 按段落/标题/标点切 | 语义完整 | 需要文档有结构 |
| **语义切分** | 按语义相似度自动聚块 | 质量最好 | 实现复杂 |

**两个关键参数**：

- **chunk_size**：块大小。太小丢失上下文，太大语义混杂。常见 300~800 tokens
- **overlap**：重叠。一般取 chunk_size 的 10%~20%，保证跨块的内容不丢

**还有个容易忽略的点**：每块要带**元数据**（来源文件名、章节、页码），这样回答时能标注引用来源。

#### 在本项目里怎么用

V1 未涉及。V2 如果做商品 RAG，一个务实的思路：**商品本身不用切**——每个商品的描述就是一个天然的小块，直接整条 embedding 即可。

**切分主要挑战的是长文档**（如商品详情页的长图文、使用说明、客服话术库）。

#### 常见误区

1. **以为切得越细越好。** 太细会丢失上下文，检索到的块可能语义不完整。
2. **不做重叠。** 关键内容恰好在切分点会被割裂。
3. **切完就不管元数据了。** 没有来源信息，无法做引用溯源。

#### 面试能怎么讲

> "分块是把长文档切成小块再转向量，直接影响检索质量。关键参数是块大小和重叠比例，重叠是为了避免关键内容在切分点被割断。策略上有固定长度、按结构切、按语义切几种，越往后效果越好但实现越复杂。另外每块要带元数据（来源、章节），这样回答时能标注引用。"

相关词条：[#rag](#rag)、[#embedding](#embedding)

---

<a id="rerank"></a>

### 重排序 Rerank

**一句话定义**：检索出候选后，用更精确（也更慢）的模型**重新排序**，把最相关的排到前面。

#### 为什么需要它

向量检索（召回阶段）为了速度用了近似算法，精度有限。Rerank 是**二阶段检索**的第二阶段：先粗召回，再精排序。

```
用户提问
   ↓
【召回】向量检索快速找 Top-50（快但粗糙）
   ↓
【重排】Rerank 模型对这 50 条精算相关性（慢但准）
   ↓
取 Top-5 塞进 prompt
```

#### 它内部怎么工作

| | 召回（向量检索） | 重排（Rerank） |
|---|---|---|
| 方法 | 向量距离 | 交叉编码模型 |
| 速度 | 快（毫秒级） | 慢（要逐对计算） |
| 精度 | 一般 | 高 |
| 处理量 | 全库 | 只处理召回的几十条 |

Rerank 模型会**同时**看"问题"和"候选文档"，输出一个相关性分数——比单独算两个向量的距离准确得多。

#### 在本项目里怎么用

V1 未涉及。这是 RAG 系统的**进阶优化**，通常在基础 RAG 跑通、发现"检索结果不够准"之后才引入。

判断是否需要 Rerank 的信号：

- 检索回来的 Top-K 里，真正相关的排在后面
- 用户反馈"答非所问"但实际库里有答案

#### 常见误区

1. **一上来就上 Rerank。** 应该先把基础 RAG 跑通，确认"召回不准"再优化。
2. **对全库做 Rerank。** 太慢，只对召回的候选做。
3. **以为能解决所有问题。** 如果召回阶段就没把正确答案找出来，Rerank 无能为力。

#### 面试能怎么讲

> "Rerank 是二阶段检索的第二阶段：先向量检索快速召回几十条候选，再用交叉编码模型精算相关性重排，取 Top-K 喂给模型。它比纯向量检索准，但只对召回的候选做，不能对全库做。工程上我倾向于先把基础 RAG 跑通，发现检索结果排序不准时再引入，而不是一开始就上。"

相关词条：[#rag](#rag)、[#semantic-search](#semantic-search)

---

<a id="agent"></a>

### Agent 与工具编排

**一句话定义**：让模型**自主规划、循环调用工具**直到完成任务的系统，而不只是"一问一答"。

#### 为什么需要它

Function Calling 解决了"调一次工具"，但复杂任务需要"**调多次、根据结果决定下一步**"：

```
用户："帮我对比 AirSound Pro 和 闪充宝，哪个更适合送人，再帮我下单便宜的那个"

规划：① 查 AirSound Pro → ② 查 闪充宝 → ③ 比较价格
     → ④ 查用户地址 → ⑤ 下单
```

这需要模型能**循环**：调用 → 观察结果 → 再调用。

#### 它内部怎么工作

经典的 ReAct 循环（Reasoning + Acting）：

```
┌─────────────────────────────────┐
│  Thought: 我需要先查这两个商品    │ ← 模型思考
│  Action: searchProduct(...)      │ ← 选工具、填参数
│  Observation: 查到结果...        │ ← 框架执行，返回结果
│  Thought: 价格分别是...比较一下   │ ← 再思考
│  Action: createOrder(...)        │
│  Observation: 下单成功            │
│  Answer: 已帮你下单...            │ ← 最终回答
└─────────────────────────────────┘
```

**关键组件**：

| 组件 | 作用 |
|---|---|
| 规划能力 | 把大任务拆成步骤 |
| 工具集 | 可调用的能力 |
| 记忆 | 记住前面步骤的结果 |
| 终止条件 | 什么时候停（防死循环） |

**工程上的红线**：

- **必须设最大迭代次数**（防死循环烧钱）
- **危险操作（下单、支付）要人工确认**，不能让 Agent 自主执行

#### 在本项目里怎么用

**本项目 V1 不是 Agent**——它是"单轮工具调用"，模型最多调一次 `searchProduct` 然后回答。

但具备了 Agent 的基础组件：

- 工具（`searchProduct`）
- 会话记忆（`t_conversation` + `t_message`）
- 框架支持（Spring AI）

V2 如果要做 Agent，方向是：多工具 + 循环调用 + 人类确认环节。

#### 常见误区

1. **以为用了 Function Calling 就是 Agent。** 单次调用只是"工具使用"，Agent 要有自主规划和循环。
2. **让 Agent 自主执行危险操作。** 支付、删除这类必须有人工确认。
3. **不设迭代上限。** 模型可能陷入循环，疯狂调用工具烧钱。

#### 面试能怎么讲

> "Agent 和单纯的 Function Calling 区别在于有没有自主规划和循环：Agent 是 Thought-Action-Observation 的循环，模型根据工具返回的结果决定下一步，直到完成任务。我们项目 V1 是单轮工具调用，不算 Agent，但工具、记忆、框架这些基础组件都有了。做 Agent 时工程上有两条红线：必须设最大迭代次数防死循环，以及危险操作（下单、支付）必须人工确认。"

相关词条：[#function-calling](#function-calling)、[#mcp](#mcp)、[#chatmemory](#chatmemory)

---

<a id="mcp"></a>

### MCP 协议

**一句话定义**：Model Context Protocol，Anthropic 提出的**工具/数据源接入标准**——让工具像"USB 设备"一样即插即用。

#### 为什么需要它

现在每个 AI 应用都要**自己写**工具集成代码：

```
应用 A 想接 MySQL → 自己写一套
应用 B 想接 MySQL → 再写一套
应用 C 想接 Slack → 写一套
```

MCP 的目标是**标准化**：工具提供方按 MCP 实现一次，所有支持 MCP 的应用都能用。

#### 它内部怎么工作

```
┌────────────┐   MCP    ┌──────────────┐
│ AI 应用     │ ◄─────► │ MCP Server    │
│ (Client)   │  协议    │ (工具提供方)   │
└────────────┘          └──────────────┘
                              │
                        可暴露三类能力：
                        - Tools（可调用的函数）
                        - Resources（可读的数据）
                        - Prompts（预设提示词模板）
```

MCP Server 可以用任何语言写，通过 stdio 或 HTTP 与客户端通信。

#### 在本项目里怎么用

**V1 未使用**，属于进阶方向。

如果引入，价值在于：

- 复用社区现成的 MCP Server（数据库、文件系统、GitHub、浏览器等）
- 自己写的工具也能被其他 MCP 客户端复用

Spring AI 已经提供了 MCP 的集成支持。

#### 常见误区

1. **以为 MCP 是必须的。** 不是，自己写工具完全可行。MCP 解决的是**跨应用复用**问题。
2. **以为它替代 Function Calling。** 不是，MCP 是"怎么发现和接入工具"，底层调用机制仍是 Function Calling。
3. **以为已经很成熟。** 生态还在快速发展中，生产使用要评估稳定性。

#### 面试能怎么讲

> "MCP 是 Anthropic 提出的工具接入标准，目标是让工具像 USB 设备一样即插即用——工具提供方实现一次，所有支持 MCP 的应用都能用。它不替代 Function Calling，而是解决工具的发现和接入标准化问题。我们项目没用到，自己写工具完全可行；引入的价值在于能复用社区现成的 MCP Server。生态还在发展中，生产使用我会先评估成熟度。"

相关词条：[#agent](#agent)、[#function-calling](#function-calling)

---

<a id="chatclient"></a>

### ChatClient 链式 API

**一句话定义**：Spring AI 提供的**高层流式 API**，用链式调用把"组装请求 → 调模型 → 取结果"压缩成几行。

#### 为什么需要它

不用 ChatClient，你要手工拼 messages 列表、处理 options、解析响应、处理流式分帧——几十行样板代码。

用 ChatClient：

```java
String answer = chatClient.prompt()
        .system("你是导购")           // system prompt
        .user("推荐一款耳机")          // 用户输入
        .call()                      // 同步调用
        .content();                  // 取正文
```

#### 它内部怎么工作

ChatClient 是**建造者模式的链式封装**，内部持有 `ChatModel`：

```
ChatClient
   ├── prompt()            开始一次对话
   │     ├── .system(...)  加 system 消息
   │     ├── .user(...)    加 user 消息
   │     ├── .messages(...) 加一批消息（历史、多模态）
   │     ├── .options(...) 按次覆盖模型参数
   │     ├── .tools(...)   按次指定工具
   │     └── .advisors(...) 加拦截器
   ├── .call()    同步 → ChatResponse / .entity() 转对象
   ├── .stream()  流式 → Flux<ChatResponse>
   └── .content() 取正文（String 或 Flux<String>）
```

常用方法：

| 方法 | 作用 | 本项目 |
|---|---|---|
| `.system(String)` | 设置 system prompt | ✅ `textSystemPrompt()` |
| `.user(String)` | 追加一条 user 消息 | ✅ 本次问题 |
| `.messages(Message...)` | 追加一批消息 | ✅ 历史 + 多模态 |
| `.options(ChatOptions)` | **按次**覆盖模型/温度等 | ✅ 视觉链路切模型 |
| `.tools(Object)` | 按次指定工具 | （本项目用 defaultTools） |
| `.call()` | 同步调用 | ✅ `chat()` |
| `.stream()` | 流式调用 | ✅ `stream()` |
| `.content()` | 取文本正文 | ✅ |

**`.messages()` 可以调用多次，框架按顺序拼接**——这在本项目视觉链路里用到了：

```java
.messages(toAiHistory(conv.getId()))        // 历史上下文
.messages(List.of(buildUserMessage(req)))   // 本次多模态消息
```

**取结果的几种方式**：

```java
.content()                       // String：只要正文
.chatResponse()                  // ChatResponse：完整响应（含 usage、finish_reason）
.entity(User.class)              // 直接转成 Java 对象（结构化输出）
```

#### 在本项目里怎么用

`AiConfig` 里装配了两个 ChatClient Bean（见第 4.7 节）：

```java
@Bean
public ChatClient chatClient(ChatModel chatModel, ProductSearchTool tool) {
    return ChatClient.builder(chatModel)
            .defaultTools(tool)          // ← 默认挂上商品搜索工具
            .build();
}

@Bean
public ChatClient visionChatClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
            .build();                    // ← 不挂工具
}
```

`defaultTools` vs `.tools()`：

| | `defaultTools` | `.tools()` |
|---|---|---|
| 作用范围 | 该 ChatClient 的**所有**请求 | 仅**本次**请求 |
| 本项目 | 文本链路统一挂工具 | 未使用 |

#### 常见误区

1. **以为 ChatClient 是线程安全的、可以改配置。** 它是**不可变**的，`default*` 在构建时设定，之后要改得重新 build 或用 `.options()` 按次覆盖。
2. **以为 `.user()` 只能调一次。** 可以调多次，按顺序追加。
3. **以为 `.call()` 直接返回结果。** 它返回的是 `CallResponseSpec`（中间规约对象，不是响应体），必须再选一个终结方法：`.content()` 拿 `String`、`.chatResponse()` 拿 `ChatResponse`（含 usage、finish_reason）、`.entity(Xxx.class)` 转 POJO。直接把 `.call()` 的返回值赋给 `ChatResponse` 是**编译不过**的。

#### 面试能怎么讲

> "ChatClient 是 Spring AI 的高层流式 API，用链式调用组装 prompt、调模型、取结果，把拼报文和解析响应压缩成几行。它内部持有 ChatModel。我们项目装配了两个 ChatClient：一个用 defaultTools 挂上商品搜索工具，一个干干净净给视觉链路用，按有没有图分流。视觉链路通过 `.options()` 设置参数，但要说清一个实情——当前两条链路用的是同一个模型（yml 默认 model 和视觉模型相同），`.model(...)` 覆盖实际无效，视觉链路真正生效的差异只是不带工具 + 温度 0.5。"

相关词条：[#chatmodel](#chatmodel)、[#springai-message](#springai-message)、[#advisor](#advisor)

---

<a id="chatmodel"></a>

### ChatModel vs ChatClient

**一句话定义**：`ChatModel` 是**底层接口**（真正发 HTTP 请求的），`ChatClient` 是**高层封装**（链式 API）。

#### 为什么需要区分

你在 yml 里配的 `spring.ai.openai.*` 最终装配的是 `ChatModel`，而业务代码用的是 `ChatClient`。理解这个层次，才能看懂配置是怎么生效的。

#### 它内部怎么工作

```
ChatClient（高层，链式 API）
      ↓ 内部持有并调用
ChatModel（接口：String call(String), Flux<ChatResponse> stream(Prompt)）
      ↓ 具体实现
OpenAiChatModel（按 OpenAI 协议发 HTTP）
      ↓
RestClient → HTTP → api.deepseek.com
```

两者定位对比：

| | ChatModel | ChatClient |
|---|---|---|
| 层次 | 底层抽象 | 高层封装 |
| API 风格 | `call(Prompt)` / `stream(Prompt)` | 链式 `.prompt().user().call()` |
| 需要手动拼 | Prompt 对象（含 messages 列表） | 不用，链式方法帮你加 |
| 灵活度 | 高（能直接操作 Prompt） | 够用（覆盖 90% 场景） |
| 本项目 | 由 Spring Boot 自动装配 | `AiConfig` 里用它建 Bean |

**什么时候直接用 ChatModel？**

- 需要高度定制（自己管理 messages 列表、自定义 options）
- 写框架代码而非业务代码
- 业务开发**用 ChatClient 就够了**

#### 在本项目里怎么用

`AiConfig` 是通过注入 `ChatModel` 来构建 `ChatClient` 的：

```java
@Bean
public ChatClient chatClient(ChatModel chatModel, ProductSearchTool tool) {
    return ChatClient.builder(chatModel)
            .defaultTools(tool)
            .build();
}
```

这个 `ChatModel` 从哪来？**Spring Boot 自动配置**——因为引入了 `spring-ai-starter-model-openai`，它会读取 `spring.ai.openai.*` 配置，自动创建 `OpenAiChatModel` Bean。

配置链路：

```yaml
spring:
  ai:
    openai:
      base-url: https://api.deepseek.com     # ← 决定请求发到哪
      api-key: ${DEEPSEEK_API_KEY:}          # ← 认证
      chat:
        options:
          model: deepseek-v4-flash-vision-exp  # ← 默认模型
          temperature: 0.7                     # ← 默认温度
```

#### 常见误区

1. **以为要自己 new ChatModel。** 不用，starter 自动装配。
2. **以为 ChatClient 和 ChatModel 二选一。** 不是，ChatClient 内部就用 ChatModel。
3. **混淆 yml 的默认配置和 `.options()` 的按次覆盖。** 后者优先级更高，只影响本次请求。

#### 面试能怎么讲

> "ChatModel 是底层接口，负责真正发 HTTP 请求，由 starter 根据 yml 配置自动装配；ChatClient 是高层链式 API，内部持有 ChatModel。业务开发用 ChatClient 就够了，需要高度定制才直接碰 ChatModel。我们项目在 AiConfig 里注入自动装配的 ChatModel，再构建两个不同配置的 ChatClient Bean。"

相关词条：[#chatclient](#chatclient)、[#openai-protocol](#openai-protocol)

---

<a id="springai-message"></a>

### Spring AI 的 Message 体系

**一句话定义**：Spring AI 对协议消息的 Java 抽象，一个接口 + 若干实现类，对应 OpenAI 协议的四种 role。

#### 为什么需要它

协议里消息是 JSON，Java 里需要对象来承载。Spring AI 用一套接口体系做映射。

#### 它内部怎么工作

```
org.springframework.ai.chat.messages.Message  （接口）
   ├── UserMessage          用户消息      → role: user
   ├── AssistantMessage     AI 消息       → role: assistant
   ├── SystemMessage        系统消息      → role: system
   └── ToolResponseMessage  工具结果      → role: tool
```

核心接口方法很简单（就两个）：

```java
public interface Message extends Content {
    String getText();                    // 文本内容
    MessageType getMessageType();        // 类型枚举：USER/ASSISTANT/SYSTEM/TOOL
    // Media、metadata 等由 Content 父接口提供
}
```

**构造方式**：

```java
new UserMessage("推荐一款耳机")                          // 纯文本
new AssistantMessage("推荐 AirSound Pro...")            // 纯文本

UserMessage.builder()                                    // 多模态（文本+图片）
        .text("这图里是什么？")
        .media(List.of(media))
        .build();
```

#### `.user(String)` vs 手动组装：什么时候必须退回 builder

ChatClient 链式 API 里加"本次用户消息"有两种写法，本项目两条链路恰好各用一种：

```java
// 纯文字链路：便捷方法一步到位
answer = chatClient.prompt()
        .messages(toAiHistory(conv.getId()))
        .user(req.getMessage())            // 框架内部 new UserMessage(text)
        .stream().content();

// 视觉链路：手动组装后经 .messages() 加入
return visionChatClient.prompt()
        .messages(toAiHistory(conv.getId()))
        .messages(List.of(buildUserMessage(req)))   // 文字+图片绑成一条 UserMessage
        .stream().content();
```

**为什么视觉链路不能用 `.user()`？** 因为带图消息是"文字 + 图片"的复合体，发给模型后 `content` 从字符串**升级成数组**：

```jsonc
// .user(String) 生成的 → content 是字符串
{ "role": "user", "content": "有什么降噪耳机" }

// builder 组装的 → content 是数组（文字和图片混在一条消息里）
{ "role": "user", "content": [
    { "type": "text", "text": "请识别这张图片" },
    { "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,..." } }
] }
```

`.user(String)` 这个便捷方法**只能接收一个 String**，装不下 `Media`；而链式 API 也没有"往已添加的 user 消息里追加 media"的方法。所以**只要消息带图片这类非文字附件，就必须退回手动组装 `UserMessage`，再用 `.messages()` 塞进去**。

**两个细节**：

1. **`.messages()` 是追加语义不是覆盖**——链上可以调用多次，框架按调用顺序依次拼接。本项目视觉链路就调了两次：先历史、再本次多模态消息，最终发给模型的是 `[system, ...历史, 本次"文字+图片"]`，正好符合"历史在前、新问题在后"的排序铁律。
2. **反过来，纯文字链路也可以用 `.messages(List.of(new UserMessage(text)))`**，效果与 `.user()` 一模一样——只是多写三个方法调用。所以选择原则就一条：**能便捷就便捷，有附件才手动**。

| | `.user(String)` | `.messages(buildUserMessage())` |
|---|---|---|
| 承载内容 | 只有文字 | 文字 + 任意附件（图片等） |
| 协议 content 形态 | 字符串 | 数组（text + image_url） |
| 可否互换 | 可（但啰嗦） | **不可**——装不下 Media |

#### 在本项目里怎么用

#### 在本项目里怎么用

**这里有个本项目特有的命名历史**，值得知道：

`toAiHistory()` 里会同时出现两种"消息"——

```java
.map(m -> (Message)                                  // ← Spring AI 的 Message（协议消息）
        (ChatMessage.ROLE_USER.equals(m.getRole())   // ← 本项目实体（库里存的记录）
                ? new UserMessage(m.getContent())
                : new AssistantMessage(m.getContent())))
```

- **`Message`**（`org.springframework.ai.chat.messages.Message`）：发给模型的协议消息
- **`ChatMessage`**（`com.aimall.ai.bean.ChatMessage`）：我们数据库里的聊天记录

**早期本项目的实体也叫 `Message`，两者重名**，同一文件里只能用全限定名区分，读代码极易混淆。后来把实体改名为 `ChatMessage`，从根上解决了这个坑——现在看名字就能分辨。

#### 常见误区

1. **把实体和协议消息搞混。** 记住：带 `Chat` 前缀的是我们库里的，不带的是发给模型的。
2. **以为 `UserMessage` 只能装文本。** 可以用 builder 带 `Media`（图片）。
3. **忘记历史里要有 assistant 消息。** 缺了模型会"失忆"（见 [三种角色](#system-prompt)）。

#### 面试能怎么讲

> "Spring AI 的 Message 是接口，实现类对应协议的四种 role：UserMessage、AssistantMessage、SystemMessage、ToolResponseMessage。有个命名上的坑值得一提：我们项目自己的实体原本也叫 Message，和 Spring AI 的 Message 接口重名，同一文件里只能写全限定名区分，后来把实体改名成 ChatMessage 才彻底解决。现在约定很清楚——ChatMessage 是库里的记录，Message 是发给模型的协议对象。"

相关词条：[#media](#media)、[#system-prompt](#system-prompt)、[#function-calling](#function-calling)、[#chatclient](#chatclient)

---

<a id="media"></a>

### Media 与多模态附件

**一句话定义**：`org.springframework.ai.content.Media`——一条消息携带的**媒体附件**，由 MIME 类型 + 数据源组成。

#### 为什么需要它

纯文本消息只有文字。要传图片，需要告诉框架两件事：**这是什么格式**（MIME）和**数据在哪**（Resource）。

#### 它内部怎么工作

```java
public class Media implements Content {
    private final MimeType mimeType;    // 格式：image/jpeg
    private final Resource data;        // 数据源
}
```

构造：

```java
Media media = new Media(
        MimeTypeUtils.parseMimeType("image/jpeg"),    // 格式
        new ByteArrayResource(bytes) {                // 数据源
            @Override
            public String getFilename() {
                return "product.jpg";
            }
        });
```

然后挂到 UserMessage 上：

```java
UserMessage.builder()
        .text("这图里是什么商品？")
        .media(List.of(media))
        .build();
```

框架最终会把它翻译成协议里的：

```jsonc
{ "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,..." } }
```

**这解释了为什么 Media 必须同时要 MIME 和数据源**——没有 MIME 就拼不出 `data:image/jpeg;base64,` 中间那截。

#### 在本项目里怎么用

`ChatServiceImpl.buildUserMessage()`：

```java
byte[] bytes = decodeImage(req.getImage());
String mimeType = detectImageMimeType(bytes);         // 按文件头嗅探真实格式
String filename = "product." + imageExtension(mimeType);
Media media = new Media(MimeTypeUtils.parseMimeType(mimeType),
        new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
return UserMessage.builder().text(text).media(List.of(media)).build();
```

两个细节：

1. **MIME 用文件头字节（magic number）嗅探**，不采信前端声明——因为前端 canvas 压出来是 JPEG，早期代码却硬编码 `image/png`，靠网关自行嗅探才没出事（详见主笔记 5.4.1）
2. **`getFilename()` 的匿名重写**是兼容层脏活：`ByteArrayResource.getFilename()` 默认返回 `null`，而部分网关要求附件必须有文件名

#### 常见误区

1. **以为 Media 可以直接传 URL。** 能，但要用 `UrlResource` 而不是 `ByteArrayResource`（V2 存对象存储后可以改这个）。
2. **MIME 写死。** 会导致声明与真实格式不符，换个严格网关就可能失败。
3. **忘记重写 `getFilename()`。** 某些网关会 400。

#### 面试能怎么讲

> "Media 是 Spring AI 的多模态附件抽象，由 MIME 类型加数据源组成，最终被翻译成协议的 image_url。要注意 MIME 必须和真实格式一致——我们踩过坑：前端压出来是 JPEG，代码却硬编码 image/png，靠网关自行嗅探才侥幸没出事。修复方式是按文件头 magic number 嗅探真实格式。另外 ByteArrayResource 的 getFilename 默认返回 null，部分网关要求必须有文件名，所以要用匿名子类重写补上。"

相关词条：[#springai-message](#springai-message)、[#multimodal](#multimodal)、[#resource-abstraction](#resource-abstraction)

---

<a id="tool-annotation"></a>

### @Tool 与工具注册

**一句话定义**：Spring AI 用 `@Tool` 标记方法为"可被模型调用的工具"，并自动生成 JSON Schema。

#### 为什么需要它

手写工具定义要拼 JSON Schema、写反射调用、处理结果序列化——`@Tool` 让这一切变成"加个注解"。

#### 它内部怎么工作

```
注册时（注意：不是容器启动时全局扫描）：
defaultTools(obj) / .tools(obj) 被调用
        → MethodToolCallbackProvider 反射扫描「该对象」的 @Tool 方法
        → 读取方法签名 + @ToolParam 注解
        → 生成 JSON Schema，包装成 MethodToolCallback
        → 挂到这个 ChatClient（或仅本次请求）

调用时：模型返回 tool_calls
        → 框架按 name 找到对应方法
        → 把 arguments 反序列化成参数
        → 反射调用
        → 返回值序列化成字符串，作为 role=tool 消息
```

注解对照：

```java
@Tool(description = "工具说明书：返回什么 + 何时该调")
public String searchProduct(
        @ToolParam(required = false, description = "关键词，如'耳机'") String keyword,
        @ToolParam(required = false, description = "分类id，可不传") Long categoryId) {
    // 方法体：把参数转成真实查询
    return objectMapper.writeValueAsString(resp);   // 返回值会原样作为工具结果
}
```

**注册方式**：

| 方式 | 写法 | 作用范围 |
|---|---|---|
| `defaultTools(...)` | 构建 ChatClient 时 | 该 Client 所有请求 |
| `.tools(...)` | 链式调用时 | 仅本次请求 |

```java
ChatClient.builder(chatModel)
        .defaultTools(productSearchTool)     // ← 这里
        .build();
```

#### 在本项目里怎么用

`ProductSearchTool` 是全局唯一的工具，注册在**文本链路**的 ChatClient 上。

**为什么视觉链路不注册？** 第 4 章案例四的真实事故：视觉模型收到"工具说明书"后开始胡言乱语。所以 `visionChatClient` 刻意"空手"。

**方法体内的防御是重点**（第 4.3 节）——模型是"不可信的外部调用者"：

```java
query.setKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim());
query.setCategoryId(categoryId != null && categoryId > 0 ? categoryId : null);
query.setPage(page == null || page < 1 ? 1 : page);
query.setPageSize(pageSize == null || pageSize < 1 ? 10 : pageSize);
```

还有一条重要设计：**工具抛异常 = 这次对话直接失败**，所以要用 try-catch 兜底，宁可返回空列表。

#### 常见误区

1. **参数设必填。** 模型会编造值交差（见 [工具 Schema](#tool-schema)）。
2. **方法体不做参数防御。** 模型可能传 null、0、负数。
3. **让异常冒出去。** 会导致整次对话失败。
4. **返回值不精简。** 工具返回值计入 token。

#### 面试能怎么讲

> "Spring AI 用 @Tool 标记工具方法，启动时扫描方法签名和 @ToolParam 自动生成 JSON Schema，调用时框架按 name 找到方法、反序列化参数、反射执行，把返回值序列化后作为 role=tool 消息发回。我们注册在文本链路的 ChatClient 上，视觉链路不注册——因为踩过视觉模型收到工具说明书后胡言乱语的坑。另外方法内必须对参数做防御，模型是不可信调用者，而且方法抛异常会导致整次对话失败，要用 try-catch 兜底。"

相关词条：[#function-calling](#function-calling)、[#tool-schema](#tool-schema)

---

<a id="advisor"></a>

### Advisor 拦截器

**一句话定义**：Spring AI 的**请求拦截器链**，类似 Servlet 的 Filter，可以在请求发出前/响应返回后插入逻辑。

#### 为什么需要它

有些横切逻辑不该塞进业务代码：

- 自动管理会话记忆（存取历史）
- 记录请求/响应日志
- RAG 检索并注入上下文
- 敏感词过滤

Advisor 就是为这些横切关注点设计的。

#### 它内部怎么工作

```
请求 → Advisor1 → Advisor2 → ... → ChatModel → 响应
         ↓                                      ↑
      改写请求                              改写响应
```

Spring AI 内置的常用 Advisor：

| Advisor | 作用 |
|---|---|
| `MessageChatMemoryAdvisor` | 自动把历史消息注入请求、把回答存入记忆 |
| `QuestionAnswerAdvisor` | **RAG 专用**：检索向量库并注入相关文档 |
| `SimpleLoggerAdvisor` | 打印请求/响应日志（调试神器） |
| `SafeGuardAdvisor` | 敏感词拦截 |

用法（构建 ChatClient 时或调用时）：

```java
ChatClient.builder(chatModel)
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                new SimpleLoggerAdvisor()
        )
        .build();
```

**执行顺序**：按注册顺序依次执行，可以指定 order。

#### 在本项目里怎么用

**本项目没有用 Advisor**——因为会话历史是**自己管理**的（存 MySQL、`toAiHistory()` 手动查出并拼进请求）。

这其实是**有意的选择**：

| | 用 ChatMemory + Advisor | 自己管（本项目） |
|---|---|---|
| 存储 | 框架提供（默认内存） | **MySQL，可持久化、可回显** |
| 控制力 | 弱 | 强（能按业务裁剪历史） |
| 前端回显 | 要另做 | 同一张表直接查 |
| 复杂度 | 低 | 中 |

本项目需要"历史消息在前端回显"（切会话时展示聊天记录），所以必须自己存表。用框架的 ChatMemory 反而要额外处理回显。

**但如果是简单场景**（不需要回显、不需要跨服务），用 `MessageChatMemoryAdvisor` 能省不少代码。

#### 常见误区

1. **以为 Advisor 能改模型参数。** 它主要改的是消息列表，参数用 `.options()`。
2. **以为用了 Advisor 就不需要自己存历史。** 取决于要不要回显和持久化。
3. **忽略执行顺序。** 多个 Advisor 有依赖关系时要注意 order。

#### 面试能怎么讲

> "Advisor 是 Spring AI 的拦截器链，类似 Filter，可以在请求前后插入横切逻辑，比如自动管理会话记忆、RAG 检索注入、打印日志。我们项目没用它，因为历史消息需要存 MySQL 供前端回显，框架的 ChatMemory 默认内存存储、回显还得另做，所以选择自己管理——用 toAiHistory 手动查库拼装。如果是不需要回显的简单场景，用 MessageChatMemoryAdvisor 能省不少代码。"

相关词条：[#chatclient](#chatclient)、[#chatmemory](#chatmemory)、[#rag](#rag)

---

<a id="chatmemory"></a>

### ChatMemory 会话记忆

**一句话定义**：Spring AI 提供的**会话历史管理抽象**，自动存取多轮对话的消息。

#### 为什么需要它

多轮对话要"每次都带上历史"。手写这套存取逻辑很繁琐，ChatMemory 提供了标准方案。

#### 它内部怎么工作

```
ChatMemory（接口）
   ├── add(conversationId, messages)     存
   ├── get(conversationId)               取
   └── clear(conversationId)             清

ChatMemoryRepository（存储实现）
   ├── InMemoryChatMemoryRepository      内存（默认，重启丢失）
   ├── JdbcChatMemoryRepository          Spring AI 1.0 内置，自动建表，支持 H2/MySQL/PG/SQLite
   └── 可自定义 JDBC / Redis 实现
```

配合 `MessageChatMemoryAdvisor`，可以做到**业务代码完全不感知历史管理**：

```java
ChatClient.builder(chatModel)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();

// 调用时带上会话 id
chatClient.prompt()
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
        .user(question)
        .call().content();
```

框架会自动：请求前取出历史注入 → 请求后把问答存入记忆。

#### 在本项目里怎么用

**本项目没有用 ChatMemory**（原因见 [Advisor](#advisor)）：需要历史持久化到 MySQL 并供前端回显。

本项目的等价实现：

| ChatMemory 负责 | 本项目对应 |
|---|---|
| `add()` 存消息 | `saveMessage()` + `t_message` 表 |
| `get()` 取历史 | `messageMapper.selectByConversationId()` |
| 会话 id | `conversationId` 字段 |
| 自动注入请求 | `toAiHistory()` 手动 + `.messages(...)` |

**换句话说：本项目手写了一个"带持久化和回显能力的 ChatMemory"。**

#### 常见误区

1. **以为默认实现能持久化。** 默认是内存存储，重启即丢。生产要自定义 Repository。
2. **以为用了 ChatMemory 就不需要自己的消息表。** 如果要做前端回显、历史查询、数据分析，还是要自己存。
3. **忽略历史膨胀。** ChatMemory 也要考虑窗口限制。

#### 面试能怎么讲

> "ChatMemory 是 Spring AI 的会话记忆抽象，配合 MessageChatMemoryAdvisor 能自动存取历史，业务代码不感知。我们项目没直接用它——因为需要历史持久化到 MySQL 并在前端回显，框架的默认实现是内存的、回显还得另做，所以我们自己用 t_message 表加 toAiHistory 实现了一套等价能力。可以理解为'手写了一个带持久化和回显的 ChatMemory'。"

相关词条：[#advisor](#advisor)、[#chatclient](#chatclient)、[#context-window](#context-window)

---

<a id="vectorstore-springai"></a>

### VectorStore 向量存储

**一句话定义**：Spring AI 对向量数据库的**统一抽象**，让你换向量库只改配置不改代码。

#### 为什么需要它

向量库有很多种（pgvector、Milvus、Qdrant、Redis...），每种 API 都不同。Spring AI 用 `VectorStore` 接口屏蔽差异。

#### 它内部怎么工作

```java
public interface VectorStore {
    void add(List<Document> documents);              // 存入（自动 embedding）
    void delete(List<String> idList);
    List<Document> similaritySearch(SearchRequest request);   // 相似度检索
}
```

核心数据类型是 `Document`：

```java
Document doc = new Document(
        "AirSound Pro 真无线降噪耳机，40dB 主动降噪",   // 文本内容
        Map.of("name", "AirSound Pro", "price", 399)   // 元数据
);
```

**`add()` 会自动调用 Embedding 模型把文本转向量**——你不需要自己算。

检索：

```java
List<Document> results = vectorStore.similaritySearch(
        SearchRequest.builder()
                .query("通勤想安静听歌")    // 自然语言提问
                .topK(5)                   // 取最相似的 5 条
                .build());
```

#### 在本项目里怎么用

**V1 未使用**，这是 RAG 演进方向。

如果 V2 要上，典型流程：

```
1. 商品上架/更新时 → 构造 Document → vectorStore.add()
2. 用户提问时     → similaritySearch 取 Top-K
                  → 拼进 prompt → 模型基于真实商品回答
```

Spring AI 还支持 **ETL 管道**（读取文档 → 切分 → 向量化 → 存储），以及 `QuestionAnswerAdvisor` 把检索+注入做成一行配置。

#### 常见误区

1. **以为 VectorStore 会帮你管理原文。** 它存向量和元数据，原文建议留在业务库里用 ID 关联。
2. **以为 add 时不消耗 token。** 会自动调 Embedding 模型，是收费的。
3. **数据更新时忘记同步向量。** 商品改价后要重新 add，否则检索到的是旧数据。

#### 面试能怎么讲

> "VectorStore 是 Spring AI 对向量库的统一抽象，提供 add 和 similaritySearch 两个核心方法，add 时会自动调 Embedding 模型转向量，换向量库只改配置。我们项目 V1 没用到，规划里商品量上来后做 RAG 会引入。工程上要注意原文和向量的同步——商品改价后要重新 add，否则检索到旧数据。"

相关词条：[#vector-db](#vector-db)、[#rag](#rag)、[#embedding](#embedding)

---

<a id="observation"></a>

### 可观测性 Observability

**一句话定义**：让 AI 调用过程**可监控、可追踪、可度量**——知道每次调用花了多久、多少 token、成功与否。

#### 为什么需要它

AI 调用有三个特点让它特别需要可观测：

1. **慢**（几秒到几十秒）
2. **贵**（按 token 计费）
3. **不稳定**（可能超时、限流、返回异常）

没有监控，成本失控和故障定位都会变成灾难。

#### 它内部怎么工作

Spring AI 基于 Micrometer 的 Observation API，自动记录：

| 指标 | 说明 |
|---|---|
| 调用次数 | 按模型、按操作统计 |
| 耗时 | 首 token 延迟、总耗时 |
| Token 消耗 | prompt / completion tokens |
| 错误率 | 失败次数与类型 |

配置开启：

```yaml
management:
  observations:
    enable: true
  endpoints:
    web:
      exposure:
        include: metrics,health
```

> ⚠️ **前提**：这些指标要生效，需要先加 `spring-boot-starter-actuator` 和一个 meter registry（如 `micrometer-registry-prometheus`）。**本项目 pom 目前没有引入 actuator**，所以上面这些指标在本项目里实际是取不到的——这是上生产前要补的第一步。加上后用 `management.endpoints.web.exposure.include` 暴露，即可访问 `/actuator/metrics/gen_ai_client_operation_seconds`、`gen_ai_client_token_usage` 等指标。属性名是 `management.observations.enabled`（且默认就是 true，通常不用写）。

调试期还可以直接看日志：

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
```

（注意：DEBUG 会打印完整请求，含 base64 图片，日志会爆炸——只在调试时开，且用小图。）

#### 在本项目里怎么用

**V1 只做了最基础的**：`logging.level.com.aimall: info` + 关键节点的 `log.error`。

如果要上生产，建议补：

1. **Token 统计**：从响应的 `usage` 里取，按用户/会话统计成本
2. **耗时监控**：特别是首 token 延迟（影响体验）
3. **失败告警**：模型调用失败率突增时告警

#### 常见误区

1. **只在出错时记日志。** 正常调用的耗时和 token 也要记，那是成本和体验的依据。
2. **生产开 DEBUG 日志。** 会打印完整请求体（含 base64 图片），日志量爆炸且可能泄露数据。
3. **忽略 token 统计。** 等账单来了才发现超支。

#### 面试能怎么讲

> "AI 调用慢、贵、不稳定，所以可观测性很重要。Spring AI 基于 Micrometer 的 Observation API 自动记录调用次数、耗时、token 消耗和错误率。我们项目 V1 只做了基础日志，要上生产我会补三块：从响应的 usage 里统计 token 成本、监控首 token 延迟（直接影响体验）、失败率告警。注意生产不能开 DEBUG，会把含 base64 图片的完整请求打进日志。"

相关词条：[#chatclient](#chatclient)、[#token](#token)

---

<a id="fetch-api"></a>

### fetch API

**一句话定义**：浏览器原生的 HTTP 请求 API，Promise 风格，是 `XMLHttpRequest` 的现代替代。

#### 为什么需要它

发 AJAX 请求的老方案是 `XMLHttpRequest`（jQuery 的 `$.ajax` 底层就是它），回调风格、API 繁琐。`fetch` 更简洁且基于 Promise，还能拿到**响应流**（这是本项目选它的关键原因，见 [EventSource vs fetch](#eventsource)）。

#### 它内部怎么工作

```javascript
const resp = await fetch('/api/v1/chat', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Authorization': token
    },
    body: JSON.stringify({ message: '推荐一款耳机' })
});
const data = await resp.json();       // 解析 JSON
```

**几个必须知道的坑**：

1. **`fetch` 只在网络错误时 reject**——HTTP 404、500 **不会** reject，要自己检查 `resp.ok`：

```javascript
if (!resp.ok) throw new Error('HTTP ' + resp.status);
```

2. **默认不携带 Cookie**，需要 `credentials: 'include'`

3. **响应体只能读一次**——调了 `resp.json()` 就不能再调 `resp.text()`

4. **没有超时机制**，要自己用 `AbortController` 实现：

```javascript
const controller = new AbortController();
setTimeout(() => controller.abort(), 30000);
fetch(url, { signal: controller.signal });
```

#### 在本项目里怎么用

`frontend/src/api/index.js` 里用 fetch 封装了全部接口。非流式接口：

```javascript
const resp = await fetch('/api/v1/chat', { method: 'POST', headers, body });
const data = await resp.json();
// data = { code, msg, data }
```

流式接口 `sendStream` 用的是 `resp.body`（见 [ReadableStream](#readable-stream)）。

#### 常见误区

1. **以为 HTTP 错误会进 catch。** 不会，必须检查 `resp.ok`。
2. **以为 fetch 自带超时。** 没有，要自己实现。
3. **重复读响应体。** 会报错。

#### 面试能怎么讲

> "fetch 是浏览器原生的 Promise 风格请求 API。要注意它只在网络错误时 reject，HTTP 4xx/5xx 不会，必须自己检查 resp.ok；另外它不自带超时，得用 AbortController 实现。我们项目全部接口都用 fetch 封装，流式接口则利用 resp.body 拿到可读流逐块处理。"

相关词条：[#readable-stream](#readable-stream)、[#eventsource](#eventsource)

---

<a id="readable-stream"></a>

### ReadableStream 流读取

**一句话定义**：浏览器 Streams API 的一部分，让你可以**逐块读取**响应体，而不用等全部下载完。

#### 为什么需要它

普通 `resp.json()` 会等整个响应下载完才解析。流式响应是**持续到达**的，必须边收边处理——ReadableStream 就是干这个的。

#### 它内部怎么工作

```
resp.body（ReadableStream）
   ↓ getReader()
reader（ReadableStreamDefaultReader）
   ↓ await reader.read() 循环
{ done: false, value: Uint8Array }   ← 每次拿到一块字节
   ↓ TextDecoder 解码
字符串片段
   ↓ 按 SSE 格式解析（分割 data: 块）
业务数据
```

核心循环：

```javascript
const reader = resp.body.getReader();
const decoder = new TextDecoder('utf-8');
let buffer = '';

while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    // 关键：增量模式，处理跨块的多字节字符
    buffer += decoder.decode(value, { stream: true });

    const lines = buffer.split('\n');
    buffer = lines.pop();          // 最后一个可能不完整，留到下次

    for (const line of lines) {
        if (line.startsWith('data:')) {
            const payload = line.slice(5).trim();
            if (payload && payload !== '[DONE]') {
                onChunk(payload);   // 交给业务处理
            }
        }
    }
}
```

**三个关键点**：

1. **`decoder.decode(value, { stream: true })`**——增量模式。不传 `stream: true` 时，一个中文字符如果被拆到两个块里就会解码成乱码。这是流式中文乱码的**头号原因**。
2. **`buffer = lines.pop()`**——最后一行可能只收到了半个事件，必须留着和下一块拼接。
3. **循环直到 `done: true`**——流结束。

#### 在本项目里怎么用

`frontend/src/api/index.js` 的 `sendStream` 就是上面这套逻辑（第 50-82 行）。

`Chat.vue` 收到每个 chunk 后：

```javascript
acc += chunk;              // 累积
// 触发 Vue 响应式更新 → 界面打字机效果
```

**一个已知的小现象**：因为 `SseEmitter` 会把含换行的元素拆成多行 `data:`，前端按 `\n` split 后逐行回调，**换行符会被吞掉**。所以 AI 回答里的换行在界面上可能表现异常。

#### 常见误区

1. **`TextDecoder` 不传 `{stream: true}`。** 偶发中文乱码，且极难排查。
2. **忘记保留半个事件。** `lines.pop()` 那行不能省，否则会丢数据或解析出半个 JSON。
3. **以为 `value` 是字符串。** 它是 `Uint8Array`（字节），必须解码。

#### 面试能怎么讲

> "流式响应用 resp.body.getReader() 拿到 reader，循环 read() 逐块读取，每块是 Uint8Array，用 TextDecoder 解码。关键有三点：TextDecoder 必须用增量模式 {stream:true}，否则多字节的中文字符跨块时会乱码；收到的最后一行可能不完整，要留到下一块拼接；循环直到 done。我们项目前端就是这么解析 SSE 的。"

相关词条：[#fetch-api](#fetch-api)、[#sse-protocol](#sse-protocol)、[#content-type](#content-type)

---

<a id="eventsource"></a>

### EventSource vs fetch

**一句话定义**：`EventSource` 是浏览器**原生**的 SSE 客户端，但本项目**没用它**，用的是 fetch + ReadableStream。

#### 为什么需要对比

"看 SSE 为什么不用浏览器自带的 EventSource？"——这是面试常见的追问，要能说清取舍。

#### 它内部怎么工作

| 维度 | EventSource | fetch + ReadableStream |
|---|---|---|
| 请求方法 | **只能 GET** | 任意（POST/PUT...） |
| 自定义请求头 | **不支持** | ✅ 支持 |
| 请求体 | **不能带** | ✅ 可以带 |
| 自动重连 | ✅ **原生支持** | ❌ 要自己写 |
| 解析 SSE 格式 | ✅ 自动 | ❌ 要自己解析 |
| 控制粒度 | 低 | **高** |

**EventSource 的致命限制**：

```javascript
const es = new EventSource('/api/v1/chat/stream');
// 问题：
// 1. 只能发 GET，不能 POST
// 2. 不能设置 Authorization 头
// 3. 不能带请求体
```

而本项目的流式接口是 **POST + JSON body + Authorization 头**——EventSource 三项全都做不到。

#### 在本项目里怎么用

所以本项目用 **fetch + ReadableStream 手撕 SSE 解析**（`frontend/src/api/index.js` 的 `sendStream`）。

代价是失去了两个原生能力，需要自己补：

| 失去的 | 怎么补 |
|---|---|
| 自动重连 | 在 `onError` 里自己实现重连逻辑（本项目未实现） |
| 自动解析 SSE | 手动 split + 判断 `data:` 前缀（已实现） |
| `Last-Event-ID` 断点续传 | 未实现 |

**什么时候可以用 EventSource？** 如果接口设计成 GET + token 放 URL 参数（或 Cookie），EventSource 会更省事。但把 token 放 URL 不安全（会被日志记录），所以本项目选择了 POST + 自定义头。

#### 常见误区

1. **以为 EventSource 能发 POST。** 不能，这是硬限制。
2. **以为不用 EventSource 就不能自动重连。** 要自己实现，但在需要认证的场景下这是必须付的代价。
3. **以为手撕解析很难。** 核心就是 split + startsWith('data:')，十几行代码。

#### 面试能怎么讲

> "浏览器原生的 EventSource 只能发 GET、不能自定义请求头、不能带请求体，而我们的流式接口是 POST + JSON body + Authorization 认证头，三项都用不了，所以用 fetch 配合 ReadableStream 自己解析 SSE。代价是要自己处理重连和格式解析，但换来了对请求方式和认证的完整控制。"

相关词条：[#fetch-api](#fetch-api)、[#readable-stream](#readable-stream)、[#sse-protocol](#sse-protocol)

---

<a id="canvas-compress"></a>

### canvas 图片压缩

**一句话定义**：用 HTML5 Canvas 在**浏览器端**把图片重绘到更小尺寸并重新编码，从而减小体积。

#### 为什么需要它

**不压缩就是烧钱 + 拖慢速度**：

- 原图动辄几 MB，base64 后更大，HTTP 传输慢
- 图片按尺寸折算 token 计费（见 [图片的 token 计费](#vision-token)）
- 大图 base64 曾撑爆数据库字段（第 9 章案例一：TEXT 64KB 不够用）

#### 它内部怎么工作

```javascript
function compressImage(file) {
    return new Promise(resolve => {
        const img = new Image();
        img.onload = () => {
            // ① 算缩放比例：最长边压到 maxSize
            const scale = Math.min(1, maxSize / Math.max(img.width, img.height));
            const width  = Math.round(img.width * scale);
            const height = Math.round(img.height * scale);

            // ② 建 canvas 并按新尺寸绘制
            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const ctx = canvas.getContext('2d');

            // ③ 铺白底：PNG 的透明区转 JPEG 会变黑
            ctx.fillStyle = '#fff';
            ctx.fillRect(0, 0, width, height);

            ctx.drawImage(img, 0, 0, width, height);

            // ④ 导出 JPEG，质量 0.8
            resolve(canvas.toDataURL('image/jpeg', 0.8));
        };
        img.src = URL.createObjectURL(file);
    });
}
```

**四个参数为什么这么选**：

| 参数 | 值 | 理由 |
|---|---|---|
| 最长边 | 1280px | 再小影响识别准确率，再大纯费 token |
| 格式 | JPEG | 比 PNG 小得多；识图不需要无损 |
| 质量 | 0.8 | 肉眼几乎无损，体积能省一半以上 |
| 背景 | 白底 | **PNG 转 JPEG 时透明区会变黑** |

#### 在本项目里怎么用

`Chat.vue` 的 `compressImage()`（约第 157-177 行），在用户选图后调用，产出的 dataURL 用于：

1. **前端预览**（直接塞进 `<img src>`）
2. **上传后端**（放进 JSON 的 `image` 字段）

**同一份数据两用**——这是 dataURL 的便利之处。

#### 常见误区

1. **只压质量不压尺寸。** 对 token 计费来说，**尺寸才是关键**，质量次之。
2. **忘记铺白底。** 带透明通道的 PNG 转 JPEG 会变黑，识别准确率骤降。
3. **以为压缩不影响识别。** 压太狠（比如 300px）会丢失关键细节，要找平衡点。

#### 面试能怎么讲

> "前端用 canvas 压缩：按最长边缩到 1280px，铺白底后重绘，导出 JPEG 质量 0.8。选这几个值都有理由——尺寸再小影响识别、再大纯烧 token（图片按尺寸计 token），JPEG 比 PNG 小得多，白底是因为 PNG 的透明区转 JPEG 会变黑。压缩后体积通常能降一个数量级，既省传输又省 token。"

相关词条：[#dataurl-blob](#dataurl-blob)、[#vision-token](#vision-token)、[#base64](#base64)

---

<a id="dataurl-blob"></a>

### dataURL / Blob / File

**一句话定义**：浏览器里表示"二进制数据"的三种形态，各有适用场景。

#### 为什么需要区分

处理图片时你会在这三者之间来回转换，搞清楚区别能少踩很多坑。

#### 它内部怎么工作

| 形态 | 本质 | 典型样子 | 用途 |
|---|---|---|---|
| **File** | 用户选择的文件对象 | `<input type=file>` 的产物 | 上传入口 |
| **Blob** | 二进制大对象 | `Blob {size: 2048576, type: "image/jpeg"}` | 内存中存二进制、分片上传 |
| **dataURL** | **base64 编码的字符串** | `data:image/jpeg;base64,/9j/4AA...` | 塞进 JSON、直接当 img src |

**转换关系**：

```
File ──(本身就是 Blob 的子类)──► Blob
Blob ──FileReader.readAsDataURL──► dataURL
dataURL ──自己解析 base64──► byte[] / Blob
Blob ──URL.createObjectURL──► blob URL（blob:http://...，仅当前页面有效，记得 revoke）
```

**关键差异**：

| | dataURL | Blob URL |
|---|---|---|
| 形式 | 长字符串（base64） | 短 URL，指向内存 |
| 体积 | **膨胀 1/3** | 无膨胀 |
| 能否放进 JSON | ✅ | ❌（只在当前页面有效） |
| 内存 | 占字符串内存 | 占二进制内存 |

#### 在本项目里怎么用

本项目**全程用 dataURL**，因为要塞进 JSON 发给后端：

```javascript
// 用户选图 → File
//     ↓ compressImage()（内部 canvas.toDataURL）
//   dataURL（data:image/jpeg;base64,xxx）
//     ↓ 放进 JSON 的 image 字段
//   后端 decodeImage() 剥掉前缀、Base64 解码 → byte[]
```

`decodeImage()` 里的"剥前缀"逻辑就是处理 dataURL 格式：

```java
int idx = image.indexOf("base64,");
if (idx >= 0) {
    data = image.substring(idx + 7);    // "base64," 正好 7 个字符
}
```

**注意**：后端也兼容**裸 base64**（没有前缀），因为不能保证前端一定发 dataURL。

#### 常见误区

1. **把 dataURL 当 URL 用。** 它不是网络地址，只是编码后的数据。
2. **用 `createObjectURL` 后忘记 `revokeObjectURL`。** 内存泄漏。
3. **以为 dataURL 能直接当文件上传。** 后端要自己解码。

#### 面试能怎么讲

> "File 是用户选的文件，Blob 是内存中的二进制对象，dataURL 是 base64 编码的字符串。我们项目全程用 dataURL，因为要塞进 JSON 发给后端——代价是体积膨胀约 1/3。后端做了兼容，既能处理带 data:image/jpeg;base64, 前缀的，也能处理裸 base64，靠找 'base64,' 这个标记来切分。"

相关词条：[#base64](#base64)、[#canvas-compress](#canvas-compress)

---

<a id="localstorage"></a>

### 前端存储 localStorage

**一句话定义**：浏览器提供的**键值对本地存储**，数据持久化在浏览器里，关闭页面也不丢。

#### 为什么需要它

登录后拿到的 token 要保存下来，否则刷新页面就得重新登录。

#### 它内部怎么工作

```javascript
localStorage.setItem('token', '33d60866-...');
const token = localStorage.getItem('token');
localStorage.removeItem('token');      // 退出登录
localStorage.clear();                   // 清空
```

**只能存字符串**——存对象要序列化：

```javascript
localStorage.setItem('user', JSON.stringify({id: 1, name: '张三'}));
const user = JSON.parse(localStorage.getItem('user'));
```

三种浏览器存储对比：

| | localStorage | sessionStorage | Cookie |
|---|---|---|---|
| 生命周期 | **永久**（除非手动清） | 标签页关闭即清 | 可设过期时间 |
| 容量 | ~5MB | ~5MB | ~4KB |
| 每次请求自动携带 | ❌ | ❌ | ✅ |
| 能否设 HttpOnly | ❌ | ❌ | ✅ |

#### 在本项目里怎么用

`frontend/src/api/index.js` 里把登录返回的 token 存进 localStorage，之后每个请求从里面取出放进 `Authorization` 头：

```javascript
headers: { 'Authorization': localStorage.getItem('token') || '' }
```

**安全提示**：localStorage **对 JS 完全可读**，意味着**存在 XSS 风险**——如果页面被注入恶意脚本，token 会被窃取。

更安全的方案是 HttpOnly Cookie（JS 读不到），但需要后端配合且要处理 CSRF。本项目为简单起见用了 localStorage，属于 V1 的简化取舍。

#### 常见误区

1. **在 localStorage 存敏感信息（密码、身份证）。** 任何 JS 都能读到，XSS 一下全泄露。
2. **以为它有过期机制。** 没有，要自己实现过期判断。
3. **存大对象不序列化。** 会存成 `[object Object]`。

#### 面试能怎么讲

> "localStorage 是浏览器本地键值对存储，5MB 左右，持久化。我们用它存登录 token，每次请求取出放进 Authorization 头。但要知道它对 JS 完全可读，有 XSS 风险——更安全的做法是 HttpOnly Cookie，只是要额外处理 CSRF，我们 V1 为了简单选了 localStorage。"

相关词条：[#auth-token](#auth-token)

---

<a id="mysql-text"></a>

### MySQL TEXT vs MEDIUMTEXT

**一句话定义**：MySQL 存储长文本的几种类型，区别只在**容量上限**。

#### 为什么需要它

本项目第 9 章案例一的真实事故：用户发大图，`extra_json` 字段是 `TEXT`（上限 64KB），装不下 base64，INSERT 直接失败 → 500。

#### 它内部怎么工作

| 类型 | 最大长度 | 大约能装 |
|---|---|---|
| `TINYTEXT` | 255 B | 一句话 |
| `TEXT` | **64 KB** | 短文章 |
| `MEDIUMTEXT` | **16 MB** | 长文章、小图片 base64 |
| `LONGTEXT` | 4 GB | 超大文本 |

**为什么 base64 会撑爆 64KB？**

```
一张 1280×1280 的 JPEG，质量 0.8 → 约 200~400 KB
  ↓ base64 编码（×1.33）
约 270~530 KB
  ↓
远超 TEXT 的 64 KB → DataIntegrityViolationException → 500
```

修复（第 9 章案例一）：

```sql
ALTER TABLE t_message MODIFY extra_json MEDIUMTEXT;
```

**选型的注意点**：

1. **不是越大越好**——大字段会影响查询性能和内存占用
2. **TEXT 类型不能有默认值**（MySQL 限制）
3. **大字段建议单独建表**或走对象存储，避免拖慢主表查询
4. **`SELECT *` 会把大字段一起捞出来**，列表查询时尤其浪费

#### 在本项目里怎么用

- `t_message.extra_json` 已改为 `MEDIUMTEXT`（16MB），存图片 base64
- `listMessages()` 会查出 `extra_json` 并映射成 `MessageVO.image` 供前端回显

**这是 V1 的简化方案**，代价已写在主笔记 5.7 节：DB 膨胀、列表接口重。V2 方向是对象存储（见 [对象存储 OSS](#oss)）。

#### 常见误区

1. **以为 TEXT 够装任何文本。** 只有 64KB，图片 base64 轻松超。
2. **滥用 LONGTEXT。** 4GB 上限，但会拖垮性能。
3. **列表查询 `SELECT *` 带出大字段。** 应只 SELECT 需要的列。

#### 面试能怎么讲

> "MySQL 的 TEXT 上限 64KB，MEDIUMTEXT 是 16MB，LONGTEXT 是 4GB。我们踩过真实事故：用户发大图，extra_json 是 TEXT，base64 编码后几百 KB 直接撑爆，INSERT 失败返回 500。修复是 ALTER 成 MEDIUMTEXT。但这只是 V1 的权宜之计，代价是数据库膨胀、列表查询变重，V2 应该改成对象存储只存 URL。"

相关词条：[#oss](#oss)、[#base64](#base64)、[#db-index](#db-index)

---

<a id="oss"></a>

### 对象存储 OSS

**一句话定义**：专门存文件（图片、视频）的云存储服务，通过 HTTP URL 访问。

#### 为什么需要它

把图片 base64 存进 MySQL 的问题是（主笔记 5.7 节）：

| 问题 | 说明 |
|---|---|
| DB 膨胀 | 几百 KB 一条消息，表很快就几个 G |
| 查询变重 | 列表接口也会捞出大字段 |
| 无法缓存 | 数据库不像 CDN 能做边缘缓存 |
| 备份恢复慢 | 大表备份极慢 |

对象存储就是为解决这些而生的。

#### 它内部怎么工作

```
上传：  前端 →（直传或后端转发）→ OSS → 返回 URL
存储：  OSS 内部按对象存储，支持版本、生命周期、权限
访问：  https://bucket.oss-cn-hangzhou.aliyuncs.com/product/123.jpg
```

主流选择：

| 厂商 | 产品 |
|---|---|
| 阿里云 | OSS |
| 腾讯云 | COS |
| AWS | S3（行业事实标准） |
| MinIO | 开源自建（S3 兼容） |

**核心概念**：

- **Bucket**：存储空间（类似顶层文件夹）
- **Object**：一个文件
- **Key**：文件在 bucket 里的路径
- **签名 URL**：带时效的临时访问链接（私有文件用）

#### 在本项目里怎么用

**V1 未使用**，是明确的 V2 演进方向。改造后的收益：

| 环节 | V1（base64 存 DB） | V2（OSS 存 URL） |
|---|---|---|
| DB | 膨胀 | **只存一个 URL 字符串** |
| 前端压缩 | 必须压 | 仍建议压（省流量） |
| 传给模型 | base64 data URL | **直接传 http URL**（模型自己去下载） |
| 历史回显 | 从 DB 读 base64 | `<img src="URL">` |
| 编码开销 | 两次 base64 编解码 | **零** |

**注意**：改传 URL 后，模型需要能**公网访问**这个 URL。如果用私有 bucket，要生成带签名的临时 URL。

#### 常见误区

1. **以为上了 OSS 就不用压缩了。** 仍要压，省的是用户流量和 CDN 成本。
2. **用公开读权限图省事。** 私有文件要用签名 URL，否则有泄露风险。
3. **以为改造只是换存储。** 前端上传流程、历史数据迁移都要跟着改。

#### 面试能怎么讲

> "V1 把图片 base64 存进 extra_json，好处是少一个外部依赖、base64 本来就要喂模型，代价是数据库膨胀、列表查询变重、无法做 CDN 缓存。V2 我会改成对象存储：图片传 OSS 拿 URL，DB 只存 URL 字符串，调视觉模型也直接传 URL 让它自己去下载——这样连 base64 的两次编码开销都省了。注意如果用私有 bucket，给模型访问要生成带签名的临时 URL。"

相关词条：[#mysql-text](#mysql-text)、[#vision-token](#vision-token)

---

<a id="db-index"></a>

### 索引与查询优化

**一句话定义**：索引是数据库的"目录"，让查询不必全表扫描。

#### 为什么需要它

数据量小的时候感觉不到，上万条后没有索引的查询会从"毫秒"变成"秒级"。

#### 它内部怎么工作

MySQL 索引默认是 **B+ 树**：

```
无索引：  SELECT * FROM t_message WHERE conversation_id = 5
          → 逐行比对整张表（全表扫描，O(n)）

有索引：  先按索引树定位到 conversation_id=5 的位置
          → 直接取出那几行（O(log n)）
```

**建索引的原则**：

| 原则 | 说明 |
|---|---|
| 高区分度 | 性别这种只有两个值的字段建索引意义不大 |
| 遵循最左前缀 | 联合索引 `(a,b,c)` 能加速 `a`、`(a,b)`、`(a,b,c)`，不能加速 `b` |
| 不要过度 | 索引占空间，且**拖慢写入**（每次 INSERT 要维护索引） |
| 覆盖索引 | 查询的字段都在索引里时，不用回表，极快 |

**用 `EXPLAIN` 看查询是否走了索引**：

```sql
EXPLAIN SELECT * FROM t_message WHERE conversation_id = 5;
-- 看 key 列是否显示用了索引，type 列最好是 ref/range，最差是 ALL（全表扫描）
```

#### 在本项目里怎么用

`t_message` 上有 `idx_conversation` 索引（建表脚本里），因为核心查询是：

```sql
SELECT ... FROM t_message WHERE conversation_id = ? ORDER BY created_at ASC, id ASC
```

按 `conversation_id` 过滤，这个索引是**必需的**。

其他表的主键、外键字段（如 `t_order_item.order_id`）也应有索引。

#### 常见误区

1. **以为建了索引就一定生效。** 有例外：对字段用了函数、隐式类型转换、`OR` 连接、以 `%` 开头的 LIKE，都会导致索引失效。
2. **索引越多越好。** 拖慢写入，占空间。
3. **不看 `EXPLAIN` 凭感觉优化。** 一定要用 EXPLAIN 验证。

#### 面试能怎么讲

> "索引本质是用 B+ 树做目录，把全表扫描的 O(n) 变成 O(log n)。建索引要看区分度、遵循最左前缀，但也不能过度——索引占空间且拖慢写入。我们项目在 t_message 的 conversation_id 上建了索引，因为查历史消息是最高频的查询。排查慢查询我会先看 EXPLAIN 的 type 和 key，确认有没有走索引。"

相关词条：[#pagination](#pagination)、[#mysql-text](#mysql-text)

---

<a id="pagination"></a>

### 分页 Pagination

**一句话定义**：把大量数据分成一页一页返回，而不是一次性全给。

#### 为什么需要它

一次查一万条商品：网络传输慢、内存占用高、前端渲染卡死。

#### 它内部怎么工作

MySQL 的分页语法：

```sql
SELECT * FROM t_product
WHERE status = 1
ORDER BY id DESC
LIMIT #{offset}, #{size}        -- offset = (page-1) * pageSize
```

**深分页问题**（面试高频）：

```sql
LIMIT 1000000, 20      -- 数据库要先扫过前 100 万行再取 20 行，极慢
```

优化方案：**延迟关联 / 子查询先定位 ID**

```sql
-- 先用覆盖索引拿到 ID（不回表，快），再关联取数据
SELECT p.* FROM t_product p
INNER JOIN (SELECT id FROM t_product WHERE status=1 ORDER BY id DESC LIMIT 1000000, 20) t
ON p.id = t.id;
```

或者**游标分页**（用上一页最后的 ID 作为起点）：

```sql
SELECT * FROM t_product WHERE status=1 AND id < #{lastId} ORDER BY id DESC LIMIT 20;
-- 优点：性能恒定；缺点：不能跳页
```

#### 在本项目里怎么用

项目有统一的 `PageQuery`（page / pageSize）和 `PageResult<T>`（records / total）。

`PageQuery` 上加了 `@Valid`，配合全局异常处理器处理 `BindException`（第 3 个知识点提到的真实修复）：

```
GET /products?pageSize=9999  →  400 "pageSize 每页最多 100 条"
```

**这个校验很重要**——不限制 pageSize 的话，一个 `pageSize=999999` 的请求就能把服务器拖垮。

#### 常见误区

1. **不限制 pageSize。** 等于给别人一个"拖垮服务器"的接口。
2. **深分页不做优化。** 数据量大后必然出问题。
3. **`ORDER BY` 字段不唯一导致分页结果重复/遗漏。** 要用唯一字段（如 id）做兜底排序。

#### 面试能怎么讲

> "分页用 LIMIT offset, size，offset 是 (page-1)*pageSize。两个要点：一是必须限制 pageSize 上限，不然一个超大请求就能拖垮服务，我们项目加了 @Valid 校验，超过 100 直接返回 400；二是深分页（offset 很大）会变慢，因为要扫过前面所有行，优化方式是先用覆盖索引拿 ID 再关联，或者改成游标分页——用上一页最后的 ID 当起点，性能恒定。"

相关词条：[#db-index](#db-index)、[#validation](#validation)

---

