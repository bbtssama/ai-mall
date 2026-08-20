# ai-mall 后端学习文档 · 共享素材包（给文档作者用的"事实来源"）

> 本文档是生成 12 篇学习文档的**唯一事实来源**。请严格基于此包撰写，不要臆造代码、字段、接口或配置。所有内容均从真实源码提取核对。

## 0. 项目一句话

AI 种草商城（ai-mall）V1：单体 Spring Boot 电商项目，闭环链路 = 注册登录 → 浏览商品 → 加购物车 → 下单（防超卖）→ AI 问答（SSE 流式）。用于初级 Java 求职实战。

## 1. 技术栈与版本

| 项 | 值 |
|---|---|
| 后端框架 | Spring Boot 3.4.5（parent），Java 17 |
| ORM | MyBatis（`mybatis-spring-boot-starter` 3.0.4），**XML 手写 SQL**，无 MyBatis-Plus/PageHelper |
| 认证 | Sa-Token 1.39.0（`sa-token-spring-boot3-starter`），无状态 Token |
| 密码 | spring-security-crypto 仅 BCrypt 模块（不引入完整 Spring Security） |
| AI | Spring AI 1.0（`spring-ai-starter-model-openai`），OpenAI 兼容模式，模型 `deepseek-v4-pro`（经 OpenCode Go 中转，可切官方 DeepSeek） |
| 数据库 | MySQL 8，库 `ai_mall`，JDBC URL `jdbc:mysql://192.168.6.102:3306/ai_mall?...`（开发机） |
| Web | spring-boot-starter-web + validation |
| 前端（仅背景） | Vue3 + Vite + Element Plus，`/api` 代理到 8080 |
| Lombok | 启用（@Data/@RequiredArgsConstructor/@Slf4j 等） |
| Maven 依赖 | 阿里云镜像加速仅本项目生效 |

## 2. 目录结构与分层约定（关键！文档必须体现）

```
backend/src/main/
├── java/com/aimall/
│   ├── AimallApplication.java      # @SpringBootApplication 启动类，无 @MapperScan
│   ├── common/                     # R 统一返回 / ResultCode / BusinessException / GlobalExceptionHandler / PageQuery / PageResult
│   ├── config/                     # AiConfig / PasswordConfig / SaTokenConfig / WebConfig
│   ├── user/                       # 注册/登录/登出/me        （bean/controller/dto/mapper/service/impl）
│   ├── goods/                      # 商品列表/详情/SKU / 购物车
│   ├── order/                      # 下单/列表/详情/取消
│   └── ai/                         # 会话管理 / 问答（SSE 流式）
└── resources/
    ├── application.yml
    └── mapper/*.xml                # 8 个 XML：User/Cart/Product/ProductSku/Order/OrderItem/Conversation/Message
```

分层约定（风格对齐早年项目 order-system-v2 / blog-system）：
- 实体包叫 `bean`（不叫 entity）；Controller 命名 `XxxRestController`；接口前缀统一 `/api/v1`
- Mapper 逐接口 `@Mapper` 注册（**不用 @MapperScan**）
- 每个业务域内部 = `bean / controller / dto / mapper / service(接口) / service/impl`
- Service 实现构造器注入（`@RequiredArgsConstructor` + `private final`）
- 手写 `LIMIT offset,size` 分页 + `PageResult{records,total,page,pageSize}`，不引分页插件

## 3. 统一返回与全局异常（common 包）—— 文档核心主题之一

**R.java**：`R<T>{ code:int, msg:String, data:T }`
- 静态工厂：`ok()` / `ok(T data)` / `ok(String msg, T data)` / `fail(String msg)`（默认 500）/ `fail(ResultCode)` / `fail(int code, String msg)`
- ⚠️ **故意去掉单参 `ok(String)` 重载**：早期项目 `R.ok(String)` 与泛型 `ok(T)` 并存，当 `R.ok(chatService.chat(req))` 传 String 时被解析到 `ok(String)` 导致 data 丢失。本项目只留 `ok(T)` 和 `ok(String msg, T data)`。

**ResultCode 枚举**（按域分段）：
- 通用：SUCCESS(200) / BAD_REQUEST(400) / UNAUTHORIZED(401) / FORBIDDEN(403) / NOT_FOUND(404) / SERVER_ERROR(500)
- 用户 1xxx：USERNAME_EXISTS(1001) / USER_NOT_FOUND(1002) / PASSWORD_ERROR(1003) / USER_DISABLED(1004)
- 电商 2xxx：PRODUCT_NOT_FOUND(2001) / SKU_NOT_FOUND(2002) / SKU_OFF_SHELF(2003) / STOCK_NOT_ENOUGH(2004) / CART_ITEM_NOT_FOUND(2005) / CART_EMPTY(2006) / ORDER_NOT_FOUND(2007) / ORDER_STATUS_INVALID(2008)
- AI 3xxx：AI_SERVICE_ERROR(3001)

**BusinessException**：带 code 的 RuntimeException；三种构造：`(String msg)` 默认 500 / `(ResultCode)` / `(ResultCode, String msg)` / `(int, String)`。

**GlobalExceptionHandler**（@RestControllerAdvice）覆盖：
- BusinessException → `R.fail(code,msg)`（log.warn）
- Sa-Token NotLoginException → 401「未登录或登录已过期」
- MethodArgumentNotValidException → 400 + 第一条字段错误 `field + message`
- MissingServletRequestParameterException / HttpMessageNotReadableException → 400
- NoResourceFoundException → 404
- Exception 兜底 → 500「服务器开小差了…」（log.error 记堆栈，不泄漏给前端）

**PageQuery**：`page`(min=1) / `pageSize`(1..100) / `getOffset()=(page-1)*pageSize`。`PageResult<T>.of(records,total,page,pageSize)`。

## 4. 配置类（config 包）

- **AiConfig**：`@Bean ChatClient chatClient(ChatClient.Builder builder)`（Spring AI）
- **PasswordConfig**：`@Bean PasswordEncoder` = `new BCryptPasswordEncoder()`
- **SaTokenConfig**：`WebMvcConfigurer`，注册 `SaInterceptor`，拦截 `/api/**`，白名单 `/api/v1/auth/login`、`/api/v1/auth/register`、`/error`
- **WebConfig**：CORS 允许 `http://localhost:*`、`http://127.0.0.1:*`，方法 GET/POST/PUT/DELETE/OPTIONS，allowCredentials(true)，maxAge 3600

## 5. application.yml 关键点

- 端口 8080；MySQL：`${MYSQL_USERNAME:root}` / `${MYSQL_PASSWORD:root123}`（本地默认）
- spring.ai.openai：`base-url: https://opencode.ai/zen/go`（**不带 /v1，Spring AI 自动拼 /v1/chat/completions**）；`api-key: ${DEEPSEEK_API_KEY:sk-...}`；`model: deepseek-v4-pro`；temperature 0.7
- Jackson：date-format `yyyy-MM-dd HH:mm:ss`，time-zone Asia/Shanghai
- mybatis：mapper-locations `classpath:mapper/**/*.xml`；map-underscore-to-camel-case: true；日志 StdOutImpl
- sa-token：token-name `Authorization`；timeout 604800（7 天）；active-timeout -1；is-concurrent true；is-share false；token-style uuid；is-log false

## 6. 各域代码事实（逐文件核对，写文档时按需引用）

### user 域
- **UserMapper**：`selectById` / `selectByUsername` / `insert`（useGeneratedKeys 回填 id）
- **AuthRestController** `/api/v1/auth`：`POST /register`（@Valid RegisterRequest）、`POST /login`、`POST /logout`、`GET /me`
- **RegisterRequest**：username `^[a-zA-Z0-9_]{3,20}$`；password 6-32 位；nickname 可选
- **LoginRequest**：username/password @NotBlank
- **UserServiceImpl.register**：查重（uk_username 兜底）→ BCrypt 编码 → 默认昵称「种草用户+4位随机」→ role=0 status=1 → insert
- **login**：查用户 → `passwordEncoder.matches` → **失败统一提示 PASSWORD_ERROR（不暴露用户名是否存在）** → status==0 则 USER_DISABLED → `StpUtil.login(user.getId())` → 返回 `LoginVO(token, userVO)`
- **logout**：`StpUtil.logout()`；**me**：`StpUtil.getLoginIdAsLong()` → selectById → toVO
- **UserVO**：id/username/nickname/avatar/role（不含 passwordHash）

### goods 域
- **ProductMapper**：`selectOnSalePage(offset,size)` 列表（LEFT JOIN SKU + `MIN(price) AS min_price` 起售价，GROUP BY 全列，WHERE status=1，LIMIT）、`countOnSale`、`selectById`（含 detail）、`selectAllOnSale`（AI 知识来源，含 detail+起售价）
- **ProductSkuMapper**：`selectById` / `selectByProductId` / `selectByIds(foreach)` / `deductStock` / `addStock`
- **CartMapper**：`selectItemsByUserId`（JOIN sku+product 一次查出展示字段，ORDER BY updated_at DESC）、`selectByUserIdAndSkuId`、`insert`、`updateQuantity(id,userId,quantity)`、`deleteById(id,userId)`、`deleteByUserId`、`deleteByUserIdAndSkuIds(userId,skus)`
- **ProductRestController** `/api/v1/products`：`GET /`（PageQuery 分页上架商品）、`GET /{id}`（SKU 列表）
- **ProductServiceImpl**：pageOnSale = count + 分页 list → toListVO（不含 detail）；detail = selectById → 校验存在且 status==1 → 带 skus
- **CartRestController** `/api/v1/cart`：GET 列表 / POST 加购 / PUT {id} 改数量 / DELETE {id} 删条目 / DELETE 清空
- **CartServiceImpl**：userId 取自 StpUtil；add 时校验 SKU 存在→商品上架→库存够（内存预检，真正扣减在下单）→ 同 SKU 已存在则数量相加（封顶 99）否则 insert；**update/delete 都带 userId 条件，返回 0 行=非本人条目→CART_ITEM_NOT_FOUND（归属校验下沉 SQL）**
- **AddCartRequest**：skuId @NotNull；quantity 1..99 默认 1。**UpdateCartRequest**：quantity 1..99
- **CartItemVO**：id/skuId/skuName/price/quantity/productId/productName/mainImg + `getSubtotal()=price×quantity`

### order 域 —— 防超卖是全文核心
- **Order 状态机常量**：PENDING_PAY / PAID / SHIPPED / COMPLETED / CANCELLED
- **OrderMapper**：`selectById` / `selectByOrderNo` / `selectByUserIdPage`（(user_id,created_at) 联合索引，ORDER BY created_at DESC,id DESC）、`countByUserId` / `insert`（回填 id）/ `updateStatus(id, fromStatus, toStatus, cancelTime)`（**状态机 CAS，<if toStatus==CANCELLED> 回填 cancel_time，version+1**）
- **OrderItemMapper**：`selectByOrderId` / `batchInsert(foreach)`
- **OrderRestController** `/api/v1/orders`：POST 创建 / GET 分页我的订单 / GET {id} 详情 / POST {id}/cancel
- **CreateOrderRequest**：items @NotEmpty @Valid（List<OrderItemRequest>）、receiverName/Phone/Address @NotBlank；**OrderItemRequest**：skuId @NotNull、quantity 1..99
- **OrderServiceImpl.create**（`@Transactional(rollbackFor=Exception.class)`）：
  1. 取 userId，items 空则 CART_EMPTY
  2. 逐项：`skuMapper.selectById` → 校验商品上架 → 内存预检库存 → **`skuMapper.deductStock(id,qty)` DB 层乐观扣减（`UPDATE t_product_sku SET stock=stock-?, sales=sales+? WHERE id=? AND stock>=?`），返回 0=并发抢光→抛 STOCK_NOT_ENOUGH** → 组 OrderItem（快照 productName/skuName/price）→ 累加 total（**Decimal 累加，严禁 double**）
  3. 组 Order：orderNo（`yyyyMMddHHmmssSSS` 时间戳 + 4 位随机 + userId%1000，`uk_order_no` 兜底）、status=PENDING_PAY、收货信息 → insert
  4. 明细 batchInsert（orderId 回填）
  5. `cartMapper.deleteByUserIdAndSkuIds` 清购物车中本次下单 SKU（直接购买场景本就是空操作，幂等）
- **cancel**（@Transactional）：selectById → 归属校验（!userId.equals||null → ORDER_NOT_FOUND）→ **`updateStatus(id, PENDING_PAY, CANCELLED, now)`，返回 0=状态不符（防并发重复取消）→ ORDER_STATUS_INVALID** → 逐条 `addStock` 回补库存（selectByOrderId 遍历）
- **detail/pageMyOrders**：带 userId 校验归属；OrderVO 由 BeanUtils.copyProperties(order, vo)，items 由 orderItemMapper 查

### ai 域 —— SSE 流式
- **Conversation**：BIZ_CHAT 常量；**Message**：ROLE_USER / ROLE_ASSISTANT
- **ConversationMapper**：`insert` / `selectById` / `selectByUserId`（新→旧）
- **MessageMapper**：`insert` / `selectByConversationId`（旧→新）
- **ChatRestController** `/api/v1/chat`：`POST /conversations`（可空 body：record ChatTitleRequest）、`GET /conversations`、`GET /conversations/{id}/messages`、`POST /`（普通问答，返回 R<String>）、`POST /stream`（**produces = TEXT_EVENT_STREAM_VALUE，返回 Flux<String>**）
- **ChatRequest**：message @NotBlank、conversationId 可空（空则自动建会话，标题取问题前 20 字）
- **ChatServiceImpl.chat（普通）**：resolveConversation（归属校验/自动新建）→ `chatClient.prompt().system(buildSystemPrompt()).messages(toAiHistory).user(msg).call().content()` → 异常捕获转 AI_SERVICE_ERROR → 双写 saveMessage(user+assistant)
- **ChatServiceImpl.stream（SSE）**：`chatClient.prompt()...stream().content()` 返回 Flux<String>：
  - `doOnSubscribe` 先持久化用户问题（**防流失败留孤儿提问**）
  - `doOnNext(sb::append)` 累加
  - `doOnComplete` 持久化完整 assistant 回答
  - `onErrorResume` 返回 `"\n\n[AI 服务暂时不可用，请稍后再试]"`（**防 SSE 裸断**）
- **buildSystemPrompt**（V1 预置知识，V2 换 RAG）：`productMapper.selectAllOnSale()` → 裁剪字段（id/name/subTitle/minPrice/detail）→ ObjectMapper 序列化为 JSON → system prompt 强调「基于商品库如实回答、没有就坦诚说不知道、不编造」
- **toAiHistory**：DB 历史消息转 Spring AI 的 UserMessage/AssistantMessage（避免与本次提问重复）
- 归属校验 `ensureOwned`：会话不存在或非本人 → NOT_FOUND「会话不存在」

## 7. 数据库（sql/init.sql）8 张表事实

| 表 | 关键字段/索引 |
|---|---|
| t_user | uk_username 唯一；password_hash VARCHAR(100)（BCrypt）；role/status TINYINT；created_at 默认 CURRENT_TIMESTAMP |
| t_product | spu_name/sub_title/category_id/main_img/detail TEXT/status；idx_status |
| t_product_sku | product_id/sku_name/price DECIMAL(10,2)/stock/sales/**version（乐观锁预留）**；idx_product |
| t_cart | user_id/sku_id/quantity；**uk_user_sku(user_id,sku_id)**；updated_at ON UPDATE |
| t_order | order_no VARCHAR(32)/total_amount/status/pay_type/pay_time/cancel_time/**version**；uk_order_no；**idx_user_created(user_id,created_at)** |
| t_order_item | order_id/sku_id/**product_name/sku_name/price 快照**/quantity；idx_order |
| t_conversation | user_id/biz_type/title；idx_user |
| t_message | conversation_id/role/content/extra_json(TEXT，V2 引用来源预留)；idx_conversation |

种子：4 商品（AirSound Pro 耳机 ¥399 / 闪充宝 65W GaN ¥89-129 / 云朵唇釉 ¥69 / 轻氧手环 ¥199-249），8 个 SKU，detail 为种草图文（AI 知识来源）。

## 8. 事务/并发/一致性要点（面试级素材）

1. **防超卖**：不是先查再减，而是 `UPDATE ... SET stock=stock-? WHERE id=? AND stock>=?` —— 数据库行锁 + 条件判断原子完成；返回 0 行 = 并发下库存不足。代码里"内存预检库存"只是提前给友好报错，真正的防线在这条 SQL。
2. **@Transactional 整体性**：扣库存→写订单→写明细→清购物车全在一个事务；任一抛异常全部回滚（含已扣的库存）。rollbackFor=Exception.class（默认只回滚 RuntimeException）。
3. **状态机 CAS 取消**：`UPDATE t_order SET status=? WHERE id=? AND status='PENDING_PAY'` 防并发重复取消；成功才回补库存。version 字段随更新 +1 是乐观锁痕迹（V3 秒杀强化）。
4. **订单号**：时间戳 17 位+随机 4 位+用户尾号，业务唯一由 uk_order_no 兜底（极端并发下唯一索引抛异常）；V2 可换雪花 ID。
5. **归属校验下沉 SQL**：购物车 update/delete、订单归属均在 SQL/查询后用 userId 双重校验，杜绝越权。
6. **金额类型**：全程 BigDecimal（DECIMAL(10,2)），禁 double。
7. **Sa-Token 无状态**：登录后 StpUtil 签发 token，前端每次带 Authorization 头，拦截器统一校验；401 由全局异常转 R。
8. **SSE 时序**：订阅时先存用户消息（doOnSubscribe），流结束存 assistant（doOnComplete），错误时兜底文案而非裸断——保证"消息对"不丢。

## 9. 已知问题/演进（文档可作"延伸思考"素材，勿当缺陷藏匿）

- application.yml 含中转 Key（README/.gitignore 都提示公开前改环境变量）
- 无单元测试（仅 scripts/smoke-test.ps1 黑盒冒烟 9/9 通过）
- V2：RAG 导购 + 支付宝/微信沙箱支付 + 内容笔记；V3：Redis/MQ/秒杀；V4：微服务；V5：Agent/推荐/NL2SQL

## 10. 运行方式（文档若提到即可用）

```
mysql -h192.168.6.102 -uroot -proot123 < sql/init.sql
cd backend && mvn spring-boot:run   # 8080
cd frontend && npm install && npm run dev  # 5173
```
冒烟：`pwsh -File scripts/smoke-test.ps1`（9 项：注册/登录/商品列表/详情/加购/购物车/下单/订单/AI 问答）