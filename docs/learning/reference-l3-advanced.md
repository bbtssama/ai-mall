# Reference 参考手册 · L3 面试进阶版

> **面向"面试准备 / 深入读源码"的进阶开发者**：本手册按下述主题把 ai-mall 的**关键决策点**
> 钉死到具体文件与行号，方便你面试时"一开口就有源码坐标"。
> 每一条都附出处（`backend/src/main/java/com/aimall/...`，行号为统计时点的真实值）。
> 只重定位、重精确，不展开讲基础原理。
>
> 读者定位：会 SSM、懂事务/并发/HTTP/流式，打算把 ai-mall 讲成面试亮点。
> 事实来源：`00-backend-material-pack.md` + 后端全部源码逐文件核对。

---

## 目录

1. [各文件行号速查（面试常用的锚点）](#1-各文件行号速查面试常用的锚点)
2. [分层架构与包契约速查](#2-分层架构与包契约速查)
3. [事务与并发控制点全表（含 SQL 原文）](#3-事务与并发控制点全表含-sql-原文)
4. [SSE 流式问答链路时序速查](#4-sse-流式问答链路时序速查)
5. [Sa-Token 认证机制速查](#5-sa-token-认证机制速查)
6. [性能 / 安全 / 扩展性隐患清单](#6-性能--安全--扩展性隐患清单)
7. [易混点与"面试说不出口"的对照](#7-易混点与面试说不出口的对照)
8. [关键决策代码原样速查（面试背诵用）](#8-关键决策代码原样速查面试背诵用)

---

## 1. 各文件行号速查（面试常用的锚点）

> 基础路径 `backend/src/main/java/com/aimall/`；`XML` 指 `src/main/resources/mapper/*.xml`。
> 行号锚点 = 面试时可以直接说的出处。

| 话题 | 锚点（文件:行） |
|---|---|
| 防超卖扣库存 | `OrderServiceImpl.java:71`（调 deductStock）、`ProductSkuMapper.xml:32`（SQL） |
| 事务边界（下单） | `OrderServiceImpl.java:46` `@Transactional(rollbackFor=Exception.class)` |
| 事务边界（取消） | `OrderServiceImpl.java:126` |
| 取消状态机 CAS | `OrderServiceImpl.java:134`、`OrderMapper.xml:44` |
| 订单号生成 | `OrderServiceImpl.java:161` |
| 金额 BigDecimal 累加 | `OrderServiceImpl.java:82` |
| 归属校验下沉 SQL | `CartMapper.xml:34`(updateQuantity)、`:40`(deleteById) |
| 下单清购物车 | `OrderServiceImpl.java:101` `deleteByUserIdAndSkuIds` |
| SSE 全链路算子 | `ChatServiceImpl.java:91-108` |
| 会话归属校验 | `ChatServiceImpl.java:120` ensureOwned |
| AI 知识注入 system prompt | `ChatServiceImpl.java:159` buildSystemPrompt |
| 商品列表起售价聚合 | `ProductMapper.xml:7` |
| 登录统一提示（防枚举） | `UserServiceImpl.java:49` |
| 全局异常→R | `GlobalExceptionHandler.java:20-59` |
| Sa-Token 拦截白名单 | `SaTokenConfig.java:14-23` |

---

## 2. 分层架构与包契约速查

### 2.1 打包分区（V4 微服务边界早已埋好）

| 业务域包 | 模块（未来 V4 微服务名） | 暴露的接口前缀 | 含哪些子包 |
|---|---|---|---|
| `user` | 用户/认证服务 | `/api/v1/auth` | bean / controller / dto / mapper / service / service/impl |
| `goods` | 商品（+购物车）服务 | `/api/v1/products`、`/api/v1/cart` | 同上 |
| `order` | 订单服务 | `/api/v1/orders` | 同上 |
| `ai` | AI 导购服务 | `/api/v1/chat` | 同上 |
| `common` | （公共基础库，不属单服务） | — | api / exception / page |
| `config` | （闸门/网关边界的配置位） | — | AiConfig / PasswordConfig / SaTokenConfig / WebConfig |

> V4 拆分依据（`AimallApplication.java:9` 注释）：「按业务域分包，为 V4 拆微服务预留边界」。
> 每个域自含 `controller→service→mapper`，域内依赖、域间通过商品/订单 Mapper 跨域查表（见 3.3 的耦合点说明）。

### 2.2 分层职责速查

| 层 | 包 | 职责（一句话） | 本项目的对应物 |
|---|---|---|---|
| 表现层 | `controller` | 收参 / 返回 R | 5 个 `*RestController` |
| 业务层 | `service(.impl)` | 编排业务规则 / 事务 / 校验 / 异常 | 4 个 Service + 4 个 Impl |
| 数据访问层 | `mapper` + XML | 手写 SQL / 访问 DB | 8 个 Mapper 接口 + 8 个 XML |
| 领域模型 | `bean` | 表实体（贫血模型） | 8 个 bean |
| DTO/VO | `dto` | 入参/出参脱敏与装配 | Request / V O |
| 通用横切 | `common` | 统一返回 / 异常 / 分页 | R / ResultCode / GlobalExceptionHandler |
| 配置 | `config` | Bean 与过滤器注册 | 4 个 config |

### 2.3 分层约定契约（防"跨层违规"的记忆点）

| 约定 | 说明 | 出处 |
|---|---|---|
| 返回必须走 R | 除 SSE 之外，Controller 一律返回 `R<T>`；错误不进业务层直接拼 JSON | `R.java:10` |
| 事务只在 Service 层 | `@Transactional` 只出现在 `OrderServiceImpl.create/cancel` | `OrderServiceImpl.java:46/126` |
| Mapper 逐接口 `@Mapper` | 无 @MapperScan，AimallApplication 注释明确说明 | `AimallApplication.java:9-10` |
| 归属校验下沉 SQL | 购物车的改/删、订单的查/取消都在 SQL WHERE 或查询后带 userId 判断 | `CartMapper.xml`、`OrderServiceImpl.java:130/147` |
| 金额全 BigDecimal | 字段与累加全程 BigDecimal，禁 double | `OrderServiceImpl.java:82` |

---

## 3. 事务与并发控制点全表（含 SQL 原文）

### 3.1 @Transactional 确切位置（全项目仅两处）

| 方法 | 文件:行 | 备注 |
|---|---|---|
| `OrderService.create`（下单） | `OrderServiceImpl.java:45-46` `@Transactional(rollbackFor = Exception.class)` | 覆盖扣库存→写订单→写明细→清购物车，**任一失败全部回滚（含已扣库存）** |
| `OrderService.cancel`（取消） | `OrderServiceImpl.java:125-126` | 覆盖状态机 CAS + 逐条回补库存，失败回滚 |

> 面试点：`rollbackFor=Exception.class` 是显式声明（Spring 默认只回滚 RuntimeException/Error）；
> 这是数据一致性从"默认值"升级到"所有异常都回滚"的关键一行。

### 3.2 防超卖 / 并发扣减（核心 SQL 原文）

`ProductSkuMapper.xml:31-37`：

```sql
<update id="deductStock">
    UPDATE t_product_sku
    SET stock = stock - #{quantity},
        sales = sales + #{quantity}
    WHERE id = #{id} AND stock >= #{quantity}
</update>
```

- **机制**：MySQL 行级锁 + `stock>=?` 条件判定，**扣减与检查原子完成**，非"先查再减"。
- **信号**：`update` 返回受影响行数 = 0，即并发下库存已被抢光 → Service 抛 `STOCK_NOT_ENOUGH`。
- **两层防线**：内存预检（`OrderServiceImpl.java:66`）只是提前友好提示；真正防线是这条 SQL。

### 3.3 下单完整事务（含跨域耦合点）

`OrderServiceImpl.create`（46-104）步骤：

| 步 | 做的事 | 关键代码 / Mapper |
|---|---|---|
| 0 | 取当前用户 | `StpUtil.getLoginIdAsLong()` :48 |
| 1 | items 空 → CART_EMPTY | :50-52 |
| 2 | 逐项：查 SKU→查商品→验上架→内存预检→**deductStock 乐观扣**→组明细→累加 total | :57-83；`skuMapper.deductStock` :71 |
| 3 | 组订单主表（orderNo / status=PENDING_PAY / 收货信息）→ insert | :86-94 |
| 4 | 明细批量插入（快照） | :96-98 `orderItemMapper.batchInsert` |
| 5 | 清购物车中本次 SKU | :100-102 `cartMapper.deleteByUserIdAndSkuIds` |

> 面试点1：先扣 SKU 再写订单，若后续 insert 失败，@Transactional 会把已扣的库存回滚 —— **不会出现"扣了款没订单"或"扣了库存没记录"**。
> 面试点2：明细存的是**商品名/规格名/单价快照**（`:77-79`），避免后续商品改价影响历史订单。

### 3.4 取消订单状态机 CAS（防并发重复取消）

`OrderMapper.xml:43-51` 原文：

```sql
<update id="updateStatus">
    UPDATE t_order
    SET status = #{toStatus},
        <if test="toStatus == 'CANCELLED'">cancel_time = #{cancelTime},</if>
        version = version + 1
    WHERE id = #{id} AND status = #{fromStatus}
</update>
```

`OrderServiceImpl.cancel`：归属校验 :130 → CAS :134（fromStatus=PENDING_PAY, toStatus=CANCELLED）→ 返回 0=状态不符抛 `ORDER_STATUS_INVALID` :136-138 → 回补库存 :140-141。

> 面试点：`WHERE status=fromStatus` 是**状态机 CAS**，两个并发取消只可能一个成功；
> `cancel_time` 只有取消方向才回填（`<if>`）；`version+1` 是乐观锁痕迹（V3 秒杀强化用，当前 V1 未校 version）。

### 3.5 其他一致性点定位

| 点 | 位置 | 一句话 |
|---|---|---|
| 用户唯一（防并发重注册） | `UserServiceImpl.java:30` + DB `uk_username` | 先 selectByName，唯一索引兜底 |
| 订单号唯一 | `OrderServiceImpl.java:161` 生成 + DB `uk_order_no` 兜底 | 时间戳17位+随机4位+userId%1000，极端并发靠唯一索引 |
| 同 SKU 购物车合并 | `CartService.add` + DB `uk_user_sku` | 加购查重，已有则数量相加封顶 99（保证单行） |
| 订单列表索引 | `OrderMapper.xml:24` | 走 `(user_id, created_at)` 联合索引 |
| 兜底异常不泄漏 | `GlobalExceptionHandler.java:55-59` | 500 只给"服务器开小差了"，堆栈进 log.error |

---

## 4. SSE 流式问答链路时序速查

### 4.1 一图链路（HTTP + Reactor + Spring AI）

```
前端 fetch(POST /api/v1/chat/stream)
   │ 请求体 ChatRequest{message, conversationId?}
   ▼
ChatRestController.stream  (ChatRestController.java:55-58)
   produces = text/event-stream，controller 直接 return Flux<String>（不包 R）
   ▼
ChatServiceImpl.stream (ChatServiceImpl.java:91-108)
   resolveConversation :92          # 归属校验或自动建会话
   chatClient.prompt().system(buildSystemPrompt())
     .messages(toAiHistory).user(msg).stream().content()   # 返回 Flux<String>
   ├─ doOnSubscribe → saveMessage(USER)      :101   # 订阅即持久化用户问题
   ├─ doOnNext(sb::append)                   :102   # 逐块累加回答
   ├─ doOnComplete → saveMessage(ASSISTANT)  :103   # 流完成持久化完整回答
   └─ onErrorResume → Flux.just(兜底文案)    :104-108
   ▼
逐段 emit 到 HTTP response → 前端逐字渲染
```

### 4.2 算子时序表（精确位置 + 落库时机）

| 算子 | 文件:行 | 触发时机 | 副作用 |
|---|---|---|---|
| `resolveConversation` | ChatServiceImpl:92 | 订阅**之前**（同步执行） | 校验归属 / 自动新建会话 |
| `.stream()`/`.content()` | :94-99 | 建立到 Spring AI 的流 | 返回 Reactor `Flux` |
| `.doOnSubscribe(...)` | :101 | **订阅开始（第一块前）** | ✅ **落库用户提问**（防流失败留孤儿问题） |
| `.doOnNext(sb::append)` | :102 | 每收到一块回答 | 内存累加（不逐块落库） |
| `.doOnComplete(...)` | :103 | 流正常结束 | ✅ **落库完整 assistant 回答** |
| `.onErrorResume(...)` | :104-108 | 上游出错 | 返回 `Flux.just("\n\n[AI 服务暂时不可用，请稍后再试]")`，**避免 SSE 裸断** |

### 4.3 时序保证（面试点）

| 保证 | 说明 |
|---|---|
| "消息对"不丢 | 用户消息在订阅时先存（:101），回答在结束时存（:103）；无论成功失败，用户提问都已入库 |
| SSE 不裸断 | 出错走 `onErrorResume` 兜底文案，连接不中断，前端不会停在半句话 |
| 历史不重复 | `toAiHistory`（:146）只取 DB 已完成的轮次，本次提问不入 history，避免与当前问题重复 |
| 非流式对称 | `chat()`（:70-88）同步调用：成功才双写 user+assistant；失败抛 `AI_SERVICE_ERROR` |

---

## 5. Sa-Token 认证机制速查

### 5.1 机制流程

```
登录成功(UserServiceImpl.login:55) ── StpUtil.login(userId)
   │  签发 token（uuid 字符串），存服务端会话
   ▼
前端拿 LoginVO.token，后续每次请求带头 Authorization: <token>
   ▼
SaInterceptor(ChatRestController 等所有 /api/**, SaTokenConfig.java:16) 校验 token
   │  未带/失效 → 抛 NotLoginException
   ▼
GlobalExceptionHandler:30-33 → R.fail(401, "未登录或登录已过期")
```

### 5.2 配置与语义速查

| 配置项 | 值 | 语义 / 面试点 |
|---|---|---|
| `token-name` | `Authorization` | 请求头名；兼容 `Bearer ` 前缀 |
| `timeout` | 604800（7 天） | 无状态 token 寿命；俗称"免登录 7 天" |
| `active-timeout` | -1 | 不因不活跃过期 |
| `is-concurrent` | true | 允许多端同时登录（每端单独 token） |
| `is-share` | false | 不共用 token（配合并发登录） |
| `token-style` | uuid | 随机字符串，非 JWT/自增 |
| `is-log` | false | 不输出登录日志 |
| 白名单 | /api/v1/auth/login、/api/v1/auth/register、/error | `SaTokenConfig.java:18-22` exclude |

### 5.3 常用 StpUtil 调用点

| 场景 | 调用（文件:行） |
|---|---|
| 签发 / 写会话 | `StpUtil.login(user.getId())` → `UserServiceImpl.java:55` |
| 读 token 值返回给前端 | `StpUtil.getTokenValue()` → `UserServiceImpl.java:56` |
| 取当前用户 id | `StpUtil.getLoginIdAsLong()` → 下单/购物车/订单/会话各处 |
| 登出 | `StpUtil.logout()` → `UserServiceImpl.java:61` |
| 拦截校验 | `SaInterceptor` → `SaTokenConfig.java:16` |
| 401 翻译 | `GlobalExceptionHandler.java:30-33` |

---

## 6. 性能 / 安全 / 扩展性隐患清单

> 全部来自真实代码，标注出处；"隐患 = 项目当前真实取舍"，正是面试谈「演进」的素材。

### 6.1 性能隐患

| 隐患 | 出处 | 说明 / 演进方向 |
|---|---|---|
| 商品列表全量注入 AI | `ChatServiceImpl.buildSystemPrompt` :159-168（`selectAllOnSale` 全量上架商品 JSON 进 system prompt） | 演示数据量小可接受；规模大后 token 爆炸。**V2 换 RAG**（t_product_doc 分块 + embedding） |
| 金额逐条乘加在 Java | `OrderServiceImpl.java:82` `total.add(price×qty)` | 数据量小无碍；量大可下推到 SQL 聚合，但需保快照语义 |
| 订单明细快照逐条组对象 | `OrderServiceImpl.java:75-81` | 内存对象多；可换批量构造 |
| 无分页做全量 SQL（AI 场景） | `ProductMapper.xml:29 selectAllOnSale` | 有意的全量（AI 知识），但缺 LIMIT 风险口头说明 |
| 无缓存、每请求全查 | 全局无 Redis/本地缓存依赖 | **V3 引 Redis**（购物车迁 Hash、热点缓存） |
| 无连接池调优 / 慢 SQL 日志 | application.yml 仅 StdOutImpl | 生产需加 druid/hikari 调参与慢查监控 |

### 6.2 安全隐患

| 隐患 | 出处 | 说明 / 缓解 |
|---|---|---|
| **application.yml 写死中转 API Key** | `application.yml:20` `${DEEPSEEK_API_KEY:sk-...}` 含明文默认值 | README/.gitignore 均提示公开前改环境变量；生产必须走 `DEEPSEEK_API_KEY` |
| 用户唯一性仅"先查后插"+唯一索引 | `UserServiceImpl.java:30` | uk 兜底了并发，但 1001 语义依赖先查的结果，轻微竞态窗口可接受 |
| 越权防护靠 userId 双查 | `CartMapper.xml`、`OrderServiceImpl.java:130` | 已做，但分散在各方法，面试可归纳为"归属校验下沉 SQL + 查询后二次校验" |
| 登录不区分用户不存在/密码错误 | `UserServiceImpl.java:49` | 这是**安全设计**（防用户名枚举），不是漏洞 |
| 无接口限流 / 无防刷 | 全局无 | 加购/下单/AI 接口高可用场景需加限流 |
| CORS 放开 localhost 任意端口 | `WebConfig.java:16` | 仅为开发便利，生产由网关/同域收敛 |
| AI system prompt 依赖数据可信 | buildSystemPrompt 注入 DB 商品 | 商品库受污染会注入执行上下文；V2 RAG 前需对 detail 做清洗 |

### 6.3 扩展性与架构隐患

| 隐患 | 出处 | 说明 / 演进 |
|---|---|---|
| 购物车用 MySQL | `t_cart`（init.sql）+ `Cart.java` | **V3 迁 Redis Hash(user_id→sku_id→count)** |
| 版本乐观锁 V1 未真正使用 | `OrderMapper.xml` `version=version+1`；`ProductSku.version` | V1 靠 `stock>=?` 与状态 CAS，version 是**预留乐观锁痕迹**；V3 秒杀强化用它做校验 |
| 订单状态机仅实现一档迁移 | `Order.STATUS_*` 常量 + `updateStatus` | V1 只做 PENDING_PAY→CANCELLED；PAID/SHIPPED/COMPLETED 预留（V2 支付回调链路补全） |
| 单表无分表/无 MQ | 全表 + 事务直连 | **V3 引 MQ 做下单异步解耦**、V4 微服务拆分 |
| 会话/消息无分页 | `ChatService.listMessages` 一次全查 | 长会话内存压力；需分页 |
| 无单元测试 | 素材包第 9 节 | 仅 `scripts/smoke-test.ps1` 黑盒 9 项；"真实工程补单测"是加分推进项 |

---

## 7. 易混点与"面试说不出口"的对照

| 易混点 | 正解（源码位置） |
|---|---|
| "防超卖是查库存再判断" | ❌ 非先查再减；✅ `UPDATE ... WHERE stock>=?` 原子扣减（`ProductSkuMapper.xml:32`），内存预检只是提前提示 |
| "乐观锁靠 version" | ⚠️ V1 主线是**条件扣减/状态 CAS**（stock>=?、fromStatus=?）；version 字段在更新时 +1 属**预留痕迹**，V1 未校验（`ProductSku.version`、`OrderMapper.xml:48`） |
| "事务没写 rollbackFor" | ✅ 两处都显式 `rollbackFor=Exception.class`（`OrderServiceImpl.java:46/126`） |
| "Sa-Token 是 JWT" | ❌ token-style=uuid，**无状态会话**（服务端存），非自包含 JWT |
| "AI 走 R 返回" | ❌ `/chat/stream` 直接返回 `Flux<String>`（`ChatRestController.java:55-58`），只有普通 `/chat` 是 `R<String>` |
| "取消订单可能重复回补库存" | ✅ 状态 CAS 保证，只有一个成功才走 addStock（`OrderServiceImpl.java:134-141`） |
| "用户密码是明文" | ❌ `passwordEncoder.encode` BCrypt（`UserServiceImpl.java:35`），DB 存 `password_hash` |
| "用户名不存在会暴露" | ✅ 故意统一 `PASSWORD_ERROR`（`UserServiceImpl.java:49`），防枚举 |
| "扣库存后订单写失败会丢库存" | ❌ 同事务回滚（`OrderServiceImpl.java:46` + 81 抛错回滚） |
| "SSE 半句话断流正常" | ❌ `onErrorResume` 兜底文案，连接不裸断（`ChatServiceImpl.java:104-108`） |

---

---

## 8. 关键决策代码原样速查（面试背诵用）

> 以下均为从真实源码**原样摘录**的片段（含 package/注释风格），面试分点引用即可。

### 8.1 防超卖扣库存（两处配合）

`OrderServiceImpl.create` 内循环（`OrderServiceImpl.java:97-83` 摘录）：

```java
// 数据库层乐观扣减（WHERE stock >= ?），返回 0 = 并发下库存已被抢光
if (skuMapper.deductStock(sku.getId(), it.getQuantity()) == 0) {
    throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
            "商品[" + product.getSpuName() + "]库存不足");
}
```

`ProductSkuMapper.xml`（`ProductSkuMapper.xml:31-37`）：

```xml
<!-- 乐观扣减：数据库行锁 + stock>= 条件保证不超卖；返回 0 行 = 库存不足 -->
<update id="deductStock">
    UPDATE t_product_sku
    SET stock = stock - #{quantity},
        sales = sales + #{quantity}
    WHERE id = #{id} AND stock >= #{quantity}
</update>
```

### 8.2 下单事务 + 金额 BigDecimal（`OrderServiceImpl.java:70-90` 摘录）

```java
total = total.add(sku.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
...
Order order = new Order();
order.setOrderNo(generateOrderNo(userId));
order.setUserId(userId);
order.setTotalAmount(total);
order.setStatus(Order.STATUS_PENDING_PAY);
order.setReceiverName(req.getReceiverName());
order.setReceiverPhone(req.getReceiverPhone());
order.setReceiverAddress(req.getReceiverAddress());
orderMapper.insert(order);
```

### 8.3 取消订单状态机 CAS（`OrderServiceImpl.java:126-142` 原样）

```java
@Transactional(rollbackFor = Exception.class)
public void cancel(Long id) {
    Long userId = StpUtil.getLoginIdAsLong();
    Order order = orderMapper.selectById(id);
    if (order == null || !order.getUserId().equals(userId)) {
        throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
    }
    // 状态机 CAS：仅 PENDING_PAY 可取消，防并发重复取消
    int rows = orderMapper.updateStatus(id, Order.STATUS_PENDING_PAY,
            Order.STATUS_CANCELLED, LocalDateTime.now());
    if (rows == 0) {
        throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "仅待支付订单可取消");
    }
    // 回补库存
    orderItemMapper.selectByOrderId(id)
            .forEach(oi -> skuMapper.addStock(oi.getSkuId(), oi.getQuantity()));
}
```

### 8.4 SSE 流式核心（`ChatServiceImpl.java:91-109` 原样）

```java
@Override
public Flux<String> stream(ChatRequest req) {
    Conversation conv = resolveConversation(req);
    StringBuilder sb = new StringBuilder();
    return chatClient.prompt()
            .system(buildSystemPrompt())
            .messages(toAiHistory(conv.getId()))
            .user(req.getMessage())
            .stream()
            .content()
            // 订阅开始即持久化用户提问（避免流失败时留下孤儿问题）
            .doOnSubscribe(s -> saveMessage(conv.getId(), Message.ROLE_USER, req.getMessage()))
            .doOnNext(sb::append)
            .doOnComplete(() -> saveMessage(conv.getId(), Message.ROLE_ASSISTANT, sb.toString()))
            .onErrorResume(e -> {
                log.error("AI 流式问答失败: {}", e.getMessage(), e);
                // 兜底文案，避免 SSE 连接裸断
                return Flux.just("\n\n[AI 服务暂时不可用，请稍后再试]");
            });
}
```

### 8.5 AI 预置知识 system prompt（`ChatServiceImpl.java:159-188` 摘录）

```java
private String buildSystemPrompt() {
    List<Product> products = productMapper.selectAllOnSale();
    StringBuilder sb = new StringBuilder();
    sb.append("你是「AI 种草助手」，一个耐心、专业的电商导购。")
      .append("回答用户问题时，请基于提供的商品库信息如实回答；")
      .append("介绍商品时给出名称、价格区间与核心卖点；")
      .append("如果商品库中没有相关信息，坦诚说明不知道，绝对不要编造商品或参数。")
      .append("\n\n【本店在售商品库(JSON)】\n");
    sb.append(toKnowledgeJson(products));
    return sb.toString();
}
```

### 8.6 登录统一提示（防用户名枚举）（`UserServiceImpl.java:49-54` 原样）

```java
// 统一提示，避免暴露"用户名是否存在"
if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
    throw new BusinessException(ResultCode.PASSWORD_ERROR);
}
if (user.getStatus() != null && user.getStatus() == 0) {
    throw new BusinessException(ResultCode.USER_DISABLED);
}
StpUtil.login(user.getId());
return new LoginVO(StpUtil.getTokenValue(), toVO(user));
```

### 8.7 归属校验下沉 SQL（`CartMapper.xml:34-42` 原样）

```xml
<update id="updateQuantity">
    UPDATE t_cart
    SET quantity = #{quantity}
    WHERE id = #{id} AND user_id = #{userId}
</update>

<delete id="deleteById">
    DELETE FROM t_cart WHERE id = #{id} AND user_id = #{userId}
</delete>
```

### 8.8 全局异常兜底（`GlobalExceptionHandler.java:55-59` 原样）

```java
/** 兜底异常 */
@ExceptionHandler(Exception.class)
public R<Void> handleOther(Exception e) {
    log.error("系统异常", e);
    return R.fail(ResultCode.SERVER_ERROR);
}
```

> **手册结束。** 面试定位口诀：谈架构→第 2 节；谈并发/事务→第 3 节与第 8 节代码；
> 谈流式→第 4 节与 8.4；谈认证→第 5 节；谈坑与演进→第 6 节；怕说反→对照第 7 节；
> 要背原样代码→第 8 节。