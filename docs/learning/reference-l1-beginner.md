# Reference 参考手册 · L1 零基础版

> **ai-mall 后端「小词典」**：这本手册不讲解原理，只负责**查**。
> 想查"怎么启动""某个接口怎么调""某张表什么结构""某个错误码什么意思"——按下面目录翻到对应表格即可。
>
> 读者定位：零基础（会看表格、会用"查找"功能）。所有内容均从
> `00-backend-material-pack.md`（事实来源）与 `sql/init.sql`（真实建表脚本）提取核对，没有臆造。

---

## 目录

1. [快速定位：这本词典怎么用](#1-快速定位这本词典怎么用)
2. [项目名片：技术栈速查](#2-项目名片技术栈速查)
3. [启动与运行速查](#3-启动与运行速查)
4. [代码目录速查](#4-代码目录速查)
5. [统一返回 R 与错误码速查](#5-统一返回-r-与错误码速查)
6. [接口速查（按业务域）](#6-接口速查按业务域)
7. [数据表速查（8 张表）](#7-数据表速查8-张表)
8. [常量与状态速查](#8-常量与状态速查)
9. [关键 SQL 速查（并发/一致性）](#9-关键-sql-速查并发一致性)
10. [种子数据速查](#10-种子数据速查)
11. [常见报错对照速查](#11-常见报错对照速查)

---

## 1. 快速定位：这本词典怎么用

| 我想…… | 翻到 |
|---|---|
| 把项目跑起来 / 初始化数据库 | [第 3 节 · 启动与运行速查](#3-启动与运行速查) |
| 知道项目用了什么技术 | [第 2 节 · 项目名片](#2-项目名片技术栈速查) |
| 查某个接口：方法、路径、入参、出参、要不要登录 | [第 6 节 · 接口速查](#6-接口速查按业务域) |
| 查某张表的字段/类型/索引 | [第 7 节 · 数据表速查](#7-数据表速查8-张表) |
| 查错误码 1001 / 2004 / 3001 是什么意思 | [第 5 节 · 错误码](#5-统一返回-r-与错误码速查) 与 [第 11 节 · 报错对照](#11-常见报错对照速查) |
| 查订单状态、角色、常量（7 天有效期、99 上限……） | [第 8 节 · 常量与状态速查](#8-常量与状态速查) |

**读接口速查表的通用约定（先看这一行）：**

| 表头 | 含义 |
|---|---|
| 操作 | 这个接口是干什么的 |
| 方法与路径 | HTTP 方法 + 完整接口路径（统一前缀 `/api/v1`，前端经 `/api` 代理到 8080） |
| 入参 | 调用时要传的内容：路径参数（URL 里的 `{id}`）/ 查询参数（`?page=1`）/ body（JSON 请求体） |
| 出参 | 返回内容；所有接口都包在 `R{code,msg,data}` 里，此列只写 `data` 部分；写"R"表示成功即可、`data` 无特定结构 |
| 需登录 | 是否必须携带登录后拿到的 `Authorization` 请求头（token） |
| 失败时 | 该接口主要失败场景对应的错误码（完整表见第 5 节） |

> 登录规则一句话：**只有注册、登录两个接口不用登录**（Sa-Token 白名单），其余 `/api/**` 接口都要带 `Authorization` 头，否则返回 401「未登录或登录已过期」。

---

## 2. 项目名片：技术栈速查

| 项 | 值 |
|---|---|
| 项目全称 | AI 种草商城（ai-mall）V1：单体 Spring Boot 电商项目 |
| 一句话链路 | 注册登录 → 浏览商品 → 加购物车 → 下单（防超卖）→ AI 问答（SSE 流式） |
| 后端框架 | Spring Boot 3.4.5（parent），Java 17 |
| ORM | MyBatis `mybatis-spring-boot-starter` 3.0.4，**XML 手写 SQL**（无 MyBatis-Plus / PageHelper） |
| 认证 | Sa-Token 1.39.0（`sa-token-spring-boot3-starter`），无状态 Token |
| 密码 | spring-security-crypto 仅 BCrypt 模块（不引入完整 Spring Security） |
| AI | Spring AI 1.0（`spring-ai-starter-model-openai`），OpenAI 兼容模式，模型 `deepseek-v4-pro` |
| 数据库 | MySQL 8，库名 `ai_mall`，开发机 JDBC：`jdbc:mysql://192.168.6.102:3306/ai_mall?...` |
| Web | spring-boot-starter-web + validation |
| 前端（背景） | Vue3 + Vite + Element Plus，`/api` 代理到 8080 |
| Lombok | 启用（@Data / @RequiredArgsConstructor / @Slf4j 等） |
| Maven | 阿里云镜像加速**仅本项目生效** |
| 项目定位 | 初级 Java 求职实战项目 |

---

## 3. 启动与运行速查

### 3.1 环境准备

| 需要 | 说明 |
|---|---|
| JDK 17 | 后端编译运行 |
| Maven | 后端构建（依赖走阿里云镜像） |
| MySQL 8 | 数据库（开发机在 `192.168.6.102`，用户名/密码默认 `root` / `root123`） |
| Node.js | 仅前端需要（前端只是背景，接口调试可不用） |

### 3.2 三步启动（命令表）

| 顺序 | 命令 | 干什么 | 产物/端口 |
|---|---|---|---|
| 1 | `mysql -h192.168.6.102 -uroot -proot123 < sql/init.sql` | 建库 `ai_mall` + 8 张表 + 种子数据（4 商品 8 SKU） | 数据库就绪 |
| 2 | `cd backend && mvn spring-boot:run` | 启动后端（工作目录在项目根下的 `backend`） | 后端 `8080` 端口 |
| 3 | `cd frontend && npm install && npm run dev` | 启动前端开发服务器 | 前端 `5173` 端口，`/api` 代理到 8080 |

### 3.3 冒烟测试（一键自检）

| 命令 | 检查内容 |
|---|---|
| `pwsh -File scripts/smoke-test.ps1` | 9 项：注册 / 登录 / 商品列表 / 商品详情 / 加购 / 购物车 / 下单 / 订单 / AI 问答（全过即 9/9） |

### 3.4 环境变量（application.yml 可覆盖项）

| 变量名 | 默认值 | 作用 |
|---|---|---|
| `MYSQL_USERNAME` | `root` | 数据库用户名 |
| `MYSQL_PASSWORD` | `root123` | 数据库密码 |
| `DEEPSEEK_API_KEY` | `sk-...` | AI 接口密钥（公开发布前务必改用环境变量，别暴露中转 Key） |

### 3.5 常见启动问题速查

| 现象 | 排查方向 |
|---|---|
| 后端起不来、报数据库连接失败 | 检查 MySQL 是否启动、`192.168.6.102` 是否可达；连本地库则改 `application.yml` 的 JDBC URL |
| 8080 被占用 | 换端口或结束占用进程 |
| MySQL 报 `Unknown database 'ai_mall'` | 还没执行第 1 步 `init.sql` |
| 登录后前端报 401 | 请求头缺少 `Authorization` 或 token 过期（有效期 7 天） |

---

## 4. 代码目录速查

### 4.1 源码结构（backend/src/main）

| 路径 | 放什么 |
|---|---|
| `java/com/aimall/AimallApplication.java` | 启动类（@SpringBootApplication，**无 @MapperScan**） |
| `java/com/aimall/common/` | `R` 统一返回 / `ResultCode` / `BusinessException` / `GlobalExceptionHandler` / `PageQuery` / `PageResult` |
| `java/com/aimall/config/` | `AiConfig` / `PasswordConfig` / `SaTokenConfig` / `WebConfig` |
| `java/com/aimall/user/` | 注册 / 登录 / 登出 / me |
| `java/com/aimall/goods/` | 商品列表 / 详情 / SKU / 购物车 |
| `java/com/aimall/order/` | 下单 / 列表 / 详情 / 取消 |
| `java/com/aimall/ai/` | 会话管理 / 问答（SSE 流式） |
| `resources/application.yml` | 主配置文件 |
| `resources/mapper/*.xml` | 8 个 MyBatis XML：User / Cart / Product / ProductSku / Order / OrderItem / Conversation / Message |

### 4.2 分层约定（找代码时照着猜位置）

| 约定 | 规则 |
|---|---|
| 实体包名 | 叫 `bean`（不叫 entity） |
| Controller 命名 | `XxxRestController` |
| 接口前缀 | 统一 `/api/v1` |
| Mapper 注册 | 逐接口 `@Mapper`（**不用 @MapperScan**） |
| 业务域内部结构 | `bean / controller / dto / mapper / service(接口) / service/impl` |
| 依赖注入 | Service 实现用构造器注入（`@RequiredArgsConstructor` + `private final`） |
| 分页 | 手写 `LIMIT offset,size` + `PageResult{records,total,page,pageSize}`，不引分页插件 |

---

## 5. 统一返回 R 与错误码速查

### 5.1 R 统一返回结构

所有接口的返回体固定为：

```json
{ "code": 200, "msg": "ok", "data": { ... } }
```

| 字段 | 类型 | 含义 |
|---|---|---|
| code | int | 业务码，见 5.3 错误码表 |
| msg | String | 提示信息 |
| data | T | 业务数据（成功时才有；失败时通常为空） |

### 5.2 R 静态工厂速查

| 工厂方法 | 效果 |
|---|---|
| `ok()` | 成功，无 data |
| `ok(T data)` | 成功 + data |
| `ok(String msg, T data)` | 成功 + 自定义提示 + data |
| `fail(String msg)` | 失败，默认码 500 + 提示语 |
| `fail(ResultCode)` | 按枚举失败（code/msg 取自枚举） |
| `fail(int code, String msg)` | 自定义码 + 提示语 |

> ⚠️ 记忆点：本项目**故意去掉单参 `ok(String)` 重载**（早期项目因此把 `R.ok(chatService.chat(req))` 解析错、丢失 data）。查代码时见到 `ok(...)` 只可能是上表的几种。

### 5.3 ResultCode 错误码表（查字典用）

| 分段 | 常量名 | code | 含义 / 典型触发场景 |
|---|---|---|---|
| 通用 | `SUCCESS` | 200 | 成功 |
| 通用 | `BAD_REQUEST` | 400 | 参数错误（校验失败 / 缺参 / JSON 不可读） |
| 通用 | `UNAUTHORIZED` | 401 | 未登录或登录已过期 |
| 通用 | `FORBIDDEN` | 403 | 无权限 |
| 通用 | `NOT_FOUND` | 404 | 资源不存在（含「会话不存在」） |
| 通用 | `SERVER_ERROR` | 500 | 服务器内部错误 |
| 用户 1xxx | `USERNAME_EXISTS` | 1001 | 用户名已被注册 |
| 用户 1xxx | `USER_NOT_FOUND` | 1002 | 用户不存在 |
| 用户 1xxx | `PASSWORD_ERROR` | 1003 | 用户名或密码错误（登录失败**统一提示此码**，不暴露用户名是否存在） |
| 用户 1xxx | `USER_DISABLED` | 1004 | 账号被禁用（status=0） |
| 电商 2xxx | `PRODUCT_NOT_FOUND` | 2001 | 商品不存在 |
| 电商 2xxx | `SKU_NOT_FOUND` | 2002 | SKU 不存在 |
| 电商 2xxx | `SKU_OFF_SHELF` | 2003 | 商品已下架 |
| 电商 2xxx | `STOCK_NOT_ENOUGH` | 2004 | 库存不足（并发抢光也算） |
| 电商 2xxx | `CART_ITEM_NOT_FOUND` | 2005 | 购物车条目不存在 / 非本人条目 |
| 电商 2xxx | `CART_EMPTY` | 2006 | 购物车为空（下单未带任何商品） |
| 电商 2xxx | `ORDER_NOT_FOUND` | 2007 | 订单不存在 / 非本人订单 |
| 电商 2xxx | `ORDER_STATUS_INVALID` | 2008 | 订单状态不符（如重复取消） |
| AI 3xxx | `AI_SERVICE_ERROR` | 3001 | AI 服务异常 |

### 5.4 全局异常 → 返回对照表

| 抛出的异常/情况 | 返回给前端 | 备注 |
|---|---|---|
| `BusinessException` | `R.fail(code, msg)` | 后端用 log.warn 记录 |
| Sa-Token `NotLoginException` | 401「未登录或登录已过期」 | 没带 token / token 失效 |
| `MethodArgumentNotValidException` | 400 + **第一条**字段错误（`字段名 + 提示`） | 入参校验不通过 |
| `MissingServletRequestParameterException` / `HttpMessageNotReadableException` | 400 | 缺参数 / 请求体不是合法 JSON |
| `NoResourceFoundException` | 404 | 路径不存在 |
| 其他 `Exception` | 500「服务器开小差了…」 | log.error 记堆栈，**不向前端泄漏细节** |

### 5.5 分页参数速查

| 名称 | 规则 | 公式 |
|---|---|---|
| `PageQuery.page` | 最小 1 | — |
| `PageQuery.pageSize` | 1..100 | — |
| `PageQuery.getOffset()` | — | `(page-1) * pageSize` |
| `PageResult<T>` | 结构 | `{records, total, page, pageSize}`，工厂 `PageResult.of(records, total, page, pageSize)` |

---

## 6. 接口速查（按业务域）

> 提示：以下所有路径都是完整路径（已含 `/api/v1`）。出参列只写 `R.data` 部分。

### 6.1 用户域 auth（`/api/v1/auth`）

| 操作 | 方法与路径 | 入参 | 出参（R.data） | 需登录 | 失败时 |
|---|---|---|---|---|---|
| 注册 | `POST /api/v1/auth/register` | body：`RegisterRequest{username, password, nickname?}`；username 必须匹配 `^[a-zA-Z0-9_]{3,20}$`，password 6~32 位，nickname 可选 | R（成功即注册） | **否**（白名单） | 1001 用户名已存在 |
| 登录 | `POST /api/v1/auth/login` | body：`LoginRequest{username, password}`（均必填 @NotBlank） | `LoginVO{token, userVO}`；`userVO=UserVO{id, username, nickname, avatar, role}` | **否**（白名单） | 1003 密码错误（统一）；1004 账号禁用 |
| 登出 | `POST /api/v1/auth/logout` | 无 | R | 是 | 401 |
| 我的信息 | `GET /api/v1/auth/me` | 无 | `UserVO{id, username, nickname, avatar, role}`（**不含 passwordHash**） | 是 | 401 |

> 用户域要点速查：注册成功默认 `role=0`、`status=1`；昵称为空自动生成「种草用户 + 4 位随机数」；密码 BCrypt 加密后入库；"查用户 → 比对密码"失败统一报 1003。

### 6.2 商品域 products（`/api/v1/products`）

| 操作 | 方法与路径 | 入参 | 出参（R.data） | 需登录 | 失败时 |
|---|---|---|---|---|---|
| 上架商品分页列表 | `GET /api/v1/products` | 查询参数：`page`(≥1)、`pageSize`(1..100) | `PageResult<商品列表项>`；列表项**不含** detail，含起售价 `min_price`（LEFT JOIN SKU 取 MIN(price)） | 是 | 401 |
| 商品详情 | `GET /api/v1/products/{id}` | 路径参数：`id` | 商品详情（含 detail 图文 + 该商品的全部 SKU） | 是 | 2001 / 401 |

> 商品域要点速查：列表/详情只出 `status=1`（上架）商品；详情查到后还会校验 `status==1`；列表 SQL 为 `WHERE status=1 + GROUP BY + LIMIT offset,size`。

### 6.3 购物车域 cart（`/api/v1/cart`）

| 操作 | 方法与路径 | 入参 | 出参（R.data） | 需登录 | 失败时 |
|---|---|---|---|---|---|
| 购物车列表 | `GET /api/v1/cart` | 无 | `CartItemVO[]`；`CartItemVO{id, skuId, skuName, price, quantity, productId, productName, mainImg}`，另有计算字段 `subtotal = price × quantity`；按更新时间倒序 | 是 | 401 |
| 加购 | `POST /api/v1/cart` | body：`AddCartRequest{skuId(必填 @NotNull), quantity(默认1, 1..99)}` | R | 是 | 2002 SKU 不存在 / 2003 已下架 / 2004 库存不足 |
| 改数量 | `PUT /api/v1/cart/{id}` | 路径参数：`id`；body：`UpdateCartRequest{quantity(1..99)}` | R | 是 | 2005 条目不存在或非本人 |
| 删条目 | `DELETE /api/v1/cart/{id}` | 路径参数：`id` | R | 是 | 2005 条目不存在或非本人 |
| 清空 | `DELETE /api/v1/cart` | 无 | R（清空当前用户全部购物车） | 是 | 401 |

> 购物车域要点速查：加购时同 SKU 已存在则**数量相加，封顶 99**；加购/改数量/删除的 SQL 都带 `userId` 条件，更新 0 行 = 非本人条目 → 2005（归属校验下沉 SQL）。

### 6.4 订单域 orders（`/api/v1/orders`）

| 操作 | 方法与路径 | 入参 | 出参（R.data） | 需登录 | 失败时 |
|---|---|---|---|---|---|
| 创建订单 | `POST /api/v1/orders` | body：`CreateOrderRequest{items[](非空 @NotEmpty), receiverName(必填), receiverPhone(必填), receiverAddress(必填)}`；`items` 内每项 `OrderItemRequest{skuId(必填), quantity(1..99)}` | R（下单成功；订单状态 PENDING_PAY） | 是 | 2006 购物车为空 / 2002 / 2003 / 2004 库存不足 |
| 我的订单分页 | `GET /api/v1/orders` | 查询参数：`page`、`pageSize` | `PageResult<OrderVO>`（按创建时间倒序，带 userId 校验归属） | 是 | 401 |
| 订单详情 | `GET /api/v1/orders/{id}` | 路径参数：`id` | `OrderVO`（含明细 items；`BeanUtils.copyProperties(order, vo)` 拷贝主表字段） | 是 | 2007 不存在或非本人 |
| 取消订单 | `POST /api/v1/orders/{id}/cancel` | 路径参数：`id` | R（成功则回补库存） | 是 | 2007 非本人 / 2008 状态不符（非待支付，如重复取消） |

> 订单域要点速查：创建订单整个流程在一个事务里（扣库存 → 写订单 → 写明细 → 清购物车，任一失败全部回滚）；订单明细里的商品名/规格名/单价是**下单时快照**；取消只允许 `PENDING_PAY` 状态。

### 6.5 AI 域 chat（`/api/v1/chat`）

| 操作 | 方法与路径 | 入参 | 出参（R.data） | 需登录 | 失败时 |
|---|---|---|---|---|---|
| 新建会话 | `POST /api/v1/chat/conversations` | body **可空**：`ChatTitleRequest{title?}` | 新会话（当前可空 body 创建） | 是 | 401 |
| 会话列表 | `GET /api/v1/chat/conversations` | 无 | 会话列表（新 → 旧） | 是 | 401 |
| 会话消息列表 | `GET /api/v1/chat/conversations/{id}/messages` | 路径参数：`id` | 消息列表（旧 → 新） | 是 | 404 会话不存在或非本人 |
| 普通问答 | `POST /api/v1/chat` | body：`ChatRequest{message(必填 @NotBlank), conversationId?(可空)}` | `R<String>`，data = 助手回答文本 | 是 | 404 会话不存在 / 3001 AI 服务异常 |
| 流式问答 | `POST /api/v1/chat/stream` | body：`ChatRequest{message(必填), conversationId?(可空)}` | **SSE 流**：`produces = text/event-stream`，返回 `Flux<String>`（逐段输出助手回答） | 是 | 404 会话不存在 / 3001（流中断时而非裸断） |

> AI 域要点速查：`conversationId` 为空时自动新建会话，**标题取问题前 20 字**；SSE 流程为「订阅时先存用户问题 → 流结束存完整回答 → 出错返回兜底文案 `\n\n[AI 服务暂时不可用，请稍后再试]`」；归属校验失败返回 404「会话不存在」；AI 回答遵守预置 system prompt：**基于商品库如实回答，没有就坦诚说不知道，不编造**。

---

## 7. 数据表速查（8 张表）

> 来源：`sql/init.sql` 真实建表脚本（MySQL 8，InnoDB，utf8mb4）。表头约定：PK=主键、UK=唯一索引、KEY=普通索引、"字段"列注释均取自建表 COMMENT。

### 7.1 t_user 用户表

| 字段 | 类型 | 键/索引 | 注释 |
|---|---|---|---|
| id | BIGINT NOT NULL AUTO_INCREMENT | PK | 主键 |
| username | VARCHAR(50) NOT NULL | UK `uk_username` | 登录名（唯一） |
| password_hash | VARCHAR(100) NOT NULL | — | BCrypt 密码哈希 |
| nickname | VARCHAR(50) NULL | — | 昵称 |
| avatar | VARCHAR(255) NULL | — | 头像 URL |
| role | TINYINT NOT NULL DEFAULT 0 | — | 身份：0 普通用户 / 1 商家 |
| status | TINYINT NOT NULL DEFAULT 1 | — | 状态：1 正常 / 0 禁用 |
| created_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | — | 创建时间 |

### 7.2 t_product 商品表（SPU）

| 字段 | 类型 | 键/索引 | 注释 |
|---|---|---|---|
| id | BIGINT NOT NULL AUTO_INCREMENT | PK | 主键 |
| spu_name | VARCHAR(150) NOT NULL | — | 商品名称（SPU） |
| sub_title | VARCHAR(255) NULL | — | 副标题 / 卖点 |
| category_id | BIGINT NULL | — | 分类 id（V1 简化，不建分类表） |
| main_img | VARCHAR(255) NULL | — | 主图 URL |
| detail | TEXT NULL | — | 图文详情/描述（V1 AI 问答的预置知识来源） |
| status | TINYINT NOT NULL DEFAULT 1 | KEY `idx_status` | 状态：1 上架 / 0 下架 |
| created_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | — | 创建时间 |

### 7.3 t_product_sku 商品 SKU 表

| 字段 | 类型 | 键/索引 | 注释 |
|---|---|---|---|
| id | BIGINT NOT NULL AUTO_INCREMENT | PK | 主键 |
| product_id | BIGINT NOT NULL | KEY `idx_product` (product_id) | 所属商品 id |
| sku_name | VARCHAR(100) NOT NULL | — | 规格名（如 曜石黑 32G） |
| price | DECIMAL(10,2) NOT NULL | — | 售价 |
| stock | INT NOT NULL DEFAULT 0 | — | 库存 |
| sales | INT NOT NULL DEFAULT 0 | — | 销量 |
| version | INT NOT NULL DEFAULT 0 | — | 乐观锁版本号（V3 秒杀/防超卖使用，预留） |
| created_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | — | 创建时间 |

### 7.4 t_cart 购物车表（V1 MySQL 版）

| 字段 | 类型 | 键/索引 | 注释 |
|---|---|---|---|
| id | BIGINT NOT NULL AUTO_INCREMENT | PK | 主键 |
| user_id | BIGINT NOT NULL | UK `uk_user_sku` (user_id, sku_id) | 用户 id |
| sku_id | BIGINT NOT NULL | 同上（联合唯一） | SKU id |
| quantity | INT NOT NULL DEFAULT 1 | — | 数量 |
| created_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | — | 创建时间 |
| updated_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | — | 更新时间（修改时自动刷新） |

> 注意：`uk_user_sku(user_id, sku_id)` 保证同一用户同一 SKU 只有一行 —— 这就是"同 SKU 加购变相加数量"的数据库依据。

### 7.5 t_order 订单主表

| 字段 | 类型 | 键/索引 | 注释 |
|---|---|---|---|
| id | BIGINT NOT NULL AUTO_INCREMENT | PK | 主键 |
| order_no | VARCHAR(32) NOT NULL | UK `uk_order_no` | 订单号（业务唯一） |
| user_id | BIGINT NOT NULL | KEY `idx_user_created` (user_id, created_at) | 用户 id |
| total_amount | DECIMAL(10,2) NOT NULL | — | 订单总金额 |
| status | VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAY' | — | 状态机：PENDING_PAY 待支付 / PAID 已支付 / SHIPPED 已发货 / COMPLETED 已完成 / CANCELLED 已取消 |
| receiver_name | VARCHAR(50) NULL | — | 收货人 |
| receiver_phone | VARCHAR(20) NULL | — | 收货电话 |
| receiver_address | VARCHAR(255) NULL | — | 收货地址 |
| pay_type | VARCHAR(20) NULL | — | 支付方式（V2 沙箱支付后启用：ALIPAY / WECHAT） |
| pay_time | DATETIME NULL | — | 支付时间 |
| cancel_time | DATETIME NULL | — | 取消时间 |
| version | INT NOT NULL DEFAULT 0 | — | 乐观锁版本号 |
| created_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | — | 创建时间 |

### 7.6 t_order_item 订单明细表（快照冗余）

| 字段 | 类型 | 键/索引 | 注释 |
|---|---|---|---|
| id | BIGINT NOT NULL AUTO_INCREMENT | PK | 主键 |
| order_id | BIGINT NOT NULL | KEY `idx_order` (order_id) | 订单 id |
| sku_id | BIGINT NOT NULL | — | SKU id |
| product_name | VARCHAR(150) NOT NULL | — | 商品名**快照** |
| sku_name | VARCHAR(100) NOT NULL | — | 规格名**快照** |
| price | DECIMAL(10,2) NOT NULL | — | 成交单价**快照** |
| quantity | INT NOT NULL | — | 数量 |

### 7.7 t_conversation AI 会话表

| 字段 | 类型 | 键/索引 | 注释 |
|---|---|---|---|
| id | BIGINT NOT NULL AUTO_INCREMENT | PK | 主键 |
| user_id | BIGINT NOT NULL | KEY `idx_user` (user_id) | 用户 id |
| biz_type | VARCHAR(20) NOT NULL DEFAULT 'CHAT' | — | 会话类型：CHAT 通用 / CHAT_GOODS 商品问答（V2 起扩展 SHOPPING 导购） |
| title | VARCHAR(100) NULL | — | 会话标题 |
| created_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | — | 创建时间 |

### 7.8 t_message AI 会话消息表

| 字段 | 类型 | 键/索引 | 注释 |
|---|---|---|---|
| id | BIGINT NOT NULL AUTO_INCREMENT | PK | 主键 |
| conversation_id | BIGINT NOT NULL | KEY `idx_conversation` (conversation_id) | 会话 id |
| role | VARCHAR(20) NOT NULL | — | 角色：user / assistant |
| content | TEXT NULL | — | 消息内容 |
| extra_json | TEXT NULL | — | 扩展信息（V2 起：引用来源/商品卡片，预留） |
| created_at | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | — | 创建时间 |

**8 张表一句话记忆：**

| 域 | 表 | 一句话 |
|---|---|---|
| 用户 | t_user | 账号 + BCrypt 密码 + 角色/状态 |
| 电商 | t_product | 商品（SPU），detail 是 AI 知识来源 |
| 电商 | t_product_sku | 规格 + 价格 + 库存（防超卖扣减目标） |
| 电商 | t_cart | 用户×SKU 唯一，数量可叠加 |
| 电商 | t_order | 订单主表 + 状态机 + 收货信息 |
| 电商 | t_order_item | 明细快照（名字/价格定格在下单时） |
| AI | t_conversation | 会话 |
| AI | t_message | 消息（user / assistant 成对持久化） |

---

## 8. 常量与状态速查

### 8.1 订单状态机

| 常量 | 含义 | 说明 |
|---|---|---|
| `PENDING_PAY` | 待支付 | 下单默认状态（DB 默认值） |
| `PAID` | 已支付 | V1 未接支付，状态预留 |
| `SHIPPED` | 已发货 | 预留 |
| `COMPLETED` | 已完成 | 预留 |
| `CANCELLED` | 已取消 | 唯一可迁移路径：`PENDING_PAY → CANCELLED`（V1 只实现这一步） |

> 取消迁移带 CAS 条件：`WHERE status='PENDING_PAY'`，更新 0 行 = 状态不符 → 2008；取消成功才逐条回补 SKU 库存；更新时 `version` 字段 +1（乐观锁痕迹，V3 秒杀强化用）。

### 8.2 角色与状态取值

| 枚举位置 | 取值 | 含义 |
|---|---|---|
| t_user.role | 0 | 普通用户（注册默认） |
| t_user.role | 1 | 商家（建表注释定义） |
| t_user.status | 1 | 正常（注册默认） |
| t_user.status | 0 | 禁用（登录时 → 1004 USER_DISABLED） |
| t_product.status | 1 | 上架（列表/详情只出上架商品） |
| t_product.status | 0 | 下架（加购/下单校验 → 2003 SKU_OFF_SHELF） |

### 8.3 AI 域常量

| 常量 | 值 | 含义 |
|---|---|---|
| `Conversation.BIZ_CHAT` | `CHAT` | 通用会话类型（表默认值） |
| `Message.ROLE_USER` | `user` | 用户消息 |
| `Message.ROLE_ASSISTANT` | `assistant` | 助手消息 |
| 会话标题 | 问题前 20 字 | conversationId 为空自动建会话时取 |
| SSE 失败兜底文案 | `\n\n[AI 服务暂时不可用，请稍后再试]` | 流出错时发给前端，防止 SSE 裸断 |

### 8.4 业务数值限制速查

| 规则 | 值 |
|---|---|
| 用户名 | `^[a-zA-Z0-9_]{3,20}$`（3~20 位字母/数字/下划线） |
| 密码 | 6~32 位 |
| 购物车/订单条目数量 | `quantity` 1..99（加购同 SKU 累加也封顶 99） |
| 分页 pageSize | 1..100 |
| 分页 offset 公式 | `(page-1) * pageSize` |
| 默认昵称 | 「种草用户」+ 4 位随机数字 |
| 订单号规则 | `yyyyMMddHHmmssSSS`（17 位时间戳）+ 4 位随机 + `userId % 1000`，唯一性由 `uk_order_no` 兜底 |
| 小计公式 | `CartItemVO.subtotal = price × quantity` |
| 金额类型 | 全程 `BigDecimal`（DB 用 DECIMAL(10,2)），**禁用 double** |

### 8.5 配置类常量速查（config 包）

| 配置 | 关键值 |
|---|---|
| Sa-Token | token 请求头名 `Authorization`；有效期 `timeout=604800` 秒（7 天）；`active-timeout=-1`（不活跃不过期）；`is-concurrent=true`（允许并发登录）；`is-share=false`；`token-style=uuid`；`is-log=false` |
| 拦截规则 | 拦截 `/api/**`；白名单 `/api/v1/auth/login`、`/api/v1/auth/register`、`/error` |
| CORS | 允许来源 `http://localhost:*`、`http://127.0.0.1:*`；方法 GET/POST/PUT/DELETE/OPTIONS；`allowCredentials(true)`；`maxAge=3600` |
| 密码 | Bean `PasswordEncoder` = `new BCryptPasswordEncoder()` |
| AI | 模型 `deepseek-v4-pro`；`temperature=0.7`；base-url `https://opencode.ai/zen/go`（不带 `/v1`，Spring AI 自动拼 `/v1/chat/completions`）；api-key 取 `${DEEPSEEK_API_KEY:sk-...}` |
| Jackson | 日期格式 `yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai` |
| MyBatis | mapper-locations `classpath:mapper/**/*.xml`；`map-underscore-to-camel-case=true`；日志 `StdOutImpl` |
| 服务端口 | `8080` |

---

## 9. 关键 SQL 速查（并发/一致性）

> 这几条 SQL 是全项目"防超卖/防重复"的硬核条目，查代码时直接对照。

| 目的 | SQL（要点） | 返回值含义 |
|---|---|---|
| 扣库存（防超卖核心） | `UPDATE t_product_sku SET stock=stock-?, sales=sales+? WHERE id=? AND stock>=?` | 返回 **0 行 = 并发下库存不足 → 2004 STOCK_NOT_ENOUGH**；数据库行锁 + 条件判断原子完成 |
| 回补库存 | `addStock`（ProductSkuMapper，取消订单时逐条调用） | — |
| 取消订单（状态机 CAS） | `UPDATE t_order SET status=? WHERE id=? AND status='PENDING_PAY'`（cancel_time 回填，version+1） | 返回 0 行 = 状态不符（防并发重复取消）→ 2008 |
| 商品分页列表 | `LEFT JOIN SKU` + `MIN(price) AS min_price`，`WHERE status=1`，`GROUP BY` 全列，`LIMIT offset,size` | 每商品一个"起售价" |
| 购物车列表 | `JOIN sku + product` 一次查出展示字段，`ORDER BY updated_at DESC` | — |

> 一致性记忆点（只查不改）：内存预检库存只是为了提前给友好报错，**真正的防线是带 `stock>=?` 条件的那条 UPDATE**；扣库存→写订单→写明细→清购物车**全在一个 `@Transactional(rollbackFor=Exception.class)` 事务里**，任一失败全部回滚。

---

## 10. 种子数据速查

> 初始化数据库后自带 4 个演示商品、8 个 SKU；`t_product.detail` 为种草图文，是 **AI 问答的预置知识来源**。

### 10.1 商品（t_product）

| id | 商品名 | 副标题 | 分类 id | 价格范围 | SKU 数 |
|---|---|---|---|---|---|
| 1 | AirSound Pro 真无线降噪耳机 | 主动降噪 + 36小时续航 + 蓝牙5.3 | 101 | ¥399 | 2 |
| 2 | 闪充宝 65W 氮化镓充电器 | 小体积大功率，兼容手机/笔记本 | 102 | ¥89~129 | 2 |
| 3 | 云朵亲肤保湿唇釉 | 水感质地不拔干，显白豆沙色 | 103 | ¥69 | 2 |
| 4 | 轻氧 智能手环 6 | 1.62寸AMOLED屏，血氧心率监测，14天续航 | 104 | ¥199~249 | 2 |

### 10.2 SKU（t_product_sku）

| id | 所属商品 id | 规格名 | 单价 | 初始库存 | 初始销量 |
|---|---|---|---|---|---|
| 1 | 1 | 曜石黑 | 399.00 | 1000 | 356 |
| 2 | 1 | 奶白色 | 399.00 | 800 | 289 |
| 3 | 2 | 单头版 | 89.00 | 500 | 1200 |
| 4 | 2 | 三口套装版 | 129.00 | 300 | 680 |
| 5 | 3 | 豆沙色 | 69.00 | 1500 | 2330 |
| 6 | 3 | 蜜桃色 | 69.00 | 1200 | 1890 |
| 7 | 4 | 标准版 | 199.00 | 900 | 760 |
| 8 | 4 | 表带礼盒版 | 249.00 | 400 | 320 |

---

## 11. 常见报错对照速查

> 调用接口时看到什么返回，直接按 code 查。

| 返回 code / 现象 | 含义 | 下一步怎么办 |
|---|---|---|
| 200 | 成功 | 读 data 即可 |
| 400 + 字段名 | 参数校验不通过（格式/必填/范围） | 按提示检查入参：用户名 3~20 位字母数字下划线、密码 6~32 位、quantity 1..99、pageSize 1..100 |
| 401「未登录或登录已过期」 | 没带或带错 token | 先登录拿 token，请求头加 `Authorization` |
| 404 | 路径不存在，或「会话不存在」 | 检查 URL；AI 消息接口确认 conversationId 属于当前登录用户 |
| 500「服务器开小差了…」 | 未捕获异常 | 看后端控制台日志（log.error 堆栈）定位 |
| 1001 | 用户名已存在 | 换一个用户名注册 |
| 1003 | 用户名或密码错误 | 检查登录入参；注意系统**故意不区分**用户名不存在与密码错误 |
| 1004 | 账号被禁用 | 联系管理员/后端改 `t_user.status=1` |
| 2001 / 2002 | 商品 / SKU 不存在 | 核对 id 是否写错 |
| 2003 | 商品已下架 | 换商品或刷新列表 |
| 2004 | 库存不足 | 减少数量或等补货（并发抢光也会报这个） |
| 2005 | 购物车条目不存在 / 非本人条目 | 只能操作自己购物车里的条目 |
| 2006 | 购物车为空 | 下单请求的 items 没带任何条目 |
| 2007 | 订单不存在 / 非本人订单 | 只能查/取消自己的订单 |
| 2008 | 订单状态不符 | 该订单不是待支付状态（如已取消），刷新再看 |
| 3001 | AI 服务异常 | 稍后重试；`onErrorResume` 会给 SSE 流补兜底文案 |

---

> **手册到此结束。** 使用口诀：启动看第 3 节、接口查第 6 节、表结构翻第 7 节、
> 状态和常量认准第 8 节、一切错误码回第 5/11 节。祝查询顺利，不迷路。