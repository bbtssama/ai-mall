# ai-mall 商城 · 手把手教程（L2 SSM 初级版）

> 本教程带一位**懂 SSM / Spring Boot 基础**的初级开发者，把 ai-mall 后端源码
> 按依赖顺序逐层「读」懂——不是泛泛而谈架构图，而是实实在在打开每个文件，
> 知道 **该从哪看起、看什么、想什么**。
>
> 前置水平：你写过 SSM（Spring + SpringMVC + MyBatis）或 Spring Boot 小项目，
> 知道 Controller / Service / Mapper 大概是什么，听说过依赖注入、注解、事务。
> 你不必已经会 Spring AI、Sa-Token、Flux——本教程一边读一边把差异点讲透。
>
> 建议：旁边开着代码（IDE 打开 `ai-mall` 项目），跟着每一课的「读代码路线」实际点进去看。

---

## 目录

- [第 0 课 拿到一坨源码，第一步先干什么](#第-0-课-拿到一坨源码第一步先干什么)
- [第 1 课 启动类 AimallApplication：程序从哪一行活过来](#第-1-课-启动类-aimallapplication程序从哪一行活过来)
- [第 2 课 config 包：四张「全局配置单」](#第-2-课-config-包四张全局配置单)
- [第 3 课 common 包：统一返回与全局异常的战备值班室](#第-3-课-common-包统一返回与全局异常的战备值班室)
- [第 4 课 user 域：注册登录，SSM 迁移者的第一课](#第-4-课-user-域注册登录ssm-迁移者的第一课)
- [第 5 课 goods 域：商品列表/详情，第一个「JOIN + 分页」的 Mapper XML](#第-5-课-goods-域商品列表详情第一个-join-分页的-mapper-xml)
- [第 6 课 order 域：下单防超卖——真正的硬核代码](#第-6-课-order-域下单防超卖真正的硬核代码)
- [第 7 课 ai 域：Spring AI + SSE 流式的开眼课](#第-7-课-ai-域spring-ai-sses-流式的开眼课)
- [第 8 课 自己的尝试：按这套读法，去读购物车](#第-8-课-自己的尝试按这套读法去读购物车)
- [第 9 课 给你一份「SSM → 本项目」差异速查表](#第-9-课-给你一份-ssm--本项目差异速查表)
- [结课：这套代码的阅读心法总结](#结课这套代码的阅读心法总结)

---

## 第 0 课 拿到一坨源码，第一步先干什么

### 本课目标

学会拿到一个陌生 Spring Boot 项目后，按什么顺序「先扫一眼再深挖」，
不迷路、不按 .git 顺序瞎翻。

### 读代码路线（先看这里）

**第一步：看目录树**（先在地图层面定你的坐标）。重点看这个约定：

```
backend/src/main/
├── java/com/aimall/
│   ├── AimallApplication.java      # 启动类
│   ├── common/                     # 通用：R / ResultCode / 异常 / 分页
│   ├── config/                     # 全局配置：AI / 密码 / 鉴权 / CORS
│   ├── user/                       # 会员域
│   ├── goods/                      # 商品域
│   ├── order/                      # 订单域
│   └── ai/                         # AI 域
└── resources/
    ├── application.yml
    └── mapper/*.xml                # 8 个手写 XML：User/Cart/Product/ProductSku/
                                     #   Order/OrderItem/Conversation/Message
```

**你想什么（关键心法）：**
1. **分层是「按业务域切」，不是按技术层切。** 很多 SSM 项目是 `controller/ service/ mapper/`
   三大平铺包（所有 Controller 在一堆，所有 Service 在一堆）。**这里反过来了**：
   以业务域 `user` `goods` `order` `ai` 为顶层，每个域内部再分 `bean/controller/dto/mapper/service/service/impl`。
   这更贴近真实微服务演进（以后一个域长成一个服务）。读代码时「按域读」，别跨域跳。
2. **resources 下手写 XML Mapper**：没有 MyBatis-Plus、没有 PageHelper——SQL 全是手写 XML。
   这跟很多「XXBoot 全家桶」项目不一样，你得找回 SSM 时代手写 SQL 的手感。
3. **`common` 和 `config` 是所有域的「地基」**：按照 **启动类 → config → common → 业务域** 的顺序读，
   因为业务域的每个文件都依赖它们。这就是本教程的阅读顺序依据。

**动手：** 打开 IDE，把上面这颗目录树和你项目里真实的树对照一遍，确认每个字母都对得上。

---

## 第 1 课 启动类 AimallApplication：程序从哪一行活过来

### 本课目标

说清楚 Spring Boot 到底从哪「点火」，以及一个反直觉点：**为什么没有 `@MapperScan`**。

### 读代码路线

打开 `AimallApplication.java`。核心就一件事：

```java
@SpringBootApplication
public class AimallApplication {
    public static void main(String[] args) {
        SpringApplication.run(AimallApplication.class, args);
    }
}
```

**关注点：**
- `@SpringBootApplication` = `@SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan` 三合一。
  从前只是背答案，现在请盯着它想：**ComponentScan 扫的是哪个包？** 是 `com.aimall`——
  所以启动类必须放在 `com.aimall` 顶层，才能扫到 `user/goods/order/ai/config/common` 全部子包。
- **没有 `@MapperScan`（本教程第一个差异点，务必记住）**。为什么？

### 与 SSM 的差异点：Mapper 怎么被找到的？

传统 SSM 你在启动类或配置里写 `@MapperScan("com.xxx.mapper")`，让框架一次性扫描所有 Mapper 接口。
**本项目反着来**：每个 Mapper 接口头顶自己戴一顶 `@Mapper` 小帽子，逐接口注册。

```java
@Mapper                 // 我自愿申报：我是一个 Mapper 接口
public interface UserMapper {
    User selectByUsername(String username);
    ...
}
```

**思想（读代码时在脑子里问）：**
- `@MapperScan` = 单位统一给所有部门发工牌；逐接口 `@Mapper` = 每个员工自己戴着工牌来报到。
- 这样做的代价是加新 Mapper 不能忘写 `@Mapper`（忘了就注入失败报「找不到 bean」）；
  好处是**每一个 Mapper 的存在都是显式的**，IDE、同事都能一眼看到，也方便按域拆分模块时
  不靠字符串扫描路径钩在一起。面试可聊「扫描路径 vs 显式声明」。

**你先记住的结论：** 启动类几乎「没内容」，真正的魔法都在后面各包。
继续往下读，你会看到每一个「员工」（Bean）是怎么被装配的。

---

## 第 2 课 config 包：四张「全局配置单」

### 本课目标

读懂 `config` 包四张配置单在配什么。它们是全项目「依赖注入订单」的源头。

### 读代码路线

打开 `config/` 下四个类，逐个看：

| 类 | 配了什么 | 一句话记住 |
|---|---|---|
| `AiConfig` | `@Bean ChatClient` | 把 Spring AI 的「会和 AI 对话的职员」造出来 |
| `PasswordConfig` | `@Bean PasswordEncoder = new BCryptPasswordEncoder()` | 全项目统一的密码加密器 |
| `SaTokenConfig` | 注册 `SaInterceptor`，拦截 `/api/**` | 全网关卡口：谁进来要先过安检 |
| `WebConfig` | CORS 规则 | 允许浏览器跨端口访问（5173→8080） |

**关注点逐个拆：**

1. **AiConfig**：`@Bean ChatClient chatClient(ChatClient.Builder builder)`。
   这是 Spring AI 的产物——你注入了官方 `ChatClient.Builder`，项目里就能到处注入 `ChatClient` 去和模型聊。
   它是「接口——由 Spring Boot 自动配置」的典型：**你没有 new，框架按约定给你**。
2. **PasswordConfig**：全项目共用 `BCryptPasswordEncoder`。项目里任何地方加密码，
   注入的都长一模一样，保证加密算法全局一致（这避免」各处各自的 Salt 版本不同」的坑）。
3. **SaTokenConfig**（与 SSM 差异巨大的地方，重点）：它实现了 `WebMvcConfigurer`，
   里面注册了 `SaInterceptor`，拦截规则是 **`/api/**`**——几乎全接口都拦，
   但白名单只放行三个：`/api/v1/auth/login`、`/api/v1/auth/register`、`/error`。
   **这就是"第 1 课 L1 教程里那个安检门"的源头。**
4. **WebConfig**：CORS 允许 `http://localhost:*`、`http://127.0.0.1:*`。为什么？因为前端在 5173，
   后端在 8080，浏览器跨域默认不给发请求——这份配置就是解锁跨域访问的。

### 与 SSM 的差异点：Sa-Token vs Session

传统 SSM 用 **Session**（服务端存会话 + Cookie 带 sessionId，天然「有状态」）。
本项目用 **Sa-Token 无状态 Token**：

| | Session（SSM 常见） | Sa-Token（本项目） |
|---|---|---|
| 状态存哪 | 服务端内存 | 客户端拿 token，服务端无 Tomcat Session |
| 签发 | 框架自动建 | `StpUtil.login(id)` 显式签发 |
| 校验 | 拦截器拿 Session | 拦截器查请求头里的 token |
| 分布式扩展 | 麻烦（多台机 Session 不同步） | 天然适合多实例 |
| 返回给前端 | 浏览器自动带 Cookie | 请求头 `Authorization: <token>` 显式带 |

**读代码时在脑子里问**：`StpUtil` 从哪把「当前用户 id」取出来？
答案是——它回溯请求头里的 `Authorization` token，翻译回 userId。
你之后会反复看到 `StpUtil.getLoginIdAsLong()` 这样一行代码拿到「我是谁」。

---

## 第 3 课 common 包：统一返回与全局异常的战备值班室

### 本课目标

读懂为什么「所有接口返回都长一个样」，以及「出错了为什么前端永远看到同一模板」。这是 SSM 项目很少做完整的部分，也是本项目要你重点学的工程习惯。

### 读代码路线

`common/` 下文件：`R` / `ResultCode` / `BusinessException` / `GlobalExceptionHandler` / `PageQuery` / `PageResult`。
按「数据 → 异常 → 兜底」逻辑读。

**① `R<T>` —— 所有接口的"标准信封"**

```java
public class R<T> {
    private int code;
    private String msg;
    private T data;
}
```

静态工厂（重点记方法签名）：
- `R.ok()` 成功无数据
- `R.ok(T data)` 成功带数据
- `R.ok(String msg, T data)` 成功带提示+数据
- `R.fail(String msg)` 失败，默认 500
- `R.fail(ResultCode)` / `R.fail(int code, String msg)` 失败带具体码

**⚠️ 项目故意踩过的坑（必看，这就是为什么要用这套）**：
早期版本同时有 `R.ok(String)` 和泛型 `R.ok(T)` 两个重载。
当代码写 `R.ok(chatService.chat(req))` 且 `chat` 返回 `String` 时，
Java 重载决议会把 `String` 匹配到 `R.ok(String)`（当成"提示语"）而不是泛型 `T`，
结果就是 **data 丢了**。所以本项目**故意删掉单参 `R.ok(String)`**，只剩 `ok(T)` 和 `ok(String,T)`——
从根源杜绝这种模棱两可。

**② `ResultCode` —— 错误码的"官方法典"**

按域分段，读的时候建立"段→含义"的直觉：
- 通用 200/400/401/403/404/500
- 用户域 1xxx：`USERNAME_EXISTS 1001` `USER_NOT_FOUND 1002` `PASSWORD_ERROR 1003` `USER_DISABLED 1004`
- 电商域 2xxx：`PRODUCT_NOT_FOUND 2001` `SKU_NOT_FOUND 2002` `SKU_OFF_SHELF 2003`
  `STOCK_NOT_ENOUGH 2004` `CART_ITEM_NOT_FOUND 2005` `CART_EMPTY 2006`
  `ORDER_NOT_FOUND 2007` `ORDER_STATUS_INVALID 2008`
- AI 域 3xxx：`AI_SERVICE_ERROR 3001`

**思想：** 用枚举而不是散落的魔法数字。好处是代码里写的是 `throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH)`，
语义一目了然，且全项目码值不会撞（按域分段）。

**③ `BusinessException` —— 带 code 的运行时异常**

它 extends `RuntimeException`，持有 `code`。构造方式：
`(String)` 默认 500 / `(ResultCode)` / `(ResultCode, String)` / `(int, String)`。
业务层想"拒绝这次操作"时，就 `throw new BusinessException(...)`。

**④ `GlobalExceptionHandler` —— 全局兜底（真正体现工程水平的地方）**

用 `@RestControllerAdvice` 声明，集中处理所有「漏出来的异常」，分工如下：

| 异常 | 转成 |
|---|---|
| `BusinessException` | `R.fail(code,msg)`，记 `log.warn` |
| Sa-Token `NotLoginException` | 401 「未登录或登录已过期」 |
| `MethodArgumentNotValidException` | 400 + 第一条字段错误 `field + message` |
| 缺参数 / 请求体不可读 | 400 |
| `NoResourceFoundException` | 404 |
| 兜底 `Exception` | 500 「服务器开小差了…」，`log.error` 记堆栈（**不把内部异常泄漏给前端**） |

**与 SSM 的差异点：** 很多 SSM 项目要么到处 try-catch 各管各，要么完全不做全局异常——
前端拿到的是千奇百怪的报错。这里把所有出口统一到 `GlobalExceptionHandler`，
业务代码里只需 `throw`，完全不需要管"怎么回给前端"。

**⑤ `PageQuery` / `PageResult` —— 手写分页**

`PageQuery{page(min=1), pageSize(1..100)}` + `getOffset()=(page-1)*pageSize`。
`PageResult<T>.of(records, total, page, pageSize)`。
项目**不引分页插件**，直接算 offset，Mapper XML 里手写 `LIMIT offset, size`。

> 读完这课请自己说一遍：为啥所有接口都要用 `R` 包一层？——为了前端/调用方
> 无论成功失败都拿到同一套格式，失败也能看到规范化的 `code`。
> 想更深入，去读 `Reference` 手册的 R/ResultCode 表。

---

## 第 4 课 user 域：注册登录，SSM 迁移者的第一课

### 本课目标

把 user 域从 Controller 到 Mapper 全部读通。它是全项目最典型的「三层结构」，最适合先建立完整印象。

### 读代码路线

**① 先读 Controller：`user/controller/AuthRestController`**

```
@RestController
@RequestMapping("/api/v1/auth")
```

方法（记路径与用途）：
- `POST /register` —— 参数 `@Valid RegisterRequest`
- `POST /login`
- `POST /logout`
- `GET /me`

**你想什么：** 为什么是 `AuthRestController` 而非 `UserController`？
因为这里只管"认证动作"（register/login/logout/me），而用户信息的 CRUD 不在这 ——
这提示你：**Controller 按"对外动作"命名，不规范叫 CRUD 全能王**。

**② 看 DTO 校验规则：`dto/RegisterRequest`**

记住校验注解（不满足回 400）：
- `username`：`^[a-zA-Z0-9_]{3,20}$`（3-20 位字母数字下划线）
- `password`：6-32 位
- `nickname`：可选

`LoginRequest`：username/password 都 `@NotBlank`。

**③ 核心逻辑：`service/impl/UserServiceImpl`**

`register`（跟第 1 课 L1 的操作对应）流程：
1. **查重**：`userMapper.selectByUsername`，存在则 `throw BusinessException(USERNAME_EXISTS)`。
   数据库还有 `uk_username` 唯一索引兜底——**代码查重 + 数据库唯一索引**双层保险。
2. **BCrypt 编码**：`passwordEncoder.encode(raw)` —— 绝不存明文。
3. **补默认值**：不填昵称 → 默认「种草用户+4位随机数字」；`role=0` `status=1`。
4. **insert**：`userMapper.insert(user)`，`useGeneratedKeys` 回填 `id`（注意看 Mapper XML 的这个属性）。

`login` 流程（记死这个顺序）：
1. `selectByUsername`；不存在 → 也抛 **`PASSWORD_ERROR`**（不暴露用户名是否存在，防枚举）
2. `passwordEncoder.matches(raw, hash)` 比对，不对 → `PASSWORD_ERROR`
3. 看 `status==0` → `USER_DISABLED`
4. `StpUtil.login(user.getId())` —— **签发 token**
5. 返回 `LoginVO(token, userVO)`（userVO **不含 passwordHash**）

`logout`：`StpUtil.logout()`。`me`：`StpUtil.getLoginIdAsLong()` → `selectById` → `toVO`。

**④ 看 Mapper 接口与 XML（找手感）：`user/mapper/UserMapper` + `resources/mapper/User.xml`**

关注：`selectByUsername`（单查）、`insert`（useGeneratedKeys 回填主键）、`selectById`。
对照 XML 里几个 `<result>` 列与驼峰命名——看看 `map-underscore-to-camel-case: true` 生效的地方。

**⑤ 构造器注入：`service/impl/` 顶部的注入方式**

每看一个 Service 实现，先看它头顶：

```java
@Service
@RequiredArgsConstructor           // ← Lombok：自动生成含 final 字段的构造器
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
}
```

### 与 SSM 的差异点：构造器注入 vs 字段注入

SSM 常见 `@Autowired private UserMapper xxx;`（字段注入）。
本项目统一：`@RequiredArgsConstructor` + `private final`（构造器注入）：

- **final 字段** → 谁必须注入，由类型系统保证（漏注入直接编译不过/启动失败），而不是运行时才炸。
- **对象不可变** → 字段一旦注入不可再被改。
- **好测** → 构造参数一目了然，单测 new 时手动传 mock 即可。
- **循环依赖**直接在启动期爆出来，而不是运行时表现怪异。

> 把这条当作本项目所有 Service 的「默认姿势」，读到后面每个域都长这样。

---

## 第 5 课 goods 域：商品列表/详情，第一个「JOIN + 分页」的 Mapper XML

### 本课目标

读 goods 域，重点是那个 **LEFT JOIN + MIN(price) + GROUP BY + LIMIT** 的列表 SQL——它是本项目手写 XML 的画龙点睛之作。

### 读代码路线

**① 先读 `goods/controller/ProductRestController`**

```
GET /api/v1/products/        → 分页上架商品（参数 PageQuery）
GET /api/v1/products/{id}    → 商品详情（带 SKU 列表）
```

`/{id}` 没写错——它俩**不是一个前缀**：列表是 `/products/`，详情是 `/products/{id}`。

**② 核心 SQL：`resources/mapper/Product.xml` 的 `selectOnSalePage`**

这是一个值得你反复看的 SQL。通读后逐段对：

```sql
SELECT p.*, MIN(s.price) AS min_price, ...
FROM t_product p
LEFT JOIN t_product_sku s ON s.product_id = p.id
WHERE p.status = 1
GROUP BY (p 的所有列)
ORDER BY ...
LIMIT #{offset}, #{size}
```

**你想什么（逐点）：**
- `LEFT JOIN`：一个商品有多个规格（SKU），列表页只想要"每个商品一行 + 起售价"，
  就把规格表接上来，再用 `MIN(price)` 取最低价当起售价。
- `WHERE status=1`：只要上架的。
- `GROUP BY p 的所有列`：因为 `SELECT p.*`，必须把商品所有列都放进 GROUP BY（否则 SQL 语义不对）。
  **这个细节面试常问**：为什么列表页 SQL 要 GROUP BY 全列？——因为聚合函数 `MIN(price)` 出现时，
  非聚合列也必须 group。
- `LIMIT offset,size`：手写分页（offset 由 `PageQuery.getOffset()` 算出来）。

**③ 配套方法**：`countOnSale()`（列表总数，算 total）、`selectById`（详情，含 detail 大文本）、
`selectAllOnSale`（AI 域会用到，先看名字记下即可：全量在售商品，给 AI 当"商品手册"）。

**④ `ProductSkuMapper`**：`selectById` / `selectByProductId` / `selectByIds`(foreach) /
`deductStock` / `addStock`。**提前认识 `deductStock` / `addStock`**——第 6 课防超卖会用到。

**⑤ `service/impl/ProductServiceImpl`**：
- `pageOnSale`：**先 count 再拿 list**（两个查询），把结果包成 `PageResult` → `toListVO`（**不含 detail**，
  列表页不搬大文本）。
- `detail`：`selectById` → 校验**存在且 status==1**（`PRODUCT_NOT_FOUND`/`SKU_OFF_SHELF`）→ 带 skus。

**与 SSM 的差异点：** 没有 MyBatis-Plus 的 `selectPage`，也没有分页插件——分页三件套全靠手写
（`PageQuery` 算 offset + XML 写 `LIMIT` + `PageResult` 组装）。这其实是很多 SSM 老项目原汁原味的样子，
你读起来应该很熟。

---

## 第 6 课 order 域：下单防超卖——真正的硬核代码

### 本课目标

读懂整个项目最核心的逻辑：**下单的事务、防超卖的乐观扣减 SQL、取消订单的状态机 CAS、以及金额为什么全程用 BigDecimal**。这一课值得放慢速度。

### 读代码路线

**① 状态常量先记：`Order` 状态机**

`PENDING_PAY → PAID → SHIPPED → COMPLETED`，可中断分支：从 `PENDING_PAY`→`CANCELLED`。

**② 核心方法：`service/impl/OrderServiceImpl.create`**（头顶 `@Transactional(rollbackFor = Exception.class)`）

按步骤顺序读代码，逐行对照注释：

1. 取 userId；items 为空 → `CART_EMPTY`。
2. 逐项：
   - `skuMapper.selectById(skuId)` → 校验商品上架
   - **内存预检库存**：先看一眼够不够（提前给友好报错而已）
   - **`skuMapper.deductStock(id, qty)` 真·扣库存** —— 重点，见下面方框
   - 组 OrderItem：**快照** `productName / skuName / price`（让价定格在当下）
   - 累加 `total`（`BigDecimal`，**严禁 double**）
3. 组 Order：`orderNo = yyyyMMddHHmmssSSS时间戳 + 4位随机 + userId%1000`；状态 `PENDING_PAY`；收货信息 → insert。数据库 `uk_order_no` 兜底撞号。
4. 明细 `batchInsert`（orderId 已回填）。
5. `cartMapper.deleteByUserIdAndSkuIds` 清购物车本次下单的 SKU（直接购买场景幂等）。

**⭐ 防超卖核心 SQL（读到这里请停下来盯 20 秒）：**

```sql
UPDATE t_product_sku
SET stock = stock - #{qty}, sales = sales + #{qty}
WHERE id = #{id} AND stock >= #{qty}
```

**你想什么（这是全文最重要的一段）：**
- 为什么不是「先 SELECT 看库存，够了再 UPDATE」？——因为那两步之间有间隙，
  两个并发请求都读到"还有 1 件"，都通过检查，都去扣，就超卖了（扣成负数）。
- 这里的写法：**扣减和"够不够"的判断放进同一条 UPDATE，由数据库行锁 + WHERE 条件原子完成**。
  `WHERE stock >= qty` 不满足 → 影响 0 行 → 抛 `STOCK_NOT_ENOUGH`。
- 那代码里"内存预检"岂不是多余？不——它只是**提前给用户友好提示**（少一次 DB 开销、更快报错），
  **真正的防线在这条 SQL**。记忆锚点：**预检是面子，SQL 的 WHERE 是里子**。

**事务整体性**：`@Transactional(rollbackFor = Exception.class)` —— 扣库存→写订单→写明细→清购物车
全在一个事务；任一步抛异常整体回滚（**含已扣的库存**）。注意 `rollbackFor=Exception.class`：
Spring 默认只回滚 `RuntimeException`，这里显式扩展让 `Exception` 也回滚（更稳）。

**③ 取消订单：`cancel`**（也标 `@Transactional`）

1. `selectById` → 归属校验（`!userId.equals(order.getUserId())` 或 null → `ORDER_NOT_FOUND`）
2. **状态机 CAS 更新**：

   ```sql
   UPDATE t_order SET status = 'CANCELLED', cancel_time = <now>, version = version + 1
   WHERE id = ? AND status = 'PENDING_PAY'
   ```

   影响 0 行 = 状态不对（比如已被并发取消）→ `ORDER_STATUS_INVALID`。**防并发重复取消**。
   注意 `version + 1`：乐观锁痕迹（预留将来秒杀强化。详见 L3 教程）。
3. 成功才 `selectByOrderId` 遍历逐条 `addStock` 回补库存。

**④ 查询防越权**：`detail` / `pageMyOrders` 都带 `userId` 校验归属；`selectByUserIdPage` 用
`(user_id, created_at)` 联合索引，`ORDER BY created_at DESC, id DESC`。

### 现在代码里发生了什么（面向你自己的总结）

这套写法和 SSM 的区别不在"会调几层"，而在**并发正确性意识**：
- 用**乐观锁式 UPDATE** 而非「先查后改」处理库存；
- 用**事务**保证多写操作要么全成要么全不成；
- 用**状态机 CAS** 防止并发重复操作；
- 用**快照**隔离"下单价格"和被改动的商品主数据；
- 金额全程 **BigDecimal** 杜绝浮点误差。

这些是面试竞品，也是本项目为什么值得读的原因。搞懂这一课，你就 grasp 到本项目的"魂"。

---

## 第 7 课 ai 域：Spring AI + SSE 流式的开眼课

### 本课目标

读懂 Spring AI 的 `ChatClient` 怎么用、普通问答和流式(SSE)的区别、以及"消息成对入库不漏不丢"的时序设计。这块是 SSM 完全没接触的全新领域。

### 读代码路线

**① 先看 `ai/controller/ChatRestController`**

```
POST /conversations            创建会话（body 可空，可选 ChatTitleRequest）
GET  /conversations            我的会话列表
GET  /conversations/{id}/messages   某会话的聊天记录
POST /                         普通问答，返回 R<String>
POST /stream（produces=TEXT_EVENT_STREAM_VALUE）  流式问答，返回 Flux<String>
```

**关注点：** `/stream` 返回 `Flux<String>`（响应式流），且 `produces` 指定了
`text/event-stream` —— 这就是 SSE（Server-Sent Events）。SSM 里你是见不到 `Flux` 的。

**② `service/impl/ChatServiceImpl.chat`（普通问答）**

流程：
1. `resolveConversation`（归属校验 / 按需自动新建，标题取问题前 20 字）
2. `chatClient.prompt().system(buildSystemPrompt()).messages(toAiHistory).user(msg).call().content()`
3. 异常捕获 → `AI_SERVICE_ERROR`
4. 双写保存 (**user 消息 + assistant 消息**) `saveMessage`

**③ `ChatServiceImpl.stream`（SSE 流式）——时序设计是精华**

```java
chatClient.prompt()...stream().content()   // 返回 Flux<String>
```

配四个回调：
- `doOnSubscribe`：**先持久化用户提问** —— 防止流开始了但中途失败，导致"提问没入库"（防孤儿提问）。
- `doOnNext(sb::append)`：**边接收边累加**（收到一段就往 StringBuilder 里拼一段）。
- `doOnComplete`：**流结束后持久化完整 assistant 回答**。
- `onErrorResume`：返回 `"\n\n[AI 服务暂时不可用，请稍后再试]"` —— **防 SSE 裸断**（不会让前端收到一个断掉的半截响应却不报错）。

**你想什么（可面试的点）：** 为什么 not 在流一开始就把整段答案存了？
——因为流式的回答是"边生成边来"的，只有 `doOnComplete` 时才知道完整内容。
那为什么提问要 `doOnSubscribe` 先存？
——为了"用户提问 + AI 回答"成对入库，既保证不丢提问，又保证有一条能对上。

**④ `buildSystemPrompt`（V1 预置知识）：**

```java
productMapper.selectAllOnSale()   // 全量在售商品
→ 裁剪字段 (id/name/subTitle/minPrice/detail)
→ ObjectMapper 序列化为 JSON
→ 写进 system prompt，并强调：
   "基于商品库如实回答、没有就坦诚说不知道、不编造"
```

这解答了 L1 教程里"AI 只答手册里有的"——手册就是这条 SQL 现查的在售商品。

**⑤ `toAiHistory`**：把 DB 历史消息转成 Spring AI 的 `UserMessage`/`AssistantMessage`（传给模型当上下文），**避免与本次提问重复**。

**⑥ 归属校验 `ensureOwned`**：会话不存在或非本人 → `NOT_FOUND`「会话不存在」。
（与购物车一样，AI 会话也是"你的就是你的"。）

### 与 SSM 的差异点：响应式 Flux 不是同步 List

SSM 你熟悉的是同步 `List<User>`。这里 `Flux<String>` 是**响应式流**：
- 数据可以"一块一块"发，而不是攒成一整坨再返回。
- Controller `produces=text/event-stream` 就是让 HTTP 以 SSE 通道逐个推送。
- 代码用 `doOnNext/doOnComplete/onErrorResume` 描述"来了/完了/错了"三种时刻做什么。

**初学建议：** 不必深究响应式背后的背压理论，先搞清楚 **回调触发时机** 和 **为什么这种"事件驱动"写法保证消息对不丢**。

---

## 第 8 课 自己的尝试：按这套读法，去读购物车

### 本课目标

不代读，给你一份"读购物车(Cart)域"的自查清单，验证前面的读法是否内化。

### 读代码路线（自己做，做完对照下方答案）

1. 打开 `goods/controller/CartRestController`，列出 5 个对外动作（增/删/改/查/清空）与方法、路径。
2. 打开 `goods/service/impl/CartServiceImpl`，读 `add` 方法，回答：
   - 加购前做了哪三层检查？（提示：SKU 存在 → 上架 → 库存）
   - 为什么同 SKU 重复加购走"数量相加"而非新增一行？数据库哪张表的哪个唯一索引在兜底？
3. 看 `mapper/Cart.xml` 的 `deleteById` 的 WHERE 条件，回答：为什么 update/delete 都要带 `userId`？
   返回 0 行说明什么？（提示：归属校验下沉 SQL，答 `CART_ITEM_NOT_FOUND`）
4. 看 `CartItemVO` 的 `getSubtotal()`，回答：小计是哪里算的？（`price × quantity`）

**参考答案速查：**
- 动作：GET 列表 / POST 加购 / PUT {id} 改数量 / DELETE {id} 删条目 / DELETE 清空。
- 三层检查：`selectById` 存在 → 商品上架 → 库存够（**内存预检**，真扣在下单）。
- 同 SKU 相加：DB `t_cart` 的 `uk_user_sku(user_id, sku_id)` 唯一索引保证"一人一排"。
- 带 userId：防越权，`deleteById(id, userId)` 返回 0 = 不是你的条目 → `CART_ITEM_NOT_FOUND`。
- subtotal：`getSubtotal() = price × quantity`（VO 上实时算，不入库）。

---

## 第 9 课 给你一份「SSM → 本项目」差异速查表

| 传统 SSM 习惯 | ai-mall 本项目 | 在哪个文件可见 |
|---|---|---|
| 顶层 `controller/service/mapper` 平铺 | 按业务域 `user/goods/order/ai`，域内再分层 | `目录树` |
| `@MapperScan` 扫所有 Mapper | 每个 Mapper 自己 `@Mapper` | `user/mapper/UserMapper` |
| 字段注入 `@Autowired` | 构造器注入 `@RequiredArgsConstructor + final` | 各 `service/impl` |
| 实体叫 `entity` | 叫 `bean` | `user/bean` |
| Controller 名 `XxxController` | `XxxRestController` | `user/controller` |
| Session + Cookie | Sa-Token 无状态 token + `Authorization` 头 | `config/SaTokenConfig` |
| MyBatis-Plus / PageHelper 分页 | 手写 XML `LIMIT offset,size` + `PageQuery/PageResult` | `Product.xml` |
| 返回格式各管各 | 统一 `R{code,msg,data}` + 全局异常 | `common/R` `common/GlobalExceptionHandler` |
| `R.ok(String)` 与泛型重载混用 | **只留** `ok(T)` 和 `ok(String,T)` | `common/R` |
| 密码 md5/明文 | `BCryptPasswordEncoder` | `config/PasswordConfig` |
| 防超卖先查后改 | `UPDATE ... WHERE stock>=?` 乐观扣减 | `OrderServiceImpl.create` / `ProductSku.xml` |
| 取消订单直接改状态 | 状态机 CAS `WHERE status='PENDING_PAY'` + version 乐观锁 | `order/mapper/Order.xml` |
| 金额 double | 全程 `BigDecimal` | `OrderServiceImpl` |
| 同步返回 List | AI 问答用 `Flux<String>` + SSE 流式 | `ChatServiceImpl.stream` |

---

## 结课：这套代码的阅读心法总结

**读代码的顺序**（按依赖方向，别跳）：
```
AimallApplication（点火）
  → config（依赖注入订单 + 全局开关：AI/密码/鉴权/CORS）
  → common（返回格式 + 错误码 + 全局异常 + 分页）
  → user（三层典范，建立完整印象）
  → goods（JOIN + 分页 SQL）
  → order（事务 + 乐观扣减 + 状态机 —— 核心）
  → ai（Spring AI + SSE 流式 —— 增量惊喜）
```

**每一课的自问模板**：
1. 这个 Controller 暴露了哪些"动作"？路径/方法是什么？
2. Service 里最重要的方法，业务决策点是什么？（校验什么、抛什么异常、用到什么并发技巧）
3. Mapper 的 XML 里，哪个 SQL 值得背下来？为什么这么写？
4. 这里的"与 SSM 习惯不同"的地方，作者为什么这么设计？

**四大"本项目的魂"（你已逐层亲手摸过）**：
- ✅ 分域分层 + 构造器注入 + 逐接口 @Mapper
- ✅ 统一返回 `R` + 全局异常（工程整洁的底线）
- ✅ 防超卖：`UPDATE ... WHERE stock>=?` + 事务 + 状态机 CAS + BigDecimal（并发正确的核心）
- ✅ AI 域：Spring AI + SSE 流式 + 消息成对入库（增量技术栈的亮点）

**下一步**：
- 想看每个接口的参数/返回全字段表，去 **Reference 参考手册（L2）**；
- 想理解"为什么是 Sa-Token 无状态""为什么乐观锁能防超卖"的原理本质，去 **Explanation 原理讲解（L2/L3）**；
- 想被面试官追问到底，去 **Tutorial L3 面试进阶版**：现场讲清防超卖、CAS、乐观锁、SSE 时序。

> 🎉 通关彩蛋：现在回看 `OrderServiceImpl.create` 里那条 `deductStock` 调用——
> 你已经不只是"见"过它，而是**懂**它背着数据库行锁在替你拦下超卖。
