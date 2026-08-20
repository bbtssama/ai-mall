# 把 ai-mall 演进到 V2/V3：五个关键改造点实战（How-to · L3 面试进阶版）

> **本指南的任务**：带你把 ai-mall 这个单体电商项目，按真实演进路线做**五处关键改造**——改造的目标不是"花架子"，而是每一项都踩在面试常考、生产必见的技术点上（配置安全、并发测试、缓存、分库分表前兆、分布式锁）。
>
> **写给谁**：进阶开发者 / 面试准备者——你已懂 SSM，能独立把项目跑起来（跑不起来的先看 L1/L2 版），并且想在这套代码上做出"简历能写、面试能答"的改造亮点。
>
> **本指南的特点**：每个任务都不是空谈方案，而是**锚定项目真实代码**给出现状 → 目标 → 步骤 → 验证 → 风险五段式。改造后的**新增代码是示意**，因为最终的实现要与你 clone 的源码对齐；但"现状"字段引用的都是素材包核对过的真实事实。
>
> **事实来源**：除素材包外，改造目标对应项目官方演进路线：V2 = RAG 导购 + 支付宝/微信沙箱支付 + 内容笔记；V3 = Redis/MQ/秒杀。本指南从中选取"不依赖外部商务条件（支付资质）、适合在本地代码库闭环落地"的五项。

---

## 目录

- [提前说：改造的底线（不是安全网，是面试红线）](#提前说改造的底线不是安全网是面试红线)
- [任务 1：AI Key 改纯环境变量注入并加固](#任务-1ai-key-改纯环境变量注入并加固)
- [任务 2：补 JUnit 防超卖并发测试](#任务-2补-junit-防超卖并发测试)
- [任务 3：购物车迁 Redis Hash](#任务-3购物车迁-redis-hash)
- [任务 4：订单号换雪花 ID](#任务-4订单号换雪花-id)
- [任务 5：下单加分布式锁 / 库存预减](#任务-5下单加分布式锁--库存预减)
- [验收与面试追问（怎么把改造讲成加分项）](#验收与面试追问怎么把改造讲成加分项)
- [附录 A：五任务改动文件清单](#附录-a五任务改动文件清单)
- [附录 B：改造后的验证命令集](#附录-b改造后的验证命令集)

---

## 提前说：改造的底线（不是安全网，是面试红线）

改造任何"能跑"的项目，你都要守住三条底线，否则一票否决：

1. **不破坏已有红绿**：改完必须 `pwsh -File scripts/smoke-test.ps1` 仍 9/9 通过（除非那条链路被你自己替换成新的实现，如任务 3 购物车迁 Redis 后有对应的新验证）。
2. **一张表一个真源**：数据在同一时刻只能有一个权威存储。任务 3 最危险——Redis 和 MySQL 可以并存（Redis 缓存 + MySQL 兜底/落地），但**不能**出现"同一份数据两边都改、互相不知道"。
3. **缺失先行于安全**：先补齐可观测（日志）、可回滚（开关/降级），再上不可逆改造（换主键生成器、动扣库存时机）。

> 这五项的改造难度和依赖是递增的：任务 1、2 是"零外部依赖、今天就能干";任务 3 开始引 Redis;任务 5 要在任务 2 的测试保护下动最敏感的扣库存逻辑。**建议按顺序做**，别一上来就动防超卖。

---

## 任务 1：AI Key 改纯环境变量注入并加固

### 1.1 现状（引用真实代码/素材包事实）

- `backend/src/main/resources/application.yml` 里 AI 配置（素材包 §5）：

```yaml
spring:
  ai:
    openai:
      base-url: https://opencode.ai/zen/go        # 不带 /v1，Spring AI 自动拼 /v1/chat/completions
      api-key: ${DEEPSEEK_API_KEY:sk-...}         # ← 现状：环境变量 DEEPSEEK_API_KEY，缺省时回退到明文占位
      model: deepseek-v4-pro
```

- **现状暴露点**：`api-key` 落在**版本化配置文件**里，且带一个明文缺省值 `sk-...`。README/.gitignore 已提示"公开前改环境变量"（素材包 §9"已知问题"第一条：application.yml 含中转 Key），但仍属"靠自觉"级别——一旦有人把 yml 提交上去，Key 就泄露了。
- 本项目用 Spring AI 的 `spring-ai-starter-model-openai`，OpenAI 兼容模式（素材包 §1/§11 已有事实），所以改法是在 Spring AI 的绑定属性上做文章，不碰业务代码。

### 1.2 改造目标

要让 Key **完全不出现在任何配置文件里**，且：
- 未配置环境变量时**启动即报错**（fail-fast，而不是带假 Key 悄悄跑）；报错里**不打印 Key**；
- 支持多环境，本地/CI/CD 各自注入；
- 中间人攻击防护：中转是 http 就拒绝（或显式允许但告警）。

### 1.3 关键步骤

**步骤 1：删掉 yml 里的明文缺省**，环境变量缺省值也去掉，让它"必须提供"：

```yaml
# application.yml
spring:
  ai:
    openai:
      base-url: ${SPRING_AI_BASE_URL:https://opencode.ai/zen/go}
      api-key: ${DEEPSEEK_API_KEY}        # 不再给缺省；下面用校验兜底
      model: deepseek-v4-pro
```

**步骤 2：加一个 `@Configuration` 启动校验**（示意），在 Spring 上下文刷新时强制 Key 存在且格式合法：

```java
// config/AiKeyValidationConfig.java（示意；新增文件）
@Configuration
@ConfigurationProperties(prefix = "spring.ai.openai")
public class AiKeyValidationConfig {
    private String apiKey;
    // ...getter/setter...
    @PostConstruct
    void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "DEEPSEEK_API_KEY 未配置。请在环境变量或启动参数注入后再启动。");
        }
        if (apiKey.startsWith("sk-...")) {          // 占位符还没被替换
            throw new IllegalArgumentException("AI Key 仍是占位值，禁止启动");
        }
        if (!apiKey.matches("^sk-[A-Za-z0-9._-]{16,}$")) {
            throw new IllegalArgumentException("AI Key 格式不合法，拒绝启动");
        }
    }
}
```

> 补充加固（示意，按需取）：`logback` 里给 `spring.ai` 相关 log 加 masking；或写个 `@Around` 环绕切面，把任何抛出来的 Exception 中可能带 `api-key` 的串打码后再落日志——防止异常堆栈把 Key 带出。

**步骤 3：CI/CD / 启动脚本注入**（示意）：

```text
# Windows 临时注入（终端会话级，不写盘）
$env:DEEPSEEK_API_KEY="sk-xxxx"
# 或启动时传（不常驻环境）
mvn spring-boot:run -Dspring.ai.openai.api-key=sk-xxxx
```

> 优先级提醒：Spring Boot 中**命令行参数 > 环境变量 > yml**。所以即使 yml 里还留着缺省，命令行 `-D` 也能压过它——但我们的目标是 yml 干脆别留。

### 1.4 验证方式

1. **fail-fast**：不设 `DEEPSEEK_API_KEY`，`mvn spring-boot:run` → 应立刻抛 `AI Key ... 禁止启动`，**不启动**；
2. **注入通过**：`$env:DEEPSEEK_API_KEY="sk-xxx"` → 能正常启动；
3. **不泄露**：全仓搜 `grep -r "sk-" backend/src/main/resources/` → 无真实 Key；异常日志里搜 key → 无明文；
4. **回归**：正常调一次 `/api/v1/chat` 与 `/api/v1/chat/stream`，AI 回答正常（SSE 仍流式）。

### 1.5 风险点

- ⚠️ **环境变量被误置为占位** → 校验主动拦截（占位符检测），阻断启动；
- ⚠️ **CI 管道里没注入** → 校验让它在 CI 的单元/启动阶段就失败，而不是带假 Key 跑集成；
- ⚠️ **异常吞 Key** → masking 切面是**可选**加固，优先级低于前两步；
- ⚠️ **base-url 若被改成 http** → 建议加个"非 https 拒绝"(中转域名例外)或至少打 error 日志，你写的就是面向生产的姿势。

---

## 任务 2：补 JUnit 防超卖并发测试

### 2.1 现状（引用真实代码/素材包事实）

- 项目**当前没有单元测试**，仅 `scripts/smoke-test.ps1` 黑盒冒烟 9/9 通过（素材包 §9"已知问题"第二条）。
- 防超卖核心是这条**原子 SQL**（素材包 §4/§8）：

```sql
-- ProductSkuMapper.deductStock(id, qty)
-- UPDATE t_product_sku SET stock=stock-?, sales=sales+? WHERE id=? AND stock>=?
```

- 扣减发生在 `OrderServiceImpl.create`（`@Transactional(rollbackFor=Exception.class)`）内部第 2 步，先**内存预检**再 DB 层乐观扣减，返回 0 行 = 并发抢光 → `STOCK_NOT_ENOUGH`（素材包 §6 order 域 step 2 / §8.1）。

> 现状正确性依赖两点：① SQL 的 `AND stock>=?` 条件 + 行锁在并发下原子成立；② 整个 create 事务里"扣库存→写订单→写明细→清购物车"全回滚（§8.2）。**测试要同时钉死这两点。**

### 2.2 改造目标

补一组能打的并发测试，达到：
- **可复现**：每次跑都能稳定暴露/验证"库存不为负、订单不超卖"；
- **覆盖两条污染边界**：扣减原子性（并发下单总成交 ≤ 库存）、事务回滚（中途异常不残留半截状态）；
- **可进 CI**：不依赖真实 MySQL 也能跑（用 H2 兼容模式或 Testcontainers，二选一并说明取舍）。

### 2.3 关键步骤

**步骤 1：引入测试依赖**（示意，`backend/pom.xml` 的 `<dependencies>`）：

```xml
<!-- 方案 A：内存态 H2（快，CI 免容器），但方言尽量贴合 MySQL -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope>
</dependency>
```

> 取舍：H2 在 `MODE=MySQL` 下模拟 MySQL 方言，但对"行锁/并发抽读"的模拟不完全真实；Testcontainers 起真 MySQL 更准但要 Docker。给面试说法：**单元/逻辑测试用 H2，防超卖这种"真并发"用 Testcontainers 单独跑**——一句话体现你懂两者局限。

**步骤 2：写防超卖并发测试**（示意，`src/test/java/com/aimall/order/OrderConcurrencyTest.java`）：

```java
@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {
    @Autowired OrderService orderService;

    @Test
    void concurrentCreate_shouldNotOversell() throws Exception {
        long stock = /* 通过 ProductSkuMapper 读到 skuId=1 当前库存 */;
        int threads = 20;                     // 并发用户
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int k = i;
            futures.add(pool.submit(() -> {
                start.await();               // 一起冲
                try {
                    orderService.create(buildRequest(skuId, 1, "u" + k));
                    success.incrementAndGet(); // 下单成功数
                } catch (BusinessException e) {
                    // 期望部分线程 STOCK_NOT_ENOUGH（库存不够就失败），不算错误
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get();
        // 断言：成功的订单数 必须 ≤ 初始库存；且库存 ≥ 0、未出现负数
        assertTrue(success.get() <= stock);
        assertEquals(0, /* 查 t_product_sku 库存 */ resultStock);
    }

    @Test
    void create_whenMiddleStepFails_shouldRollbackAll() {
        // 传一个不存在的 skuId（触发 SKU_NOT_FOUND）
        assertThrows(BusinessException.class, () ->
            orderService.create(buildRequest(99999, 1, "u_fail")));
        // 断言 t_order / t_order_item 无该订单残留，t_product_sku.stock 未变动
    }
}
```

**步骤 3：建 `application-test.yml`**（示意）：指向最终会 H2/测试库的连接，关闭不必要的日志。

**步骤 4：跑测**：

```text
mvn -pl backend test -Dtest=OrderConcurrencyTest
```

### 2.4 验证方式

- 并发用例：库存 5、开 20 线程各买 1 → 断言成功数 ≤ 5、最终 stock=0 **且从未出现负数**、订单数 = 成功数（一致）；
- 回滚用例：失败下单后 `t_order`/`t_order_item` 无残留、库存不变；
- 故意"开刀"验证测试的杀伤力（可选、推荐演示）：把 deductStock 里的 `AND stock>=?` 临时删掉，测试应**立刻全红**——这证明你的测试真的在守护原子性，不是摆设。

### 2.5 风险点

- ⚠️ **H2 对 MySQL 锁语义有偏差** → 防超卖结论用 Testcontainers 复核，别只信 H2 绿；
- ⚠️ **测试污染真库** → 用独立测试库/事务回滚策略（`@Transactional` 测试），别写进 dev 的 `ai_mall`；
- ⚠️ **并发测试看机遇率** → 用 CountDownLatch 让线程同时起跑、并把库存压到很小（如 2~5），让竞争必然发生；
- ⚠️ **别测出"全绿假象"** → 断言维度要够（成功数、最终库存、无负数三者都断）。

---

## 任务 3：购物车迁 Redis Hash

### 3.1 现状（引用真实代码/素材包事实）

- 购物车现状在 MySQL：`t_cart` 表，关键约束 `uk_user_sku(user_id, sku_id)`、`updated_at ON UPDATE`（素材包 §7）；
- 访问接口（素材包 §6 goods 域）：`GET/POST /cart`、`PUT/DELETE /cart/{id}`、`DELETE /cart`；
- `CartMapper` 方法集（§6）：`selectItemsByUserId`（JOIN sku+product）、`selectByUserIdAndSkuId`、`insert`、`updateQuantity(id,userId,quantity)`、`deleteById(id,userId)`、`deleteByUserId`、`deleteByUserIdAndSkuIds(userId,skus)`；
- `CartServiceImpl`：userId 取 `StpUtil.getLoginIdAsLong()`；加购同 SKU 数量相加、封顶 99（封顶下限素材包 §6）；归属校验在 `update/delete` 里**下沉 SQL**（`WHERE ... AND user_id=?`，返回 0 行 = `CART_ITEM_NOT_FOUND`）。

> 现状本质：一张以 `user_id` 为查询入口的"用户→(sku, qty)"映射表。**Hash 天然契合**：`key = user:{userId}:cart`，`field = skuId`，`value = quantity`。

### 3.2 改造目标

把"读/算购物车"搬到 Redis，让 `GET /cart` 成为低延迟读，同时**MySQL 仍保留**作为持久层/兜底，达到"缓存 + 落地"双写一致，而非"数据搬走"。

### 3.3 关键步骤

**步骤 1：引入 Redis（示意，pom + 配置）**：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

**步骤 2：抽象 CartCache 服务**（示意，新增 `goods/service/CartCacheService.java`）：对外提供 `getCart/upsert/remove/batchRemove/clear`，内部操作 `RedisTemplate<String,Object>` 的 `hashOperations`，并为 quantity 提供 `increment`。**关键**：变更购物车时**先写 Redis，再写 MySQL**，且返回给前端以 Redis 为准；MySQL 侧走**异步/双写落地**（示意：事件监听或简单同步双写均可，先说清一致性策略）。

**步骤 3：改写 `CartServiceImpl`**：读路径改为走 `CartCacheService.getCart(userId)`；写路径（加购/改量/删）改为 `hashOperations` 操作 + 同步/异步写 MySQL；归属校验在 Redis 层等价实现（操作指定 user 的 hash，天然隔离，比 SQL 下沉更直接）。

> **一致性策略（务必在面试前想清楚）**：
> - 策略 A"Cache-Aside + 落地双写"：Redis 命中即回；不命中去 MySQL 并回填。用户加购 = 写 Redis + 写 MySQL。
> - 策略 B"Redis 为真源，MySQL 兜底"：服务重启后从 MySQL 恢复 Redis 再降级。
> 推荐 A（改动小、能把扣减与下单解耦），并在注释里写明"若后续做多实例部署，需引入消息队列保证最终一致"——一句话带出 MQ 演进（V3）。

### 3.4 验证方式

- 功能：`POST /cart` 加购 → `GET /cart` 能读到且 `subtotal=price*quantity` → `PUT /cart/{id}` 改量 → `GET /cart` 反映；
- 一致性：停 Redis → 服务可降级回 MySQL 读（Cache-Aside 空回源）；重启进程 → Redis 重建，购物车不丢；
- 数据：`redis-cli HGETALL user:<id>:cart` 与 MySQL `t_cart` 对比一致；
- 权限：`DELETE /cart/{id}` 换别的 user token 操作他人条目 → 在 Redis 层也应失败（field 隔离）。

### 3.5 风险点

- ⚠️ **双写不一致**（Redis 成功 MySQL 失败）→ 必须定回滚/补偿：交易类变更建议"先 Redis 事务成功再落 MySQL，失败则连 Redis 也回滚或重试队列"；
- ⚠️ **Redis 丢了购物车** → 用 MySQL 兜底恢复，别让 Redis 成为唯一真源；
- ⚠️ **旧 `uk_user_sku` 语义变化** → `update`/`delete` 归属校验有两条实现（Redis 层 + MySQL 链 SQL 下沉），**忘了一条就出现越权漏洞**，两条都要在测试覆盖；
- ⚠️ **缓存穿透**（陌生 userId）→ Cache-Aside 空结果也短暂缓存或直接回源，别让 Redis 拖慢直连。

---

## 任务 4：订单号换雪花 ID

### 4.1 现状（引用真实代码/素材包事实）

- `OrderServiceImpl.create` 组 Order 时：`orderNo = yyyyMMddHHmmssSSS`（17 位时间戳）+ 4 位随机数 + `userId%1000`（用户尾号），业务唯一由 `uk_order_no` 兜底（素材包 §6 order 域 step 3 / §8.4）；
- 素材包明说演进：`V2 可换雪花 ID`（§8.4）；表 `t_order.order_no VARCHAR(32)`（§7），雪花 ID 转字符串一般 18~19 位，**列宽足够**（现状 32 位 VARCHAR 装得下）。

> 现状痛点：时间戳+随机+尾号的唯一性靠**概率 + 唯一索引**，极端并发下 `uk_order_no` 抛异常（要靠重试兜底）；且订单号**可解析出时间与用户尾号**，泄露了部分信息。

### 4.2 改造目标

引入分布式 ID 生成器（雪花/增强雪花）产出全局唯一、趋势递增、不可反解的订单号；保留 `uk_order_no` 作为**最终防线**而非"主要生成手段"。

### 4.3 关键步骤

**步骤 1：引入雪花工具**（示意，新增 `common/snowflake/IdGenerator.java` 或引第三方）；实现经典雪花位布局：占 64 位，`时间戳41位 + 机器标识10位 + 序号12位`，注意**依赖时钟**。

**步骤 2：注入并在 create 时替换**（示意）：

```java
// OrderServiceImpl
private final IdGenerator idGenerator;     // 构造器注入（沿用 RequiredArgsConstructor）
// ...
order.setOrderNo(String.valueOf(idGenerator.nextId()));   // 替换原时间戳拼接
order.setOrderNo(String.valueOf(idGenerator.nextId()));
```

**注意**：`t_order.order_no` 现在是 `VARCHAR(32)`,雪花存字符串可放；若将来要存 BIGINT，需要 `ALTER TABLE t_order MODIFY order_no BIGINT` 并重建 `uk_order_no`（老数据转换）。

**步骤 3：时钟回拨防护**（示意）：生成前记录/对比 lastTimestamp，发现回拨：若回拨在阈值内（如≤5ms）自旋等待，否则抛异常/切备用生成器——这是雪花最常被问的坑。

### 4.4 验证方式

- 唯一性压测：并发生成大量 `nextId()`，`distinct` 计数 == 总数，无重复；
- 趋势：`order_no` 单调递增（新订单 > 旧订单），利于按时间分页索引；
- 端到端：下单 → 详情的 `orderNo` 是雪花 ID 格式，数据库中 `t_order.order_no` 正常，原有"下单→查详情→取消"链路不回归；
- 冒烟：`smoke-test.ps1` 9/9 仍通过（订单那项用了新单号）。

### 4.5 风险点

- ⚠️ **时钟回拨 = 重复 ID 灾难** → 必须做回拨防护，面试必答；
- ⚠️ **机器标识管理** → 单机可写死 `workerId`；多机要分发表/zk，别让两台机器 worker 冲突；
- ⚠️ **唯一索引保留** → 雪花只是降低冲突概率，`uk_order_no` 在极端情况下仍要拦截，不能删;
- ⚠️ **老订单号兼容** → 现有订单不迁移（雪花只影响新单），但数据库类型若改 BIGINT，要处理存量 VARCHAR 数据转换。

---

## 任务 5：下单加分布式锁 / 库存预减

### 5.1 现状（引用真实代码/素材包事实）

- 现状防超卖 = **DB 层原子扣减** 兜底（`deductStock` 的 `AND stock>=?` 条件+行锁），内存预检只给友好报错（§8.1）；事务内"扣库存→写订单→写明细→清购物车"整体回滚（§8.2）；
- **现状的局限（面试要能讲）**：同一 SKU 高并发下单时，每条请求都执行一条 `UPDATE ... WHERE id=? AND stock>=?`——行锁竞争集中在 `t_product_sku` 这**一条行**上，QPS 上去后 DB 成为热点；且所有请求都会真实打到 IO（没有内存/Redis 层先挡一下）。
- V3 演进：Redis/MQ/秒杀；V3 秒杀强化用 `version` 字段做强一致（§8.3：version 随更新 +1，乐观锁痕迹）。

### 5.2 改造目标

给"下单扣库存"加**分布式锁**（串行化同 SKU 的扣减临界区），并做**库存预减**（事前冻结/扣减到 Redis，失败快速拒绝），把 DB 行锁热点前移，同时保持"最终以 DB 兜底"的正确性。**两个子能力二选一或叠加**：
- 分布式锁：锁住"某个 skuId 的扣减"；
- 库存预减（Redis 预扣）：请求先减 Redis 库存，成功才进事务，DB 侧再落地/校验。

### 5.3 关键步骤

**计划 A：分布式锁（示意）**——锁粒度 = skuId，锁住扣减临界区：

```java
// OrderServiceImpl.create 扣减前
String lockKey = "ORDER:LOCK:SKU:" + skuId;
Boolean locked = redisLock.tryLock(lockKey, TimeUnit.SECONDS.toMillis(5), 3_000);
if (!Boolean.TRUE.equals(locked)) {
    throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "当前商品购买火爆，请稍后再试");
}
try {
    // 原 deductStock + 组Order + insert ...（保持原样，DB 仍是最终防线）
} finally {
    redisLock.unlock(lockKey);
}
```

> 关键点：**锁只负责"串行化临界区+提前挡并发"**，真正的原子性仍由 DB 的 `AND stock>=?` 保证——**不要**为了用锁就删掉 SQL 条件（双保险，面试加分）。锁要带过期时间防死锁，`try/finally` 释放。

**计划 B：库存预减（示意）**——Redis 预扣，DB 落地/校验：

```java
// 下单前：Redis 预扣
long remain = cartRedis.redisStock.decrement(skuId, qty);
if (remain < 0) {
    cartRedis.redisStock.increment(skuId, qty);   // 回补
    throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "库存不足");
}
// 进事务：跑原 create 逻辑（DB 仍有 stock>=? 兜底）
// 事务失败/回滚：redisStock.increment
```

> 风险点（必讲）：预减后 DB 事务失败必须**回补 Redis**（`increment`），否则 Redis 越减越少、和 DB 库存对不上。两者一致性同样是"先 Redis 事务成→落 DB，失败补偿"。

**步骤 1**：先在 `OrderServiceImpl` 抽出可注入的 `StockGuard`（接口），把扣减入口收拢，便于替换/打桩；
**步骤 2**：实现 Redis 分布式锁与/或预减；**步骤 3**：接入 create 并在事务回滚时补偿；**步骤 4**：用任务 2 的并发测试**重新跑**，确认"有锁/预减后总额仍不超卖、库存无负数"。

### 5.4 验证方式

- 并发回归：跑任务 2 的 `OrderConcurrencyTest`，加锁/预减后**仍应**成功数 ≤ 库存、最终库存 ≥ 0、无负数——这是最重要的护栏；
- 锁互斥：同 SKU 并发下单，`tryLock` 只有一人能拿到，其余快速失败不积压；
- 预减补偿：故意让 create 事务后半段抛异常（比如收货信息缺失触发校验失败），确认 Redis 库存被回补、DB 不对不上账；
- 兜底：`redis-cli` 清空预减数据后，链路应**自动切回 DB**（有兜底开关）。

### 5.5 风险点

- ⚠️ **锁过期/任务未完成** → 锁要带足够 TTL 并重试；加锁失败快速返回，别无限等待；
- ⚠️ **预减与 DB 不一致** → 事务失败必须回补；定期对账任务（Redis vs MySQL）是修 bug 的治本线;
- ⚠️ **热点仍在** → 锁只串行化了边界，若追求更高吞吐要上"分段库存/预扣池"（面试可提，不必做）；
- ⚠️ **别过度设计** → 若这只是单实例测试环境，加锁/预减收益有限；先证明"有性能问题"再上，别把面试改造变成"为了炫技制造复杂度"（说明取舍，本身就是加分姿势）。

---

## 验收与面试追问（怎么把改造讲成加分项）

### 整体验收清单

改造完跑一遍：

```text
pwsh -File scripts/smoke-test.ps1          # 全链路不回归（任务3/5 切换实现后验证对应项）
mvn -pl backend test                        # 新增的 JUnit 并发/回滚/唯一性测试全绿
```

### 面试追问一版（对着准备，别答飞）

| 追问 | 你的应法（须讲出"为什么"） |
|---|---|
| "防超卖你到底靠什么？" | DB 行锁 + `AND stock>=?` 原子扣减是**最终防线**；分布式锁只是**串行化+提前挡**；预减是**性能优化**；三者分层，你说清层级即可得分 |
| "Redis 和 MySQL 双写怎么保证一致？" | 定策略：Cache-Aside（Redis 缓存 DB 为源）+ 变更先写 Redis 再落 DB + 失败补偿/对账；多实例再加 MQ 最终一致 |
| "雪花 ID 时钟回拨怎么办？" | 记录 lastTimestamp，回拨阈值内自旋等待，否则切备用/抛异常；别只说"雪花很快" |
| "为什么不直接上 MQ/中间件？" | 演进是分步的：先本地闭环（Redis/测试/锁）把单体能扛的边界吃到，再谈 MQ 做削峰解耦——体现架构渐进意识（对应 V3） |
| "改造会不会破坏老功能？" | 三底线：冒烟 9/9、单一一真源、缺失先行；每个任务都有独立验证与回滚开关 |

### 踩坑提示（面试暴露短板预警）

- 讲锁时**别删掉** `AND stock>=?` ——删了就等于把正确性押在锁上，锁一挂就超卖；
- 讲 Redis 双写时**必须**提一致性策略与补偿，只讲"把购物车放 Redis"会被追问到底；
- 讲雪花时**别只说**"很快"，时钟回拨、worker 标识、趋势递增三个点至少要中两个。

---

## 附录 A：五任务改动文件清单

| 任务 | 新增/改动文件（相对 `backend/`） | 性质 |
|---|---|---|
| 1 AI Key 加固 | `src/main/resources/application.yml`；新增 `src/main/java/com/aimall/config/AiKeyValidationConfig.java`；（可选）`logback` masking | 改 + 新增 |
| 2 并发测试 | `pom.xml`（加 test/H2/Testcontainers 依赖）；新增 `src/test/java/com/aimall/order/OrderConcurrencyTest.java`；新增 `src/test/resources/application-test.yml` | 改 + 新增 |
| 3 购物车迁 Redis | `pom.xml`（加 redis）；`application.yml`（加 redis 连接）；新增 `src/main/java/com/aimall/goods/service/CartCacheService.java`；改 `.../service/impl/CartServiceImpl.java` | 改 + 新增 |
| 4 雪花 ID | 新增 `src/main/java/com/aimall/common/snowflake/IdGenerator.java`；改 `.../order/service/impl/OrderServiceImpl.java`；（可选）`t_order.order_no` 改 BIGINT | 新增 + 改 |
| 5 锁/预减 | 新增 `src/main/java/com/aimall/order/service/StockGuard.java`（接口）+ 实现；改 `.../order/service/impl/OrderServiceImpl.java`；（配置）Redis 连接复用任务 3 | 新增 + 改 |

## 附录 B：改造后的验证命令集

```text
# 通用：登录拿 token
curl.exe -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"username\":\"demo01\",\"password\":\"123456\"}"

# 任务1：Key fail-fast
mvn spring-boot:run                       # 不设 DEEPSEEK_API_KEY 应启动即报错
$env:DEEPSEEK_API_KEY="sk-xxx"; mvn spring-boot:run   # 注入后正常
curl.exe -X POST http://localhost:8080/api/v1/chat -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"message\":\"推荐一款降噪耳机\",\"conversationId\":null}"

# 任务2：并发测试
mvn -pl backend test -Dtest=OrderConcurrencyTest

# 任务3：Redis 购物车
curl.exe -X POST http://localhost:8080/api/v1/cart -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"skuId\":1,\"quantity\":1}"
curl.exe "http://localhost:8080/api/v1/cart" -H "Authorization: <token>"
redis-cli HGETALL user:<你的id>:cart                      # 与 MySQL t_cart 对账

# 任务4：雪花单号
curl.exe -X POST http://localhost:8080/api/v1/orders -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"items\":[{\"skuId\":1,\"quantity\":1}],\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"上海市xx路1号\"}"
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT order_no, CHAR_LENGTH(order_no) FROM t_order ORDER BY id DESC LIMIT 3;"

# 任务5：并发压测（借助任务2的测试）+ 兜底
mvn -pl backend test -Dtest=OrderConcurrencyTest           # 有锁/预减后仍不超卖
redis-cli FLUSHALL                                          # 清预减，确认 DB 兜底仍能下单（有降级开关时）

# 收尾
pwsh -File scripts/smoke-test.ps1                           # 全链路 9/9
```

---

*做完这五项，你就把这套单体项目从"能跑"推进到"能扛"的版本：配置安全、有测试守护、缓存扩容、分布式 ID、锁与预减。面试时你不仅能复现"怎么改"，还能说出"为什么这样改、代价是什么、怎么回滚"——这正是进阶面试想看到的完整度。*