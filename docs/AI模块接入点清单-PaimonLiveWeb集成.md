# ai-mall AI 模块接入点清单（PaimonLiveWeb 语音+动效集成）

> 任务:t3 · 角色:aimall-observer · 只扫描/分析/设计,不改任何代码
> 目标:把 PaimonLiveWeb5(派蒙角色扮演聊天 AI:Live2D 皮套 + TTS 语音 + DeepSeek 人设对话)的「语音+动效」能力融入 ai-mall AI 购物助手,保留 ai-mall 购物功能,在派蒙 LiveWeb 风格基础上增加「购物助理会话切换」。本清单只界定「ai-mall 侧」的接入点与最小侵入边界。

---

## 0. 一句话概括

ai-mall 的 AI 模块是一个**独立、自洽、侵入面极小**的「聊天助手」子域:`/api/v1/chat/**` 一组 5 个 REST/SSE 接口,背后是 Spring AI 的两个 `ChatClient`(文本/视觉),数据落在 `t_conversation`/`t_message` 两张表。它**不依赖**商城任何购物链路,只单向复用 `ProductService`(通过 `ProductSearchTool`)做商品检索。因此集成 PaimonLiveWeb 的「语音+动效」时,**购物功能完全可以一行不动**,只需在 AI 模块内部换「面板/客户端/提示词/会话类型」即可。

> **关键结构事实**:ai-mall 的「AI 购物助手」本身就是「一个带人设的聊天助手」——后端已有 `bizType`(会话类型)字段且注释里明确预留了 `CHAT_GOODS`/`SHOPPING` 扩展;前端 `Chat.vue` 本质就是「面板 + 会话列表 + 消息区」。这与派蒙 LiveWeb 的「面板 + 会话」几乎同构,是本次集成天然的低摩擦基础。

---

## 1. 总体边界界定原则(先定原则再列点)

| 原则 | 含义 |
|---|---|
| **购物功能完全不动** | `goods`/`cart`/`order`/`address`/`auth` 等模块的 Controller/Service/Mapper/表 `t_*`(除 `t_conversation`/`t_message`)一律零改动。AI 模块只复用它们的 Service,不侵入它们。 |
| **AI 模块自成一条链路** | 所有「语音/动效/会话切换」相关改动都收敛在 `com.aimall.ai` 包 + 前端 `Chat.vue` + `chatApi` + `AiConfig` + `application.yml`(ai 配置段)内。 |
| **面向接口编程** | `ChatService` 是接口,`ChatServiceImpl` 是实现。换实现(加 RAG/换模型/加语音人设)Controller 不动(依赖倒置,已有注释明说)。 |
| **后端定「人设与形态」,前端定「表现与切换」** | 人设提示词、会话类型、语音/动效归属由后端下发;Live2D/TTS 播放、会话切换按钮由前端承载。 |
| **兼容现有契约** | 保留现有 5 个 REST + SSE 接口语义,新增能力走「新字段/新端点/新 bizType」,不破坏 `chatApi` 现有调用方。 |

---

## 2. 前端接入点清单

### 2.1 `frontend/src/views/Chat.vue`(AI 种草助手面板)
**职责**:整张聊天面板——会话列表(左)、消息区(中)、输入区(下),带图片压缩/粘贴识别、流式/非流式开关。是目前 ai-mall 的「AI 助手 UI 唯一面」。

**可复用点(本次集成直接复用,不用改)**
- 会话列表 `conv-list` ↔ `loadConversations()` ↔ `chatApi.conversations()`:已是「多会话切换」的完整骨架。
- 消息区 `msg-area` ↔ `messages` ↔ `switchConversation()` ↔ `chatApi.messages(id)`:已是「按会话回显历史」。
- SSE 流式渲染管线:`send()` + `sendStream(onChunk/onDone/onError)` + 占位消息 + 光标 `▍`——派蒙的「逐句出字」可无缝替换/增强(把每个 chunk 当一句喂给 TTS)。
- 图片压缩 `compressImage()`(最长边≤1280、JPEG 0.8):五官链路复用,无需改。

**改造点(集成「购物助理会话切换」+ 语音表现)**
- **会话切换需带「类型」**:现在 `switchConversation()` 只按 `id` 切;需增加按 `bizType`(或新字段区分「派蒙购物助理/普通种草」)分区/分组展示或加标签。可在 `conv-item` 附近加「购物助理 / 种草助手」切换 tab,或按 `bizType` 给会话列表分组。
- **表现层挂语音/动效**:派蒙 LiveWeb 的 `Live2DCanvas.vue`(视线/口型/表情/[act])、`useAudioQueue.js`(逐句 TTS 预取→顺序播放)应作为**组件/组合式函数**挂进 `Chat.vue`(或成为共享 layout)。把「消息区底部/侧边」作为 `ChatPanel.vue`(派蒙侧)的挂载槽位。
- **「购物助理」= 一个人设变体,不是新页面**:建议在 `Chat.vue` 顶部/侧边加一个「人设/助理切换」控件,切换时只改 `systemPrompt`/会话 `bizType`,→ 复用同一面板。

### 2.2 `frontend/src/api/index.js`(chatApi)
**职责**:所有 AI 模块 HTTP 调用的前端封装;`sendStream` 手写 `fetch` + `ReadableStream` 解析 SSE(不走 axios,因流式)。

**可复用点**:5 个方法已与后端一一对应(`conversations`/`createConversation`/`messages`/`send`/`sendStream`)。**无需为集成新增普通 REST 调用**;派蒙侧如需要额外接口(如 TTS、情绪),另开 `paimonApi` 或并入 `chatApi` 即可,不影响现有方法。

**改造点(最小)**
- 若引入派蒙「语音/动效」协议(如 `/tts`、/emotion),建议**新增独立 API 对象**而非改现有 `chatApi` 语义,保持 `Chat.vue` 现有调用不变。
- `sendStream` 回调充足:`onChunk`(逐字)、`onDone`、`onError`——已有能力可直接当「TTS 逐句播放」的事件源。

### 2.3 `frontend/src/api/request.js`(axios 实例)
**职责**:统一 baseURL(`/api`)、请求附 Authorization token、响应解包 `R{code,msg,data}`、401 跳登录。

**可复用点**:完全复用。AI 模块所有非流式接口已走它,无需改。

**改造点**:无(除非要处理派蒙侧特殊响应,但目前 AI 接口全走 `R` 包装,统一)。

### 2.4 `frontend/src/router/index.js`
**职责**:路由 + 登录守卫。`/chat` 挂 `Chat.vue`,`beforeEach` 检查 token 无则跳 `/login`。

**可复用点**:`/chat` 路由与登录守卫直接复用。

**改造点**:无。若「购物助理」作为独立路由入口(如新增 `paimon` 页做纯派蒙 LiveWeb 面板),可加 `{ path: '/paimon', component: ... }`;但按最小侵入原则,**更推荐复用 `/chat` 单面板 + 人设切换**,不新增路由。

---

## 3. 后端接入点清单

### 3.1 `ChatRestController`
**职责**:AI 模块 HTTP 门户,5 个接口(会话管理 3 + 问答 2);Controller 零业务逻辑,全转 `ChatService`。

**可复用点(完全复用,不改)**
- `POST /api/v1/chat/conversations` → 建会话
- `GET /api/v1/chat/conversations` → 会话列表
- `GET /api/v1/chat/conversations/{id}/messages` → 历史回显
- `POST /api/v1/chat` → 非流式问答(备选)
- `POST /api/v1/chat/stream` → SSE 流式(带图/纯文字)
- `ChatTitleRequest` record(新建会话请求体)。

**改造点(最小,可选)**
- 如需「新会话时选人设/助理类型」,给 `ChatTitleRequest` 加一个可选类型字段(如 `bizType`),透传给 `ChatService.createConversation`;或复用现有 `bizType` 逻辑(后端默认 `CHAT`)。
- 若新增派蒙语音/动效直连接口(非必须),在此加独立端点;否则**本类无需改动**。

### 3.2 `ChatService`(接口)
**职责**:AI 模块「能力清单」——5 个方法(会管 3 + 问答 2)。依赖倒置:Controller 依赖接口。

**可复用点(完全复用)**:5 个方法签名保持;这是**本次集成的稳定契约**——只要不改这几个签名,集成就只发生在实现层/表现层。

**改造点(最小,可选)**
- 若要支持「按人设/助理类型取不同提示词、不同会话类型」,可在接口加一个**可选能力**方法(如 `createConversation(String title, String bizType)` 重载)或复用现有 `bizType` 传递。**优先不破坏现有 5 个方法**,新增走重载/新方法。

### 3.3 `ChatServiceImpl`(实现,业务心脏)
**职责**:校验 → 定会话 → 分流(视觉/文本)→ 调模型 → 落库 → 自动命名。三条铁律:模型无记忆(每次带全量历史)、模型异常转统一业务异常、AI 接口不豁免安全(会话归属校验)。

**可复用点(核心,本次集成的「人设替换点」)**
- `textSystemPrompt()` / `visionSystemPrompt()`:**人设提示词就写在这里**。这是把「AI 种草助手」换成/扩展成「派蒙购物助理」的**最直接改造点**——改动人设即可让 AI 以派蒙语气做购物导购,购物检索(工具)链路完全不动。
- `toAiHistory()` / `saveMessage()` / `autoRenameIfDefault()` / `resolveConversation()` / `ensureOwned()` / `validate()`:全部复用,语音/动效不改变它们。
- 分流逻辑 `hasImage()`→`visionChatClient` / 无图→`chatClient`:复用;语音/动效不影响「选哪个模型」。

**改造点(最小)**
- **人设/助理类型注入**:把当前写死的 `textSystemPrompt()` 升级为「按会话 `bizType`/助理类型取不同 systemPrompt」(如 `PaimonShoppingPrompt` vs `GenericShoppingPrompt`)。**只改提示词字符串,不动模型调用、工具、落库**——这是「购物功能不动」的最强保证。
- **会话类型**:`createConversation()`/`resolveConversation()` 里 `setBizType(Conversation.BIZ_CHAT)` 可扩展为新类型(如 `Conversation.BIZ_SHOPPING_ASSISTANT=`"SHOPPING"`)。表字段已支持(见 §4)。
- 若派蒙侧需要「脏话/情绪标签剥离」(如 `[emo:x]`/`[act:y]`)下发给前端,可在 `content()` 返回前做后处理,但**这是表现增强,不改变落库/历史逻辑**(历史里是否保留标签需产品定,建议保留无损、前端播放时再剥离)。

### 3.4 `AiConfig`
**职责**:Spring AI 装配车间——两个 `ChatClient` Bean(文本+视觉),均 `defaultTools(searchTool)`;Builder 由 starter 按 `application.yml` 的 `spring.ai.openai.*` 自动配好。

**可复用点(完全复用)**
- `chatClient`(文本链路,挂 `searchProduct` 工具)/ `visionChatClient`(视觉链路,也挂工具):**这两个 Bean 直接承载派蒙人设对话的模型调用**,无改动。
- `ProductSearchTool` 通过 `defaultTools` 注入——购物检索能力复用于此,无需改。

**改造点(可选)**
- 若「派蒙购物助理」与「通用种草」用**不同模型/温度/工具集**,可在此按助理类型注册更多 `ChatClient`(如 `paimonChatClient`),或仍复用现有 Bean + 按次 `.options()` 覆盖;**优先复用现有 Bean,避免新增装配复杂度**。
- 若调 DeepSeek 人设对话用官方 `deepseek-chat`(派蒙侧默认),而 ai-mall 用 `deepseek-v4-flash-vision-exp`,只需在 `application.yml` 改默认 model 或在 Service 层按次覆盖,不改装配结构。

### 3.5 `application.yml`(ai 配置段)
**职责**:数据源、`spring.ai.openai.*`(base-url/api-key/model/temperature)、Sa-Token、MyBatis、Jackson。

**可复用点**:整段结构复用;`spring.ai.openai.*` 已兼容 OpenAI 协议中转/官方。

**改造点(最小,配置级)**
- `spring.ai.openai.chat.options.model`/`temperature`:**人设对话的模型与温度在此配置**;派蒙侧如用 `deepseek-chat` 可在此调整(或按次覆盖)。
- 若要支持「多助理用不同模型/温度」,可各加一组配置(如 `spring.ai.openai.chat.options.*` + 自定义 `paimon.*` 配置),但**优先保持单组配置**。
- 无代码层面改动;仅改配置值。

---

## 4. 数据表接入点清单

### 4.1 `t_conversation`(AI 会话表)
```sql
id BIGINT PK
user_id BIGINT       -- 归属人,防越权核心
biz_type VARCHAR(20) DEFAULT 'CHAT'  -- 【扩展点】CHAT通用 / CHAT_GOODS商品问答 / SHOPPING导购
title VARCHAR(100)   -- 自动命名
created_at DATETIME
```
**职责**:一个用户与 AI 的一次连续对话 = 一个会话(类比微信聊天框)。

**可复用点**:结构、索引 `idx_user`、归属校验字段全部复用。

**改造点(最小)**
- **`biz_type` 是关键扩展位**:当前注释已写明 `CHAT_GOODS`(V2)/`SHOPPING`(导购)。**本次集成「购物助理会话切换」的本质 = 让会话带 `biz_type` 类型**,如新增 `SHOPPING`(派蒙购物助理)与默认 `CHAT`(通用种草)。前端据此区分会话类型并渲染不同入口/标签。
- 无需新表、无需改表结构即可支持多助理(单表多 `biz_type`)。

### 4.2 `t_message`(AI 会话消息表)
```sql
id BIGINT PK
conversation_id BIGINT
role VARCHAR(20)        -- user / assistant(刻意与 OpenAI 一致,转协议消息免翻译)
content TEXT            -- 消息正文
extra_json MEDIUMTEXT   -- 【扩展点】V1 存用户图 base64;V2 计划存商品卡片/引用来源/情绪等 JSON
created_at DATETIME
```
**职责**:一个会话里的每条气泡;只增不改(INSERT/SELECT)。

**可复用点**:顺序 `ORDER BY created_at ASC, id ASC`、角色取值、`extra_json` 扩展位全部复用。

**改造点(最小)**
- **`extra_json` 是「语音/动效/结构化元数据」的最佳落点**:目前仅存用户图片 base64。V2 可在 assistant 消息的 `extra_json` 里存「情绪标签(`emo`)/动作指令(`act`)/引用的商品卡片 JSON」。**不改表结构**,只扩展 JSON 语义;历史无损、兼容。
- **不要改 `content` 语义**:正文仍是纯文本;情绪/动作标签建议放 `extra_json` 而非拼进 `content`,避免污染「历史 → 模型上下文」的转换(`toAiHistory` 只取 `content`)。

---

## 5. R 返回与 Sa-Token 鉴权

### 5.1 `R<T>`(统一返回体)
```java
{ code, msg, data }   // code=200 成功,ok()/fail() 家族
```
**职责**:除 SSE `/stream`(`Flux<String>`)外,所有接口返回 `R<T>`。

**可复用点(完全复用)**:`R.ok()/R.fail()` 全栈用同一套;前端 `request.js` 已按 `code===200` 解包。**无需改动**。
- **注意**:`/stream` 是唯一不走 `R` 的接口(SSE 事件流)。前端语音/动效若依赖逐句 JSON(含情绪/动作),建议沿用 SSE 自定义数据帧格式(参考派蒙 `SseEvents`),而**非塞进 `R`**。

### 5.2 `ResultCode`(错误码)
**职责**:统一错误码;AI 域 `3xxx`,`AI_SERVICE_ERROR(3001)`。

**可复用点**:AI 异常统一 `3001`,前端有兜底文案。**无需改动**(除非要加「语音不可用」等新码,可扩展 `3xxx`)。

### 5.3 `BusinessException` / `GlobalExceptionHandler`
**职责**:业务异常转 `R`;全局兜底,堆栈只进日志不进响应。

**可复用点(完全复用)**:AI 模块所有校验失败(`validate`/`ensureOwned`/`resolveConversation`)都抛 `BusinessException`,由全局处理器统一转 `R`。语音/动效的失败(如 TTS 不可用)在此以统一错误码呈现即可。

### 5.4 `SaTokenConfig`(Sa-Token 鉴权)
```java
.addInterceptor(new SaInterceptor())
.addPathPatterns("/api/**")
.excludePathPatterns("/api/v1/auth/login","/api/v1/auth/register","/error")
```
**职责**:`/api/**` 默认需登录,白名单排除登录/注册/`/error`。

**可复用点(完全复用)**:AI 模块 5 个接口全部落在 `/api/**` 下,**自动受 Sa-Token 保护**,无需额外配置。

**改造点(最小)**:若新增派蒙语音/动效接口 **必须在 `/api/**` 下**(如 `/api/v1/paimon/tts`)才会自动要求登录;若公开则需加白名单。**建议所有新端点都走 `/api/**`**,保持与购物接口一致的鉴权。

### 5.5 `WebConfig`(CORS)
**职责**:允许 localhost/127.0.0.1 任意端口、`*.paimon.store`。

**可复用点**:AI 模块前端调用已覆盖;派蒙侧若同源部署(Vite proxy / 单端口)不新增 CORS。**无需改动**。若派蒙/购物不同域且走公网,把新域名加进 `allowedOriginPatterns` 即可。

---

## 6. 最小侵入边界结论(「购物功能完全不动」的明确界定)

**购物功能零改动**:`goods`(Product/ProductSku/ProductImage)、`cart`、`order`(Order/OrderItem)、`address`、`auth`(User)的所有 Controller/Service/Mapper/XML/表,以及 `R`/`ResultCode`/`BusinessException`/`GlobalExceptionHandler`/`SaTokenConfig`/`WebConfig`,**均不需要改动**。

**改动全部收敛在 AI 模块内**,且多数是「配置/提示词/扩展字段」级:

| 层面 | 改动 | 是否必须 |
|---|---|---|
| 前端 `Chat.vue` | 加「购物助理会话切换」*表现/入口*;挂派蒙 `Live2D+useAudioQueue` | 是(集成表现) |
| 前端 `api/index.js` | 可选:`paimonApi`(TTS/情绪),不动现有 `chatApi` | 可选 |
| 后端 `ChatServiceImpl` | *人设/助理类型注入*:按 `bizType` 取不同 `systemPrompt`(派蒙购物 vs 通用种草) | 是(人设切换) |
| 后端 `Conversation`/`ChatServiceImpl` | 新增 `biz_type` 取值(如 `SHOPPING`),`createConversation`/`resolveConversation` 支持传类型 | 是(会话切换) |
| 后端 `application.yml` | 人设对话模型/温度配置 | 可选 |
| `t_message.extra_json` | 存情绪/动作/商品卡片 JSON(扩展语义,不改表结构) | 可选 |
| `AiConfig` | 如多助理用不同模型/温度,可注册更多 `ChatClient`(优先复用现有) | 可选 |

**「购物功能完全不动」的强保证**:`ProductSearchTool` 是 ai-mall 侧唯一调用购物链路的点,它复用 `ProductService.pageOnSale`(与前端搜索同一数据路径)。**本次集成不改 `ProductSearchTool`、不改 `ProductService`**——即购物数据供给链路零污染。集成只在「人设提示词 + 会话类型 + 前端表现」层发生。

---

## 7. 相关后端文件索引(供集成架构设计引用)

| 文件 | 角色 | 集成定位 |
|---|---|---|
| `backend/.../ai/controller/ChatRestController.java` | HTTP 门户(5 接口) | 基本不动;可加会话类型透传 |
| `backend/.../ai/service/ChatService.java` | 能力接口 | 稳定契约,尽量不动 |
| `backend/.../ai/service/impl/ChatServiceImpl.java` | 业务心脏 | **人设/类型注入主点** |
| `backend/.../config/AiConfig.java` | Spring AI 装配 | 复用;可选加人设 Client |
| `backend/src/main/resources/application.yml` | AI 模型/温度配置 | 配置级调整 |
| `backend/.../ai/tool/ProductSearchTool.java` | 商品检索工具 | **完全不动**(购物链路唯一触点) |
| `backend/.../ai/bean/Conversation.java` | 会话实体 | 扩展 `bizType` 常量 |
| `backend/.../ai/dto/ChatRequest.java` / `ConversationVO.java` | 请求/出参 | 可加类型字段 |
| `backend/.../ai/mapper/ConversationMapper.{java,xml}` | t_conversation SQL | 复用/加 `selectByUserIdAndBizType`(可选) |
| `backend/.../ai/mapper/MessageMapper.{java,xml}` | t_message SQL | 复用 |
| `sql/init.sql` | 建表(含 `biz_type` 注释扩展) | 加 `SHOPPING` 取值说明 |
| `frontend/src/views/Chat.vue` / `api/index.js` / `api/request.js` / `router/index.js` | 面板/API/路由 | Chat.vue 表现层改造;api/router 基本复用 |
| `PaimonLiveWeb5/frontend/src/components/Live2DCanvas.vue` / `useAudioQueue.js` / `ChatPanel.vue` | 派蒙侧动效+语音+面板 | **待移植/挂载进 ai-mall 前端** |
| `PaimonLiveWeb5/backend/.../tts/*` + `SseEvents` | 派蒙 TTS + SSE 事件 | 待移植/复用为 ai-mall 语音服务 |

> 说明:PaimonLiveWeb5 侧(Live2D/TTS/SSE 事件协议)的详细迁移方案与 UI 设计交由 t1/t2/t4 负责,本清单仅从 ai-mall 侧给出「接入点在哪、购物如何零改动」的边界。
