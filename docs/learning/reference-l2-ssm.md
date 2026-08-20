# Reference 参考手册 · L2 SSM 初级版

> **面向"正在读 ai-mall 源码的人"**：这本手册是源码的**索引地图**。
> 你要在一堆类里快速定位某个 Controller / Mapper / DTO / Bean，看某条 SQL 到底做了什么、
> 某个字段有哪些、某个依赖什么版本、某个报错为什么发生怎么修——翻到对应表格即可。
> 只精确索引，不展开讲原理。
>
> 读者定位：懂 SSM（Spring + Spring MVC + MyBatis）的初级开发者。
> 所有文件清单、SQL、字段、版本均从真实源码（`backend/src/main`）逐文件核对得出，
> 行数为统计时点的真实值，供"按行号快速定位"参考。

---

## 目录

1. [源码全文件清单与行号速查](#1-源码全文件清单与行号速查)
2. [分层约定速查（读代码前先记）](#2-分层约定速查读代码前先记)
3. [启动类与配置类速查](#3-启动类与配置类速查)
4. [8 个 XML Mapper SQL 速查](#4-8-个-xml-mapper-sql-速查)
5. [全部 Bean / DTO / VO 字段清单](#5-全部-bean--dto--vo-字段清单)
6. [Controller 速查（含返回类型精确定位）](#6-controller-速查含返回类型精确定位)
7. [依赖与版本表](#7-依赖与版本表)
8. [application.yml 配置速查](#8-applicationyml-配置速查)
9. [常见报错对照表](#9-常见报错对照表)
10. [面试 SQL 关键点定位](#10-面试-sql-关键点定位)

---

## 1. 源码全文件清单与行号速查

> 基础目录：`backend/src/main/java/com/aimall/`。路径后的数字 = 文件当前行数（定位用，非权威 ABI）。
> 资源目录：`backend/src/main/resources/`（application.yml + `mapper/*.xml`）。

### 1.1 顶层与 common 包（统一返回 / 分页 / 异常）

| 文件（相对 `com/aimall/`） | 行 | 一句话职责 |
|---|---|---|
| `AimallApplication.java` | 17 | Spring Boot 主启动类，`@SpringBootApplication`，**无 @MapperScan** |
| `common/api/R.java` | 50 | 泛型统一返回体 `R<T>{code,msg,data}` + `ok()/fail(...)` 静态工厂 |
| `common/api/ResultCode.java` | 44 | 错误码枚举（通用 / 用户 1xxx / 电商 2xxx / AI 3xxx） |
| `common/exception/BusinessException.java` | 34 | 带 `code` 的业务异常，4 种构造 |
| `common/exception/GlobalExceptionHandler.java` | 60 | `@RestControllerAdvice` 全局异常→R 统一转换（含 Sa-Token 401 / 校验 400 / 兜底 500） |
| `common/page/PageQuery.java` | 24 | 分页查询参数：`page`(1..)、`pageSize`(1..100)、`getOffset()=(page-1)*pageSize` |
| `common/page/PageResult.java` | 31 | 分页返回：`PageResult<T>{records,total,page,pageSize}`，工厂 `of(...)` |

### 1.2 config 包（4 个配置类）

| 文件 | 行 | 一句话职责 |
|---|---|---|
| `config/AiConfig.java` | 18 | `@Bean ChatClient`（Spring AI） |
| `config/PasswordConfig.java` | 18 | `@Bean PasswordEncoder = new BCryptPasswordEncoder()` |
| `config/SaTokenConfig.java` | 24 | `WebMvcConfigurer`，`SaInterceptor` 拦 `/api/**`，白名单 login/register//error |
| `config/WebConfig.java` | 21 | CORS 允许 localhost/127.0.0.1 任意端口 |

### 1.3 user 域（注册 / 登录 / 登出 / me）

| 文件 | 行 | 一句话职责 |
|---|---|---|
| `user/bean/User.java` | 33 | 用户实体（t_user），含 passwordHash（BCrypt） |
| `user/controller/AuthRestController.java` | 47 | `/api/v1/auth`：register / login / logout / me |
| `user/dto/LoginRequest.java` | 17 | 登录入参（username/password @NotBlank） |
| `user/dto/LoginVO.java` | 17 | 登录出参（token + UserVO） |
| `user/dto/RegisterRequest.java` | 23 | 注册入参（username pattern / password 6-32 / nickname） |
| `user/dto/UserVO.java` | 16 | 用户视图（**无 passwordHash**） |
| `user/mapper/UserMapper.java` | 19 | 用户 Mapper 接口（selectById/selectByUsername/insert） |
| `user/service/UserService.java` | 20 | 用户服务接口 |
| `user/service/impl/UserServiceImpl.java` | 82 | register / login / logout / me 实现 |

### 1.4 goods 域（商品 + 购物车）

| 文件 | 行 | 一句话职责 |
|---|---|---|
| `goods/bean/Product.java` | 37 | 商品实体（t_product），含聚合字段 `minPrice` |
| `goods/bean/ProductSku.java` | 24 | SKU 实体（t_product_sku），含 `version` 乐观锁 |
| `goods/bean/Cart.java` | 18 | 购物车条目实体（t_cart） |
| `goods/controller/CartRestController.java` | 57 | `/api/v1/cart`：list / add / update / remove / clear |
| `goods/controller/ProductRestController.java` | 33 | `/api/v1/products`：page（分页）/ detail（含 SKU） |
| `goods/dto/AddCartRequest.java` | 19 | 加购入参（skuId 必填，quantity 默认 1，范围 1..99） |
| `goods/dto/UpdateCartRequest.java` | 17 | 改数量入参（quantity 1..99） |
| `goods/dto/CartItemVO.java` | 26 | 购物车条目视图（含计算字段 `getSubtotal()`） |
| `goods/dto/ProductVO.java` | 26 | 商品视图（列表含 minPrice / 详情含 skus） |
| `goods/dto/SkuVO.java` | 19 | SKU 视图 |
| `goods/mapper/CartMapper.java` | 33 | 购物车 Mapper 接口（7 个方法） |
| `goods/mapper/ProductMapper.java` | 26 | 商品 Mapper 接口（分页/计数/详情/全量上架） |
| `goods/mapper/ProductSkuMapper.java` | 27 | SKU Mapper 接口（查/扣减/回补） |
| `goods/service/CartService.java` | 22 | 购物车服务接口 |
| `goods/service/impl/CartServiceImpl.java` | 88 | 加购（内存预检 + 同 SKU 累加）/ 改删（归属校验下沉 SQL）实现 |
| `goods/service/ProductService.java` | 17 | 商品服务接口 |
| `goods/service/impl/ProductServiceImpl.java` | 71 | 分页列表 / 详情实现 |

### 1.5 order 域（防超卖核心）

| 文件 | 行 | 一句话职责 |
|---|---|---|
| `order/bean/Order.java` | 46 | 订单实体（t_order），顶部 5 个 `STATUS_*` 状态常量 |
| `order/bean/OrderItem.java` | 20 | 订单明细实体（t_order_item，商品名/规格/单价快照） |
| `order/controller/OrderRestController.java` | 48 | `/api/v1/orders`：create / page / detail / cancel |
| `order/dto/CreateOrderRequest.java` | 28 | 下单入参（items 非空 + 收货三字段必填） |
| `order/dto/OrderItemRequest.java` | 20 | 下单条目入参（skuId + quantity 1..99） |
| `order/dto/OrderVO.java` | 29 | 订单视图（含 items 明细列表） |
| `order/mapper/OrderMapper.java` | 35 | 订单 Mapper 接口（含状态机 CAS updateStatus） |
| `order/mapper/OrderItemMapper.java` | 19 | 明细 Mapper 接口（selectByOrderId / batchInsert） |
| `order/service/OrderService.java` | 23 | 订单服务接口 |
| `order/service/impl/OrderServiceImpl.java` | 162 | **下单事务**（扣库存→写订单→写明细→清购物车）+ 取消回补实现 |

### 1.6 ai 域（会话 + SSE）

| 文件 | 行 | 一句话职责 |
|---|---|---|
| `ai/bean/Conversation.java` | 23 | 会话实体（t_conversation），常量 `BIZ_CHAT="CHAT"` |
| `ai/bean/Message.java` | 27 | 消息实体（t_message），常量 ROLE_USER/ROLE_ASSISTANT |
| `ai/controller/ChatRestController.java` | 62 | `/api/v1/chat`：conversations CRUD + chat + **stream（SSE）**；内嵌 `ChatTitleRequest` record |
| `ai/dto/ChatRequest.java` | 17 | 问答入参（message 必填，conversationId 可空） |
| `ai/dto/ConversationVO.java` | 17 | 会话视图 |
| `ai/dto/MessageVO.java` | 17 | 消息视图 |
| `ai/mapper/ConversationMapper.java` | 22 | 会话 Mapper 接口 |
| `ai/mapper/MessageMapper.java` | 20 | 消息 Mapper 接口 |
| `ai/service/ChatService.java` | 27 | 会话 + 问答服务接口 |
| `ai/service/impl/ChatServiceImpl.java` | 218 | **体积最大**：普通问答 / SSE 流 / buildSystemPrompt（商品库 JSON）/ toAiHistory |

### 1.7 resources 资源文件

| 文件 | 一句话职责 |
|---|---|
| `resources/application.yml` | 主配置（端口 / 数据源 / Spring AI / Jackson / MyBatis / Sa-Token） |
| `resources/mapper/UserMapper.xml` | 用户 3 条 SQL |
| `resources/mapper/ProductMapper.xml` | 商品 4 条 SQL（含起售价聚合） |
| `resources/mapper/ProductSkuMapper.xml` | SKU 5 条 SQL（含防超卖扣减） |
| `resources/mapper/CartMapper.xml` | 购物车 7 条 SQL |
| `resources/mapper/OrderMapper.xml` | 订单 6 条 SQL（含状态机 CAS） |
| `resources/mapper/OrderItemMapper.xml` | 明细 2 条 SQL（含批量插入） |
| `resources/mapper/ConversationMapper.xml` | 会话 3 条 SQL |
| `resources/mapper/MessageMapper.xml` | 消息 2 条 SQL |

> 读源码建议顺序：**Controller → Service → Mapper 接口 → Mapper.xml**，
> 一条请求链路 = `Controller(收参) → Service(业务规则) → Mapper(手写 SQL)`。

---

## 2. 分层约定速查（读代码前先记）

| 约定 | 规则（源码可验证） |
|---|---|
| 实体包名 | `bean`（不叫 entity） |
| Controller 命名 | `XxxRestController` |
| 接口前缀 | 统一 `/api/v1` |
| Mapper 注册 | 逐接口 `@Mapper`，**不用 @MapperScan**（见 AimallApplication 注释） |
| 业务域内部结构 | `bean / controller / dto / mapper / service(接口) / service/impl` |
| 依赖注入 | Service 实现用构造器注入（`@RequiredArgsConstructor` + `private final`） |
| 分页 | 手写 `LIMIT offset,size` + `PageResult`，不引分页插件 |
| SQL | XML 手写。`map-underscore-to-camel-case` 开启，但关键 SQL 常用别名显式映射 |
| 校验 | Controller 入参 `@Valid` + jakarta.validation 注解，失败进 GlobalExceptionHandler 返 400 |
| 返回 | 全部 `R<T>`（除 SSE 的 `Flux<String>`；cancel/update 类用 `R<Void>`） |

---

## 3. 启动类与配置类速查

| 文件 | 关键内容（源码逐条核对） |
|---|---|
| `AimallApplication` | `@SpringBootApplication`；main 直接 `SpringApplication.run`；**无 @MapperScan** → 各 Mapper 自带 `@Mapper` |
| `AiConfig` | `@Bean ChatClient chatClient(ChatClient.Builder builder)` → `builder.build()` |
| `PasswordConfig` | `@Bean PasswordEncoder` 返回 `new BCryptPasswordEncoder()` |
| `SaTokenConfig` | 拦截 `/api/**`，排除：`/api/v1/auth/login`、`/api/v1/auth/register`、`/error` |
| `WebConfig` | CORS `/**`；来源 `http://localhost:*`、`http://127.0.0.1:*`；方法 GET/POST/PUT/DELETE/OPTIONS；`allowCredentials(true)`；`maxAge(3600)` |

**关键结论（读拦截器时记住）**：除注册、登录两个白名单接口外，**一切 `/api/**` 都必须登录**，
未带 `Authorization` 头 → Sa-Token 抛 `NotLoginException` → 全局转 401"未登录或登录已过期"。

---

## 4. 8 个 XML Mapper SQL 速查

> 方法名 = Mapper 接口方法名 = `<select/insert/update/delete id=...>`。`resultType` 均为全限定名。

### 4.1 UserMapper.xml（namespace `user.mapper.UserMapper`）

| 方法 | SQL 做了什么 | 关键点 |
|---|---|---|
| `baseCols`(sql 片段) | id, username, password_hash, nickname, avatar, role, status, created_at | `<sql>` 复用 |
| `selectById` | `SELECT baseCols FROM t_user WHERE id=#{id}` | — |
| `selectByUsername` | `SELECT baseCols FROM t_user WHERE username=#{username}` | 登录/注册查重用 |
| `insert` | `INSERT INTO t_user(username,password_hash,nickname,avatar,role,status)` | `useGeneratedKeys="true" keyProperty="id"` 回填主键 |

### 4.2 ProductMapper.xml（namespace `goods.mapper.ProductMapper`）

| 方法 | SQL 做了什么 | 关键点 |
|---|---|---|
| `selectOnSalePage` | `LEFT JOIN t_product_sku`，`MIN(s.price) AS min_price`，`WHERE p.status=1` + `GROUP BY 全列`，`ORDER BY p.id DESC`，`LIMIT offset,size` | 列表"起售价"；LEFT JOIN 保证无 SKU 商品不丢行；GROUP BY 需列出全部非聚合列 |
| `countOnSale` | `SELECT COUNT(*) FROM t_product WHERE status=1` | 与列表配套做分页 total |
| `selectById` | `SELECT ... WHERE id=#{id}` | 含 detail（详情接口用） |
| `selectAllOnSale` | 同列表但含 `p.detail`、无 LIMIT，`WHERE status=1` | **AI 预置知识**：全量上架商品给 System Prompt |

### 4.3 ProductSkuMapper.xml（namespace `goods.mapper.ProductSkuMapper`）

| 方法 | SQL 做了什么 | 关键点 |
|---|---|---|
| `baseCols` | id, product_id, sku_name, price, stock, sales, version, created_at | — |
| `selectById` | `WHERE id=#{id}` | — |
| `selectByProductId` | `WHERE product_id=#{productId}` | 商品详情带 SKU 用 |
| `selectByIds` | `WHERE id IN (foreach ids)` | 批量取 SKU |
| `deductStock` | `UPDATE ... SET stock=stock-#{quantity}, sales=sales+#{quantity} WHERE id=#{id} AND stock>=#{quantity}` | ⚠️ **防超卖核心**：行锁 + 条件原子完成；返回 0 行=库存不足 |
| `addStock` | `UPDATE ... SET stock=stock+#{quantity} WHERE id=#{id}` | 取消订单回补库存 |

### 4.4 CartMapper.xml（namespace `goods.mapper.CartMapper`）

| 方法 | SQL 做了什么 | 关键点 |
|---|---|---|
| `selectItemsByUserId` | `t_cart JOIN t_product_sku JOIN t_product`，一次查齐展示字段，`ORDER BY c.updated_at DESC` | resultType 直接是 `dto.CartItemVO` |
| `selectByUserIdAndSkuId` | `WHERE user_id=#{userId} AND sku_id=#{skuId}` | 加购查"是否已存在同 SKU" |
| `insert` | `INSERT INTO t_cart(user_id,sku_id,quantity)` | `useGeneratedKeys` 回填 id |
| `updateQuantity` | `UPDATE ... SET quantity WHERE id=#{id} AND user_id=#{userId}` | ⚠️ **归属校验下沉 SQL**：非本人更新 0 行 |
| `deleteById` | `DELETE ... WHERE id=#{id} AND user_id=#{userId}` | 同上，归属校验在 WHERE |
| `deleteByUserId` | `DELETE ... WHERE user_id=#{userId}` | 清空当前用户购物车 |
| `deleteByUserIdAndSkuIds` | `DELETE ... WHERE user_id=#{userId} AND sku_id IN (foreach)` | ⚠️ 下单成功后清本次 SKU（幂等，直接购买时为空操作） |

### 4.5 OrderMapper.xml（namespace `order.mapper.OrderMapper`）

| 方法 | SQL 做了什么 | 关键点 |
|---|---|---|
| `baseCols` | 13 列（id→created_at） | 含 version |
| `selectById` | `WHERE id=#{id}` | — |
| `selectByOrderNo` | `WHERE order_no=#{orderNo}` | 订单号查询 |
| `selectByUserIdPage` | `WHERE user_id=#{userId} ORDER BY created_at DESC,id DESC LIMIT offset,size` | 走 `(user_id,created_at)` 联合索引 |
| `countByUserId` | `COUNT(*) WHERE user_id=#{userId}` | 分页 total |
| `insert` | 插 order_no/user_id/total_amount/status/收货三字段 | `useGeneratedKeys` 回填 id |
| `updateStatus` | `SET status=#{toStatus}, <if toStatus=='CANCELLED'>cancel_time=#{cancelTime},</if> version=version+1 WHERE id AND status=#{fromStatus}` | ⚠️ **状态机 CAS**：防并发重复取消；取消才回填 cancel_time |

### 4.6 OrderItemMapper.xml（namespace `order.mapper.OrderItemMapper`）

| 方法 | SQL 做了什么 | 关键点 |
|---|---|---|
| `selectByOrderId` | `WHERE order_id=#{orderId}` | 订单详情带明细 |
| `batchInsert` | `INSERT ... VALUES foreach(items) (orderId,skuId,productName,skuName,price,quantity)` | ⚠️ `foreach` 批量插入（快照字段） |

### 4.7 ConversationMapper.xml（namespace `ai.mapper.ConversationMapper`）

| 方法 | SQL 做了什么 | 关键点 |
|---|---|---|
| `insert` | 插 user_id,biz_type,title | `useGeneratedKeys` 回填 id |
| `selectById` | `WHERE id=#{id}` | 归属校验用 |
| `selectByUserId` | `WHERE user_id=#{userId} ORDER BY created_at DESC,id DESC` | 会话列表新→旧 |

### 4.8 MessageMapper.xml（namespace `ai.mapper.MessageMapper`）

| 方法 | SQL 做了什么 | 关键点 |
|---|---|---|
| `insert` | 插 conversation_id,role,content,extra_json | `useGeneratedKeys` |
| `selectByConversationId` | `WHERE conversation_id=#{conversationId} ORDER BY created_at ASC,id ASC` | 消息旧→新（喂给 AI 的历史顺序） |

---

## 5. 全部 Bean / DTO / VO 字段清单

### 5.1 Bean 实体（8 个，全 `@Data`）

**User（t_user）**：`id`、`username`、`passwordHash`、`nickname`、`avatar`、`role`、`status`、`createdAt`

**Product（t_product）**：`id`、`spuName`、`subTitle`、`categoryId`、`mainImg`、`detail`、`status`、`createdAt`、`minPrice`（⚠️ 非表列，SQL 聚合得出）

**ProductSku（t_product_sku）**：`id`、`productId`、`skuName`、`price`、`stock`、`sales`、`version`、`createdAt`

**Cart（t_cart）**：`id`、`userId`、`skuId`、`quantity`、`createdAt`、`updatedAt`

**Order（t_order）**：`id`、`orderNo`、`userId`、`totalAmount`、`status`、`receiverName`、`receiverPhone`、`receiverAddress`、`payType`、`payTime`、`cancelTime`、`version`、`createdAt`
（⚠️ 顶部常量：`STATUS_PENDING_PAY/PAID/SHIPPED/COMPLETED/CANCELLED`）

**OrderItem（t_order_item）**：`id`、`orderId`、`skuId`、`productName`、`skuName`、`price`、`quantity`（无 created_at，纯快照）

**Conversation（t_conversation）**：`id`、`userId`、`bizType`、`title`、`createdAt`
（⚠️ 常量 `BIZ_CHAT="CHAT"`）

**Message（t_message）**：`id`、`conversationId`、`role`、`content`、`extraJson`、`createdAt`
（⚠️ 常量 `ROLE_USER="user"` / `ROLE_ASSISTANT="assistant"`）

### 5.2 Request（入参 DTO，全 `@Data` + jakarta validation）

| 类 | 字段（校验） |
|---|---|
| `RegisterRequest` | username（@NotBlank + `@Pattern("^[a-zA-Z0-9_]{3,20}$")`）；password（@NotBlank + @Size 6-32）；nickname（无校验，可空） |
| `LoginRequest` | username（@NotBlank）；password（@NotBlank） |
| `AddCartRequest` | skuId（@NotNull）；quantity（@Min 1、@Max 99，**默认 1**） |
| `UpdateCartRequest` | quantity（@NotNull、@Min 1、@Max 99） |
| `CreateOrderRequest` | items（@NotEmpty + 元素 @Valid）；receiverName / receiverPhone / receiverAddress（均 @NotBlank） |
| `OrderItemRequest` | skuId（@NotNull）；quantity（@Min 1、@Max 99，**无默认**） |
| `ChatRequest` | message（@NotBlank）；conversationId（可空 Long） |
| `ChatTitleRequest`（record，内嵌在 ChatRestController） | title（可空） |

### 5.3 VO / 出参（全 `@Data`）

| 类 | 字段 | 备注 |
|---|---|---|
| `UserVO` | id、username、nickname、avatar、role | 明确**不含 passwordHash** |
| `LoginVO` | token、user（UserVO） | `@AllArgsConstructor` |
| `ProductVO` | id、spuName、subTitle、categoryId、mainImg、detail、status、minPrice、skus(List\<SkuVO\>) | 列表只填前几项（无 detail）；详情才带 skus |
| `SkuVO` | id、productId、skuName、price、stock、sales | — |
| `CartItemVO` | id、skuId、skuName、price、quantity、productId、productName、mainImg | 计算属性 `getSubtotal()` = price×quantity |
| `OrderVO` | id、orderNo、totalAmount、status、receiverName、receiverPhone、receiverAddress、payType、payTime、cancelTime、createdAt、items(List\<OrderItem\>) | items 详情接口才填充；主字段由 BeanUtils.copyProperties 拷贝 |
| `ConversationVO` | id、bizType、title、createdAt | — |
| `MessageVO` | id、role、content、createdAt | 不含 extraJson（V2 才暴露） |

---

## 6. Controller 速查（含返回类型精确定位）

> 返回类型 = `R<泛型>` 的泛型实际值；`R<Void>` 表示只关心成功与否。

### 6.1 AuthRestController `/api/v1/auth`

| 方法 | 路径/HTTP | 返回 R\<...\> |
|---|---|---|
| register | `POST /api/v1/auth/register` | `R<UserVO>` |
| login | `POST /api/v1/auth/login` | `R<LoginVO>` |
| logout | `POST /api/v1/auth/logout` | `R<Void>` |
| me | `GET /api/v1/auth/me` | `R<UserVO>` |

### 6.2 ProductRestController `/api/v1/products`

| 方法 | 路径/HTTP | 返回 R\<...\> |
|---|---|---|
| page | `GET /api/v1/products`（PageQuery 绑定 query 参数） | `R<PageResult<ProductVO>>` |
| detail | `GET /api/v1/products/{id}` | `R<ProductVO>` |

### 6.3 CartRestController `/api/v1/cart`

| 方法 | 路径/HTTP | 返回 R\<...\> |
|---|---|---|
| list | `GET /api/v1/cart` | `R<List<CartItemVO>>` |
| add | `POST /api/v1/cart` | `R<CartItemVO>` |
| update | `PUT /api/v1/cart/{id}` | `R<Void>` |
| remove | `DELETE /api/v1/cart/{id}` | `R<Void>` |
| clear | `DELETE /api/v1/cart` | `R<Void>` |

### 6.4 OrderRestController `/api/v1/orders`

| 方法 | 路径/HTTP | 返回 R\<...\> |
|---|---|---|
| create | `POST /api/v1/orders` | `R<OrderVO>` |
| page | `GET /api/v1/orders`（PageQuery） | `R<PageResult<OrderVO>>` |
| detail | `GET /api/v1/orders/{id}` | `R<OrderVO>` |
| cancel | `POST /api/v1/orders/{id}/cancel` | `R<Void>` |

### 6.5 ChatRestController `/api/v1/chat`

| 方法 | 路径/HTTP | 返回类型（⚠️ 注意不是纯 R） |
|---|---|---|
| createConversation | `POST /api/v1/chat/conversations`（body 可空） | `R<ConversationVO>` |
| listConversations | `GET /api/v1/chat/conversations` | `R<List<ConversationVO>>` |
| listMessages | `GET /api/v1/chat/conversations/{id}/messages` | `R<List<MessageVO>>` |
| chat | `POST /api/v1/chat` | `R<String>`（data=完整回答文本） |
| stream | `POST /api/v1/chat/stream`，`produces=text/event-stream` | **直接返回 `Flux<String>`（不走 R 包装）**，SSE 逐段下发 |

---

## 7. 依赖与版本表

> 来源：`backend/pom.xml`。版本用 Maven property 管理；Spring AI 走 BOM。

| groupId | artifactId | 版本 | 作用 / 备注 |
|---|---|---|---|
| org.springframework.boot | spring-boot-starter-parent | **3.4.5**（parent） | 统管 Spring Boot 传递依赖版本 |
| org.springframework.boot | spring-boot-starter-web | （parent 管） | Web / REST / 内嵌 Tomcat |
| org.springframework.boot | spring-boot-starter-validation | （parent 管） | `@Valid` + jakarta.validation |
| org.mybatis.spring.boot | mybatis-spring-boot-starter | **3.0.4**（`${mybatis.version}`） | MyBatis 集成，XML 手写 SQL |
| com.mysql | mysql-connector-j | （runtime） | MySQL 驱动 |
| cn.dev33 | sa-token-spring-boot3-starter | **1.39.0**（`${sa-token.version}`） | 无状态 Token 认证 |
| org.springframework.security | spring-security-crypto | （parent 管） | **仅密码加密模块**，不引完整 Spring Security |
| org.springframework.ai | spring-ai-starter-model-openai | 由 **spring-ai-bom 1.0.0**（`dependencyManagement` import）管理 | OpenAI 兼容接入 DeepSeek |
| org.springframework.ai | spring-ai-bom | **1.0.0** | BOM，只 import 不引入 |
| org.projectlombok | lombok | （optional） | `@Data` 等 |
| org.springframework.boot | spring-boot-starter-test | test scope | 测试 |

**构建/工程信息**：groupId `com.aimall`，artifactId `ai-mall-backend`，version `1.0.0`，Java 17。
仓库另配 `aliyun-central`、`aliyun-spring`（阿里云镜像，仅本项目生效）。`spring-boot-maven-plugin` **exclude 了 lombok**。

---

## 8. application.yml 配置速查

| 配置块 | 关键项 | 值 / 说明 |
|---|---|---|
| server | port | `8080` |
| spring.datasource | driver | `com.mysql.cj.jdbc.Driver` |
| spring.datasource | url | `jdbc:mysql://192.168.6.102:3306/ai_mall?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true` |
| spring.datasource | username / password | `${MYSQL_USERNAME:root}` / `${MYSQL_PASSWORD:root123}` |
| spring.ai.openai | base-url | `https://opencode.ai/zen/go`（**不带 /v1**，Spring AI 自动拼 `/v1/chat/completions`；官方可改 `https://api.deepseek.com` + 模型 `deepseek-chat`） |
| spring.ai.openai | api-key | `${DEEPSEEK_API_KEY:sk-...}`（⚠️ 仓库内写有默认中转 Key，公开前必须改环境变量） |
| spring.ai.openai.chat.options | model | `deepseek-v4-pro` |
| spring.ai.openai.chat.options | temperature | `0.7` |
| spring.jackson | date-format | `yyyy-MM-dd HH:mm:ss` |
| spring.jackson | time-zone | `Asia/Shanghai` |
| mybatis | mapper-locations | `classpath:mapper/**/*.xml` |
| mybatis.configuration | map-underscore-to-camel-case | `true` |
| mybatis.configuration | log-impl | `org.apache.ibatis.logging.stdout.StdOutImpl` |
| sa-token | token-name | `Authorization`（兼容 `Bearer ` 前缀） |
| sa-token | timeout | `604800`（7 天） |
| sa-token | active-timeout | `-1`（不活跃不过期） |
| sa-token | is-concurrent / is-share | `true` / `false` |
| sa-token | token-style / is-log | `uuid` / `false` |
| logging.level | com.aimall | `info` |

---

## 9. 常见报错对照表

### 9.1 启动 / 环境类

| 现象 | 原因 | 解法 |
|---|---|---|
| 启动失败，`Unknown database 'ai_mall'` | 没跑初始化脚本 | `mysql -h192.168.6.102 -uroot -proot123 < sql/init.sql` |
| 启动失败，`Access denied for user` | 数据库账号/密码不符 | 改 `application.yml` 或设 `MYSQL_PASSWORD` 环境变量（默认 root/root123） |
| 启动失败，连接 `192.168.6.102:3306` 超时 | MySQL 不在该机器 / 未启动 | 确认 MySQL 地址与端口；本地库则改 JDBC URL |
| `Tomcat`/`Port 8080 already in use` | 端口被占用 | 换 `server.port` 或结束占用进程 |
| Maven 下载依赖极慢/失败 | 网络问题 | 已配阿里云镜像；检查网络或清本地仓库缓存 |
| 报 lombok 相关注解找不到类型 | 没装 Lombok 插件（IDE） | IntelliJ 装 Lombok 插件并 enable annotation processing |

### 9.2 认证 / 接口类

| code / 现象 | 原因 | 解法 |
|---|---|---|
| 401「未登录或登录已过期」 | 请求头没带 `Authorization`，或 token 失效（7 天） | 先登录拿到 `LoginVO.token`，加到 `Authorization` 头；确认没用错白名单（login/register 才免登录） |
| 400「字段校验失败」返回含 `username Pattern...` 等 | 入参不满足 `@Valid` 规则 | 自查：用户名 `^[a-zA-Z0-9_]{3,20}$`、密码 6-32、quantity 1..99、pageSize 1..100 |
| 1001 用户名已存在 | 注册重复 | 换用户名（DB 有 `uk_username`） |
| 1003 用户名或密码错误 | 密码不对 / 用户不存在 | ⚠️ 系统**故意不区分**"用户不存在"与"密码错误"，统一 1003；只查 `selectByUsername` + `passwordEncoder.matches` |
| 1004 账号已被禁用 | t_user.status=0 | 解开被禁用账号（改库或人工处理） |

### 9.3 电商域类

| code / 现象 | 原因 | 解法 |
|---|---|---|
| 2001 / 2002 商品/SKU 不存在 | id 传错，或根本无此记录 | 核对 skuId / productId；`selectById` 查无返回 |
| 2003 商品已下架 | `t_product.status=0` | 列表只出上架商品；换商品 / 刷新 |
| 2004 库存不足 | 库存为 0，或**并发抢光** | 减少数量或等补货；这是扣库存 SQL 返回 0 行的正常表现 |
| 2005 购物车条目不存在 | 条目不存在，或**不是当前用户**的条目 | 归属校验在 SQL WHERE 里（带 user_id），只能操作自己的条目 |
| 2006 购物车为空 | 下单 items 为空 | `CreateOrderRequest.items` 必须非空 |
| 2007 订单不存在 | 订单 id 错，或非本人订单 | detail/cancel 都有归属校验（`userId.equals`），只能看自己的订单 |
| 2008 订单状态不允许 | 该订单不是 `PENDING_PAY`（如已取消） | 状态机 CAS 更新 0 行触发；刷新订单状态再操作 |

### 9.4 AI 域类

| code / 现象 | 原因 | 解法 |
|---|---|---|
| 404「会话不存在」 | conversationId 传错 / 非本人会话 | `ensureOwned`：`selectById` + 归属校验，不存在或非本人即 404 |
| 3001 AI 服务暂时不可用 | DeepSeek 接口调用抛异常 | 检查网络 / api-key / base-url；重试 |
| SSE 流中断但收到 `[AI 服务暂时不可用，请稍后再试]` | 流过程中出错 | `onErrorResume` 兜底文案，**不是裸断**；检查 AI 可达性 |
| AI 回答为空 | base-url 带了 `/v1`，或模型名不对 | base-url 写 `https://opencode.ai/zen/go`（别加 `/v1`）；核对 `deepseek-v4-pro` |

### 9.5 中文乱码 / 时区类

| 现象 | 原因 | 解法 |
|---|---|---|
| 存储/返回中文乱码 | URL 缺字符集 / 表或连接字符集不一致 | JDBC URL 已有 `characterEncoding=UTF-8`；确认表 `utf8mb4`（init.sql 建库即 utf8mb4） |
| 日期差 8 小时 | 时区不对 | `application.yml` 已设 `time-zone: Asia/Shanghai` + JDBC `serverTimezone=Asia/Shanghai`；保持二者一致 |
| JSON 时间格式不是 `yyyy-MM-dd HH:mm:ss` | Jackson 配置没生效 | 已在 `spring.jackson.date-format` 配置；检查是否被覆盖 |

---

## 10. 面试 SQL 关键点定位

> 读源码/面试时按这里直接跳到文件对应方法，SQL 本体与注释都在 XML 里。

| 主题 | 去哪里看 | 一句话核心 |
|---|---|---|
| 防超卖（扣减） | `ProductSkuMapper.xml → deductStock` | `UPDATE ... SET stock=stock-? , sales=sales+? WHERE id=? AND stock>=?`（行锁 + 条件原子） |
| 防重复取消（CAS） | `OrderMapper.xml → updateStatus` | `UPDATE ... SET status=? [, cancel_time=?] , version=version+1 WHERE id=? AND status=?` |
| 下单事务整体性 | `OrderServiceImpl` 的 `create`（@Transactional(rollbackFor=Exception.class)） | 扣库存→写订单→写明细→清购物车，任一失败全回滚 |
| 金额 BigDecimal | Bean 全为 `BigDecimal`，DB 全 `DECIMAL(10,2)` | 全程精确计算，禁 double |
| 归属校验下沉 | `CartMapper.xml` 的 updateQuantity/deleteById；`OrderServiceImpl` 的 cancel/detail | 条件带 `user_id`，0 行=非本人→对应 2xxx |
| 订单号 | `OrderServiceImpl.create` 生成 | `时间戳17位+4位随机+userId%1000`，uk_order_no 兜底 |
| SSE 时序 | `ChatServiceImpl` 的 stream | doOnSubscribe 存用户消息 → doOnNext 累加 → doOnComplete 存回答 → onErrorResume 兜底 |
| AI 知识注入 | `ChatServiceImpl` 的 buildSystemPrompt | `productMapper.selectAllOnSale()` → 裁剪 → JSON → system prompt 强调不编造 |

---

> **手册结束。** 使用口诀：找类看第 1 节、读 SQL 跳第 4 节、对字段翻第 5 节、
> 版本依赖看第 7 节、报错查第 9 节、讲并发/一致性去第 10 节。