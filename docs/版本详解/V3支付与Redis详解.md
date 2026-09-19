# V3 支付与 Redis 从零到实战：跟着 AI 种草商城学会"收钱"与"扛流量"

> ai-mall 项目升级讲解文档 · V3（支付闭环 / Redis 缓存三兄弟 / 延迟消息 / 限量发售 / 限流）
> 配套词典：[V3支付与Redis详解【知识词典】.md](V3支付与Redis详解【知识词典】.md)——正文中每个带链接的概念都在词典里有"一句话核心+比喻+原理展开+代码对应+常见追问"
> 前置阅读：[V1.5工程基建详解.md](V1.5工程基建详解.md)（线程池/对象存储/可降级传统）、[V2内容社区与RAG详解.md](V2内容社区与RAG详解.md)（MQ 基础/幂等思想）
> 读法建议：按章顺序读，每章末尾有动手作业——**动手做过的才叫掌握**。

---

## 【易错关键点】（先背这个，全文读完回来再背一遍）

1. **回调接口不靠 token 靠[验签](V3支付与Redis详解【知识词典】.md#验签)**——第三方服务器不可能持有你的用户 token；回调是公网端点，"身份"由签名保证（完整性），业务接口靠"你是谁"（认证）。这是两套不同的身份模型，混用必出事。
2. **验签失败必须拒绝**——不验签 = 把"修改订单状态"的权限开放给全世界。绝不能"为了成功率"放行。
3. **重复回调必然发生**——第三方网络超时重传、处理慢就重试（支付宝重试 8 次以上）。已支付的重复回调必须返回 `success`，否则对方永远重发。
4. **金额必须核对且缺失即拒绝**——回调金额与支付单不一致直接拒绝；**连 amount 字段都没有也拒绝**（本项目评审就揪出一个"字段可空绕过核对"的洞）。
5. **幂等靠[状态机 CAS](V3支付与Redis详解【知识词典】.md#状态机-cas)不靠先查后判**——`UPDATE ... WHERE status='PAYING'` 返回 0 行就是被别人处理过了；并发下"先查后改"两个请求都能通过。
6. **消息级 TTL 有[队头阻塞](V3支付与Redis详解【知识词典】.md#队头阻塞)**——每条消息不同 TTL 时，队首没过期后面的即使过期也不投递。固定超时场景用**队列级 TTL**（本项目选择）。
7. **超时取消与支付回调会竞争同一订单**——两边都是状态机 CAS（PENDING_PAY 为闸门），谁先成功谁生效；**只有取消成功的线程才回补库存**，顺序错 = 库存虚增。
8. **Redis 预减必须 Lua**——"GET 判断 + DECR"两步在并发下必超卖；Lua 在 Redis 单线程里原子执行。**预减成功但后续失败必须回补**（评审揪出的洞：不回补会"看起来抢完了实际有货"）。
9. **缓存空值防[穿透](V3支付与Redis详解【知识词典】.md#缓存穿透)**——查不到也写 `__NULL__` 标记（短 TTL），挡住刷不存在 id 的流量。
10. **TTL 加随机抖动防[雪崩](V3支付与Redis详解【知识词典】.md#缓存雪崩)**——批量导入的缓存给相同 TTL = 同一时刻集体失效。
11. **限流组件 Redis 挂了要放行**——拒绝服务比多花点钱更糟；降级方向要按业务语义选（[fail-open](V3支付与Redis详解【知识词典】.md#降级方向)）。
12. **缓存是[旁路](V3支付与Redis详解【知识词典】.md#缓存旁路)**——Redis 挂了业务"变慢"而不是"不可用"，所有 Redis 调用包在可降级门面里。
13. **事务内发 MQ 消息是反模式**——事务回滚但消息已发（MQ 不参与 DB 事务）= 幽灵消息。正确姿势：`afterCommit` 提交后再发。

---

## 全书地图

```
第 0 章  破除恐惧：支付和缓存就是你已经会的东西（心智模型）
第 1 章  Redis 5 分钟入门（真·0 基础）
第 2 章  把 Redis 接进项目：配置、序列化器、可降级门面
第 3 章  缓存三兄弟：从一次 DB 被打挂的事故讲起（本章最重之一）
第 4 章  支付前传：支付单表、状态机、PayChannel 抽象
第 5 章  签名与验签：0 基础手撕 HMAC（安全的地基）
第 6 章  回调：一次支付通知的完整旅程（本章最重之二）
第 7 章  幂等三层 + 主动查单对账
第 8 章  延迟消息：订单 30 分钟自动取消（TTL+DLX）
第 9 章  限量发售：三层防护防超卖（Lua 逐行讲）
第 10 章 AI 限流：给烧钱的接口上闸
第 11 章 评审复盘：12 个真实问题（面试弹药库）
第 12 章 验收清单 + 面试速查 + 报错速查
```

---

# 第 0 章 破除恐惧：支付和缓存就是你已经会的东西

## 0.1 支付 = 带凭证的第三方 HTTP + 异步通知

0 基础对"接入支付"的想象往往是神秘的黑盒：钱从用户账户飞到商户账户，中间一定有什么高深的金融科技。

拆开看，**你在 V1 已经写过 90% 的代码了**。整个支付流程只有四件事：

| 步骤 | 本质 | 你写过的同构代码 |
|---|---|---|
| ① 创建支付单 | 往自己数据库插一行 | 注册接口插 t_user |
| ② 跳收银台 | 拿到一个 URL 给前端 | 任何返回 URL 的接口 |
| ③ 用户付款 | **发生在第三方，与你的代码无关** | —— |
| ④ 异步回调 | 第三方 POST 你的一个接口 | 前端调你的 Controller |

真正"新"的只有第 ④ 步的一个反向问题：**你的回调接口挂在公网上，任何人都能 curl 它，你怎么知道这次请求真的来自支付宝、报文没被篡改？** 答案是[验签](V3支付与Redis详解【知识词典】.md#验签)（第 5 章整章讲它）。除此之外——插表、状态流转、幂等，你在 V1 订单/V2 审核里全写过。

> 💡 **心智模型①**：支付 = "订单的续集"。下单只是把库存锁住（PENDING_PAY），支付才是把状态推进到 PAID 的那个事件。所有设计围绕一个问题：**这个事件可靠地、恰好一次地**发生。

## 0.2 Redis = 一个超快的远程 HashMap

对 Redis 的恐惧同样多余。**第一近似**：它就是一个放在另一个进程里的 `Map<String, String>`，读写都在内存里所以极快（单机 10 万+ QPS）。你项目里用到的能力只有四样：

| 能力 | 类比 | 项目用途 |
|---|---|---|
| `set(key, value, ttl)` | HashMap.put + 定时自动删除 | 缓存商品详情 |
| `incr(key)` | 计数器 | 限流计数 |
| `zadd(key, member, score)` | **自动排序的 Set**（每个成员带分数，按分数排序） | 热门榜 |
| `set key value NX PX` | "不存在才 put"+定时 | 分布式锁 |

每一样都不比 `HashMap` 难。V3 引入 Redis 的全部理由，就是**读多写少的场景用内存挡住数据库**（商品详情）、**热点写用计数器扛住**（点赞/浏览）。

> 💡 **心智模型②**：Redis 是**旁路加速层**，不是数据库的替代。一切设计围绕一条铁律：**Redis 挂了，业务变慢，但绝不能不可用**（第 2 章的门面就是这条铁律的代码化）。

## 0.3 一个绕不开的现实约束（决定第 4~7 章的形态）

真实支付宝/微信沙箱需要**商户资质**（APPID、应用私钥、支付宝公钥；微信还要商户号+APIv3 密钥），个人学习项目拿不到。如果代码硬依赖 SDK，没有凭证整套链路跑不起来——你只能"看代码想象"，无法验证。

本项目延续可插拔传统（voice 降级 Edge-TTS、storage 降级本地、MQ 降级线程池）：抽一个 [PayChannel](V3支付与Redis详解【知识词典】.md#paychannel) 接口，默认实现 `MockPayChannel`——**"模拟"的只有"第三方服务器不存在"这一件事**，签名/验签/回调/幂等/状态机全是生产级真代码。配了凭证再实现 `AlipaySandboxChannel`，业务代码零改动。

## 0.4 高频术语速查表（全书通用）

| 术语 | 一句话 | 详见词典 |
|---|---|---|
| 支付单 | 一次"发起支付"的凭证，幂等的锚点 | [支付单](V3支付与Redis详解【知识词典】.md#支付单) |
| 回调 | 第三方付完款后主动 POST 你的接口 | [异步回调](V3支付与Redis详解【知识词典】.md#异步回调) |
| 验签 | 用密钥验证"报文来自对方且未被改" | [验签](V3支付与Redis详解【知识词典】.md#验签) |
| 幂等 | 同一操作执行 N 次 = 执行 1 次 | [消费端幂等](V2内容社区与RAG详解【知识词典】.md#消费端幂等) |
| 状态机 CAS | UPDATE 带 WHERE status=期望，输家拿 0 行 | [状态机 CAS](V3支付与Redis详解【知识词典】.md#状态机-cas) |
| TTL | key/消息的存活时间 | [TTL](V3支付与Redis详解【知识词典】.md#ttl) |
| DLX | 死信交换机：过期/被拒消息的去处 | [死信队列再认识](V3支付与Redis详解【知识词典】.md#死信队列再认识) |
| Lua 脚本 | Redis 里原子执行的小脚本 | [Lua 脚本原子性](V3支付与Redis详解【知识词典】.md#lua-脚本原子性) |
| zset | 带分数自动排序的集合 | [zset](V3支付与Redis详解【知识词典】.md#zset) |

## 本章动手作业

不写代码，只回答三个问题（答不上来就重读本章）：
1. 为什么说"你在 V1 已经写过 90% 的支付代码"？剩下 10% 是什么？
2. 用户在支付宝页面付款的那一刻，你的服务器在干什么？（提示：什么都不干）
3. 如果 Redis 挂了，商品详情接口应该是什么表现——报错、变慢、还是挂掉？

---

# 第 1 章 Redis 5 分钟入门（真·0 基础）

> 核心机制：[Redis 为什么快](V3支付与Redis详解【知识词典】.md#redis-为什么快) · [单线程模型](V3支付与Redis详解【知识词典】.md#单线程模型) · [zset](V3支付与Redis详解【知识词典】.md#zset)

## 1.1 它是什么，为什么快

Redis = **RE**mote **DI**ctionary **S**erver（远程字典服务）。两个关键词：

- **字典**：内部就是一堆 key-value，跟 `HashMap` 同构；
- **远程**：跑在独立进程（你虚拟机的 192.168.6.102:6381），你的 Java 程序通过网络读写它。

快的原因只有两条，别的都是衍生：
1. **数据全在内存**——内存随机读写 ~100ns，磁盘寻道 ~10ms，差 10 万倍；
2. **单线程处理命令**——没有锁竞争、没有上下文切换。你可能会问"单线程怎么能快"？因为瓶颈根本不在 CPU，在网络 IO 和内存速度上；而单线程换来了**每个命令天然原子**（第 9 章 Lua 靠的就是这条）。

## 1.2 用 redis-cli 手玩一遍（强烈建议真做）

**这 6 条命令覆盖了本项目用到的全部 Redis 能力**：

```
127.0.0.1:6381> SET product:1001 "iPhone15" EX 1800      # 存 key，1800 秒后自动删除（缓存）
OK
127.0.0.1:6381> GET product:1001
"iPhone15"
127.0.0.1:6381> INCR rate:chat:user:1                    # 计数器 +1（限流）
(integer) 1
127.0.0.1:6381> SET lock:rebuild:1001 "uuid-abc" NX PX 3000   # 不存在才写，3000ms 过期（分布式锁）
OK
127.0.0.1:6381> ZADD hot:notes 999 "noteId:9527"         # 带分数插入有序集合（热门榜）
(integer) 1
127.0.0.1:6381> ZREVRANGE hot:notes 0 9                  # 按分数从高到低取前 10
```

看到没有——**没有表、没有 SQL、没有 JOIN**。每条命令都是一个 O(1) 或 O(logN) 的原子操作。`EX`/`PX`/`NX` 这些后缀是命令的"修饰符"，组合出过期、互斥这些语义。

## 1.3 Java 侧的对应物：RedisTemplate

Spring Data Redis 给你一个类型安全的操作柄：

```java
stringRedisTemplate.opsForValue().get(key);                    // GET
stringRedisTemplate.opsForValue().increment(key);              // INCR
stringRedisTemplate.opsForValue().setIfAbsent(key, val, ttl);  // SET NX PX
stringRedisTemplate.opsForZSet().add(key, member, score);      // ZADD
```

`opsForValue()` / `opsForZSet()` 就是"按数据结构分组的方法集"。**你不需要记 API**——记住 redis-cli 的 6 条命令，Java 侧无非是它们的驼峰拼写。

## 本章动手作业

连上你的虚拟机亲手跑一遍 1.2 的 6 条命令（`redis-cli -h 192.168.6.102 -p 6381 -a 123456`），然后回答：`SET lock:1 "a" NX PX 3000` 执行两次，第二次返回什么？（答：nil——第二次 key 已存在，NX 让它拒绝写入。这就是"抢锁"。）

---

# 第 2 章 把 Redis 接进项目：配置、序列化器、可降级门面

> 核心机制：[缓存旁路](V3支付与Redis详解【知识词典】.md#缓存旁路) · [RedisTemplate 序列化](V3支付与Redis详解【知识词典】.md#redistemplate-序列化) · [连接池参数](V3支付与Redis详解【知识词典】.md#连接池参数)

## 2.1 依赖与配置：逐行读

```xml
<!-- pom.xml：一行依赖，Boot 自动装配连接工厂、模板 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:192.168.6.102}   # 你的虚拟机；环境变量可覆盖（docker 部署时改成 redis 服务名）
      port: ${REDIS_PORT:6381}
      password: ${REDIS_PASSWORD:123456}
      timeout: 3000ms                     # 单次命令超时：3 秒拿不到结果就当它挂了
      lettuce:
        pool:                             # 连接池——与线程池同一套资源治理哲学
          max-active: 16                  # 最多 16 条连接：Redis 也扛不住无限连接
          max-idle: 8
          min-idle: 2                     # 常驻 2 条：避免突发流量现建连接的延迟毛刺
          max-wait: 2000ms                # 拿不到连接等 2 秒就失败，不无限堆积
```

对照 V1.5 的 [AsyncConfig 线程池](V1.5工程基建详解.md)记：**有界 + 等待上限 + 拒绝无界堆积**——资源治理的通用范式，本项目第三次出现。

**一个关键认知**：即使 Redis 连不上，应用也**照常启动**——Lettuce 是懒连接（首次使用才建连）。这为"可降级"提供了物理基础。

## 2.2 序列化器：新手 100% 会撞的坑

### 先撞南墙

你把一个 `ProductVO` 存进 Redis，然后去 redis-cli 想看一眼：

```
127.0.0.1:6381> GET aimall:product:1001
"\xac\xed\x00\x05sr\x00\x1acom.aimall.goods.dto.ProductVO..."
```

**乱码**。这是 `RedisTemplate` 默认的 [JDK 序列化](V3支付与Redis详解【知识词典】.md#redistemplate-序列化)——`ObjectOutputStream` 把对象连同**类全限定名**一起变成二进制。三宗罪：

| 罪状 | 现场还原 |
|---|---|
| **不可读** | redis-cli 全是 `\xac\xed`，线上排查只能靠猜 |
| **类名强耦合** | 字节里写死了 `com.aimall.goods.dto.ProductVO`——重构改个包名，**存量缓存全部反序列化失败**（`ClassNotFoundException`） |
| **反序列化漏洞** | Java 反序列化 RCE 一族事故的温床（攻击者构造恶意类字节流，反序列化时执行任意代码） |

### 正确姿势：JSON 序列化

`RedisConfig.java`（逐段讲）：

```java
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);

    // key 用字符串序列化：可读、与其他客户端兼容
    StringRedisSerializer stringSerializer = new StringRedisSerializer();

    // value 用 JSON：ObjectMapper 需要三处定制
    ObjectMapper om = new ObjectMapper();
    om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    om.registerModule(new JavaTimeModule());       // ① 支持 LocalDateTime（不配会序列化失败！）
    om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL); // ② 写入类型信息，取出才能还原成 ProductVO
    GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(om);

    template.setKeySerializer(stringSerializer);   // ③ key 与 value 序列化器分开设
    template.setValueSerializer(jsonSerializer);
    template.afterPropertiesSet();
    return template;
}
```

三个细节：
- **①JavaTimeModule**——不注册它，`LocalDateTime` 直接序列化报错（Jackson 不认 JSR-310），缓存含时间字段的对象时必撞；
- **②activateDefaultTyping**——把类型信息写进 JSON（`{"@class":"com.aimall...ProductVO", ...}`），取出时才能还原成原类型而不是一坨 `LinkedHashMap`；
- **③key 永远是纯字符串**——否则 SCAN/DEL/管理台肉眼排查全废。

与 V2 的 MQ `Jackson2JsonMessageConverter` 是**同一个思想**：跨进程/跨存储的数据一律 JSON，不用语言专属二进制。

**两个模板各司其职**：`StringRedisTemplate`（值本来就是字符串：计数/锁/限流/zset 成员）+ `RedisTemplate<String,Object>`（缓存对象）。

## 2.3 RedisOps 门面：把"降级"收敛到一处（本章核心）

### 先想清楚一个问题

假设你在业务代码里裸写：

```java
ProductVO vo = redisTemplate.opsForValue().get(key);   // Redis 断连 → 这里抛异常
```

Redis 一断连，**所有走过这行代码的接口全部 500**——缓存反而成了故障放大器。这违背了第 0 章的心智模型②（旁路：挂了只能变慢）。

### 门面的写法

`common/redis/RedisOps.java`——所有 Redis 调用收敛于此，每个方法都被 `safe()` 包装：

```java
private <T> T safe(Supplier<T> supplier, T fallback, String op, String key) {
    try {
        T v = supplier.get();
        return v == null ? fallback : v;
    } catch (Exception e) {
        // WARN 不是 ERROR——降级是设计内的路径，不是故障
        log.warn("Redis 不可用，降级 op={} key={}: {}", op, key, e.getMessage());
        return fallback;
    }
}
```

业务侧的观感：

```java
public Optional<String> get(String key) {
    return safe(() -> Optional.ofNullable(stringRedisTemplate.opsForValue().get(key)),
            Optional.empty(), "get", key);      // Redis 挂 → Optional.empty() → 调用方自然走 DB
}
```

### ★ 降级默认值必须逐方法按业务语义选（面试高频）

这是本类**最容易被问**的设计点——`fallback` 不是随便填的：

| 方法 | fallback | 语义 | 为什么 |
|---|---|---|---|
| `get`（缓存读） | 空 | "缓存未命中" | 调用方查库，变慢但正确 |
| `incr`（计数） | null | "计数器不可用" | 调用方改走 DB 计数 |
| `tryLock`（锁） | null | "没抢到锁" | 调用方走兜底路径（如直接查库） |
| `allow`（限流） | **true（放行）** | "限流组件失效" | **[fail-open](V3支付与Redis详解【知识词典】.md#降级方向)**：限流是省钱不是保命，拒绝所有用户=自己制造全站故障 |
| `zTop`（榜单） | 空列表 | "榜单暂不可用" | 展示型功能，缺了不伤主流程 |

一句话总结：**没有统一的"降级=关掉"，每个组件要想清楚"它挂了业务该怎么活"**。

### 门面里值得单独点名的两个方法

**分布式锁**（第 3 章缓存击穿要用）：

```java
// 加锁：SET key value NX PX ttl —— 一条命令完成"不存在才写 + 过期时间"
public String tryLock(String lockKey, long ttlMillis) {
    String value = UUID.randomUUID().toString();     // value 是"锁的主人"标识
    Boolean ok = safe(() -> stringRedisTemplate.opsForValue()
            .setIfAbsent(lockKey, value, ttlMillis, TimeUnit.MILLISECONDS),
            false, "tryLock", lockKey);
    return Boolean.TRUE.equals(ok) ? value : null;   // 返回 null = 没抢到（正常分支，不是异常！）
}

// 解锁：Lua 保证"校验 value 再删"的原子性
private static final String UNLOCK_LUA =
    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

public void unlock(String lockKey, String lockValue) {
    safe(() -> stringRedisTemplate.execute(
            new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
            Collections.singletonList(lockKey), lockValue), 0L, "unlock", lockKey);
}
```

两个必考细节：
1. **为什么 value 存 UUID**——标识"这把锁是我的"。释放时校验，防[锁误删](V3支付与Redis详解【知识词典】.md#锁误删)：A 的锁超时自动释放后，A 执行到 finally 把 B 刚拿到的锁删了。
2. **为什么解锁必须 Lua**——"GET 比对 + DEL"是两步，中间可能插进别人的操作；Lua 让两步变一步（原子）。第 9 章系统讲 Lua。

**通用 Lua 执行器**（第 9、10 章都靠它）：

```java
public Long eval(String script, List<String> keys, String... args) {
    return safe(() -> stringRedisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class), keys, (Object[]) args),
            null, "eval", String.join(",", keys));
}
```

## 本章动手作业

1. 停掉虚拟机的 Redis，启动后端——观察应用**照常启动**（懒连接），调商品详情接口**照常返回**，日志刷 `Redis 不可用，降级` 的 WARN——亲眼见证"旁路"哲学。
2. 把 Redis 起回来再调一次，确认 WARN 消失。
3. 思考：如果把 `allow()` 的 fallback 改成 `false`（拒绝），Redis 一挂会发生什么？（答：所有 AI 问答接口全被拒绝——限流组件把自己变成了单点故障。）

---

# 第 3 章 缓存三兄弟：从一次 DB 被打挂的事故讲起

> 核心机制：[缓存穿透](V3支付与Redis详解【知识词典】.md#缓存穿透) · [缓存击穿](V3支付与Redis详解【知识词典】.md#缓存击穿) · [缓存雪崩](V3支付与Redis详解【知识词典】.md#缓存雪崩) · [空值缓存](V3支付与Redis详解【知识词典】.md#空值缓存) · [互斥锁重建](V3支付与Redis详解【知识词典】.md#互斥锁重建) · [布隆过滤器](V3支付与Redis详解【知识词典】.md#布隆过滤器) · [逻辑过期](V3支付与Redis详解【知识词典】.md#逻辑过期) · [缓存与DB一致性](V3支付与Redis详解【知识词典】.md#缓存与-db-一致性)

## 3.1 为什么商品详情需要缓存

数据画像：商品详情**读多写少**（用户刷 100 次页面，商家才改 1 次价格）。每次读都走：

```
浏览器 → 后端 → MySQL(解析SQL→走索引→磁盘读行→组装VO) → 返回
```

MySQL 单机几千 QPS 就开始喘。而缓存路径：

```
浏览器 → 后端 → Redis(内存 GET，10万+ QPS) → 命中直接返回，不碰 MySQL
```

**命中率 95% 意味着 MySQL 压力直接降到 1/20**。这是缓存存在的全部理由。

## 3.2 先写最朴素的版本（以及它会怎么死）

0 基础的第一版缓存通常是：

```java
public ProductVO detail(Long id) {
    ProductVO vo = redis.get("product:" + id);
    if (vo != null) return vo;             // 命中，返回
    vo = productService.detail(id);        // 未命中，查库
    redis.set("product:" + id, vo, 1800);  // 回写，30 分钟
    return vo;
}
```

这段代码**功能正确**，在生产却会以三种方式死去——就是"缓存三兄弟"。本章按"事故现场 → 对策 → 逐行代码"的节奏逐个讲。

## 3.3 老大·穿透：查一个根本不存在的 id

**事故现场**：攻击者（或写错的爬虫）狂刷 `GET /products/-1`。id=-1 的商品**不存在**，所以：
- 缓存永远不命中（上面这段代码只在"查到东西"时才回写）；
- 每个请求都打到 MySQL。

100 QPS 的恶意流量 = MySQL 平白多扛 100 QPS 全无效查询。**缓存对"不存在的 key"形同虚设**——请求"穿"过缓存直插 DB，故名穿透。

**对策一（本项目采用）：[空值缓存](V3支付与Redis详解【知识词典】.md#空值缓存)**——查不到也写个标记：

```java
private static final String NULL_MARK = "__NULL__";     // 特殊标记串
private static final long NULL_TTL = 2 * 60;            // 只缓存 2 分钟

private ProductVO loadAndCache(Long productId, String key) {
    ProductVO vo = productService.detail(productId);
    if (vo == null) {
        redisOps.set(NULL_MARK_KEY(productId), "1", NULL_TTL);   // ★ 查不到也写！
        return null;
    }
    long ttl = BASE_TTL + ThreadLocalRandom.current().nextInt(TTL_JITTER);  // 雪崩对策，3.5 讲
    redisOps.setObject(key, vo, ttl);
    return vo;
}
```

读的时候先查空值标记：

```java
if (redisOps.get(NULL_MARK_KEY(productId)).isPresent()) {
    return null;      // "确认不存在"，直接返回，不再查库
}
```

**两个细节问答**：
- *为什么 TTL 只给 2 分钟而不是 30 分钟？* 商品可能下一秒就上架。空值缓存 30 分钟 = 上架后半小时用户都查不到（脏读）。2 分钟是"挡刷子"与"不脏读"的平衡。
- *为什么用特殊串 `__NULL__` 而不是存 null？* 很多 Redis 客户端不存 null 值；且"key 存在但值是标记"和"key 不存在"是两种明确可区分的状态。

**对策二（进阶，知道即可）：[布隆过滤器](V3支付与Redis详解【知识词典】.md#布隆过滤器)**——用极小内存回答"这个 id **一定不存在**吗？"。海量 key（百万级商品）才需要；本项目 100 商品，空值缓存绰绰有余。

## 3.4 老二·击穿：热点 key 过期的瞬间

**事故现场**：某爆款商品的缓存到期。**同一个毫秒**内 5000 个请求涌进来，全部发现缓存未命中（旧的已被删除、新的还没写入）→ 5000 个请求**同时**查 MySQL 并回写。

单个查询没什么，但 5000 个并发同时落库——MySQL 被打出一个尖刺。击穿 = **一个点**被击穿。

**对策（本项目）：[互斥锁重建](V3支付与Redis详解【知识词典】.md#互斥锁重建)**——同一 key 的重建只让 1 个线程做，其他人等。

`ProductCacheService.detailWithCache` 完整逻辑（逐行精读）：

```java
public ProductVO detailWithCache(Long productId) {
    String key = KEY_PREFIX + productId;

    // ---- 1. 读缓存 ----
    var cached = redisOps.getObject(key, ProductVO.class);
    if (cached.isPresent()) {
        return cached.get();                    // 命中：99% 的请求走到这就结束了
    }
    // 命中"空值标记"：确认不存在，直接返回（穿透对策）
    if (redisOps.get(NULL_MARK_KEY(productId)).isPresent()) {
        return null;
    }

    // ---- 2. 未命中：抢锁重建 ----
    String lockKey = LOCK_PREFIX + productId;
    String lockValue = redisOps.tryLock(lockKey, 3000);   // SET NX PX 3秒
    try {
        if (lockValue != null) {
            // 赢家：只有我一个线程去查库回写
            return loadAndCache(productId, key);
        }
        // 输家：别人正在重建，等 50ms 再读一次缓存
        sleepQuietly(50);
        var retry = redisOps.getObject(key, ProductVO.class);
        if (retry.isPresent()) {
            return retry.get();
        }
        // 兜底：仍读不到就直接查库——缓存不能决定可用性（等锁不能等成故障）
        return productService.detail(productId);
    } finally {
        if (lockValue != null) {
            redisOps.unlock(lockKey, lockValue);   // Lua 校验 value 再删
        }
    }
}
```

**逐个回答你可能有的疑问**：

- *为什么锁的 TTL 是 3 秒？* 防止赢家查库途中崩溃导致死锁——3 秒后锁自动释放。代价是"赢家超 3 秒没干完，锁没了别人也能进"——可接受（最坏退化成无锁，不会错）。
- *输家为什么 sleep 50ms 而不是一直等？* 赢家查库+回写通常 <50ms；等太久请求堆积。**等待要有上限**——与线程池 max-wait 同哲学。
- *输家兜底为什么敢直接查库？* 击穿的本质是"瞬时尖刺"。输家直接查库只是把尖刺从 5000 降到几十（等完锁还在的少数），可控。
- *另一条路呢？* [逻辑过期](V3支付与Redis详解【知识词典】.md#逻辑过期)：key 永不过期、值里带过期时间，过期后返回旧值+异步重建——无锁无等待，但有短暂脏读。适合极致热点；本项目互斥锁够用且好讲。

## 3.5 老三·雪崩：集体失效

**事故现场**：批量导入 100 个商品，代码统一给了 TTL=1800 秒 → **30 分钟后同一秒全部过期** → 那一秒的未命中流量全部落库。击穿是"一个点"，雪崩是"一整面"塌下来。（另一种雪崩是 Redis 整体宕机——对策是哨兵/集群，超出本项目，面试提一句即可。）

**对策：TTL 随机抖动**——一行代码的事：

```java
long ttl = BASE_TTL + ThreadLocalRandom.current().nextInt(TTL_JITTER);
// 30~40 分钟之间随机：失效点被打散在 10 分钟的窗口里
```

**零成本、收益巨大**——雪崩三板斧（抖动/多级缓存/高可用）里最便宜的一板。

## 3.6 缓存与 DB 的一致性：先更库再删缓存

商品改价了，缓存怎么办？本项目的写路径：

```
UPDATE DB → DELETE cache        （不是"更新缓存"！）
```

- *为什么删而不是更？* 并发写时"更新缓存"的顺序可能颠倒（A 先到库后到缓存，被 B 反超覆盖），旧值复活。**删除是幂等的**——谁删都一样，下次读自然重建。
- *为什么先库后缓存？* 反过来（先删缓存再更库）的话，两步之间有读请求进来会把**旧值**重新载入缓存。先更库再删缓存也有极小的不一致窗口（读者读到旧缓存），商品详情场景可接受——**追求强一致就别用缓存**。
- 进阶方案（延迟双删/订阅 binlog 的 Canal）知道名字即可，本项目量级用不上。

## 3.7 接线位置：为什么在 Controller 层

`ProductRestController.detail` 调 `detailWithCache` 而不是直接调 `productService.detail`。**为什么不把缓存写进 Service？**

```java
// 若 ProductServiceImpl.detail 内部调 ProductCacheService
// 而 ProductCacheService 又依赖 ProductService（查库兜底）
// → 构造器循环依赖 → Boot 3 默认禁止 → 启动失败
```

V2 就踩过同款（AuditServiceImpl ↔ NoteServiceImpl）。缓存是"读路径的装饰"，放 Controller 依赖注入天然无环，而且一眼可见"这个接口有缓存"。

## 本章动手作业

1. `GET /api/v1/products/1001` 连调两次，对比日志：第二次没有 SQL（或开 `redis-cli MONITOR` 看第二次只有 GET）。
2. `redis-cli DEL aimall:product:1001` 删掉缓存再查，观察互斥重建。
3. 查一个不存在的 id（如 99999）两次，第二次 `KEYS aimall:product:null:*` 应能看到空值标记。
4. 停 Redis 再查商品——接口照常（旁路），日志刷 WARN。
5. 思考题：把 BASE_TTL 和 JITTER 都设为 0 会发生什么？（答：所有缓存同一秒过期——雪崩。JITTER 就是防它的。）

---

# 第 4 章 支付前传：支付单表、状态机、PayChannel 抽象

> 核心机制：[支付单](V3支付与Redis详解【知识词典】.md#支付单) · [状态机 CAS](V3支付与Redis详解【知识词典】.md#状态机-cas) · [PayChannel](V3支付与Redis详解【知识词典】.md#paychannel)

## 4.1 先撞南墙：为什么不在订单上加个"已支付"标记

0 基础的第一反应：订单表加个 `paid` 字段，回调来了 UPDATE 一下，完事。三个结构性问题：

| 问题 | 现场还原 |
|---|---|
| **幂等粒度错** | 一次订单可能多次发起支付（第一次超时后重付）——"哪一次支付成功了"标记在订单上说不清 |
| **对账无锚点** | 第三方交易号（`trade_no`）、回调原始报文放哪？塞订单表 → 订单表膨胀成大杂烩 |
| **状态耦合** | 订单有自己的生命周期（待支付→已支付→已发货→已完成），支付也有（PAYING→PAID→CLOSED）——两者**不同步演进**（订单取消了，支付单可能还是 PAYING） |

**正确思路**：订单管交易，支付单管资金，各一张表、各一个状态机。

## 4.2 t_payment 逐字段读（Flyway V3 迁移）

```sql
CREATE TABLE t_payment (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    payment_no     VARCHAR(32)   NOT NULL COMMENT '支付单号(业务唯一，幂等核心)',
    order_id       BIGINT        NOT NULL,
    order_no       VARCHAR(32)   NOT NULL COMMENT '冗余：对账与日志排查用',
    user_id        BIGINT        NOT NULL COMMENT '冗余：回调时无需回查订单即可鉴权',
    amount         DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    channel        VARCHAR(20)   NOT NULL COMMENT 'MOCK/ALIPAY/WECHAT',
    status         VARCHAR(20)   NOT NULL DEFAULT 'PAYING',
    third_trade_no VARCHAR(64)   DEFAULT NULL COMMENT '第三方交易号(回调带来)',
    callback_body  TEXT          COMMENT '回调原始报文(留痕)',
    paid_time      DATETIME      DEFAULT NULL,
    expire_time    DATETIME      DEFAULT NULL COMMENT '支付单过期时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),      -- ★ 幂等的物理兜底
    KEY idx_order (order_id)
);
```

三个"冗余"字段（order_no/user_id）都是**为回调那一刻服务**的：回调进来只查支付单就能完成归属校验与金额比对，不用 join 订单表。`callback_body` 留痕是为了出纠纷时能把"当时对方到底发了什么"翻出来——**支付系统的每一次外部交互都要留证据**。

## 4.3 状态机：Payment 的常量设计

```java
public class Payment {
    public static final String STATUS_PAYING  = "PAYING";   // 已创建，等用户付款
    public static final String STATUS_PAID    = "PAID";     // 回调验签通过并完成流转
    public static final String STATUS_CLOSED  = "CLOSED";   // 超时/取消/订单取消
    public static final String STATUS_FAILED  = "FAILED";   // 第三方明确返回失败
}
```

合法流转只有三条边：`PAYING→PAID`（回调）、`PAYING→CLOSED`（超时/取消）、`PAYING→FAILED`。**PAID 是终态**——绝不回退。

## 4.4 PayChannel：为什么是这三个方法

任何支付网关（支付宝/微信/银联/Stripe）接入都逃不出三件事：

```java
public interface PayChannel {
    String code();                                                          // 渠道标识 MOCK/ALIPAY
    CashierInfo create(String paymentNo, BigDecimal amount, String subject); // ① 下单换收银台
    boolean verifyCallback(Map<String, String> params);                     // ② 验签
    QueryResult query(String paymentNo);                                    // ③ 主动查单
}
```

- **create**：本地支付单 → 第三方支付参数/收银台 URL。真实支付宝这里是调 SDK 的预下单接口；
- **verifyCallback**：验回调签名。每个渠道算法不同（支付宝 RSA2、微信 APIv3），但"输入参数 Map、输出 boolean"的形状相同；
- **query**：主动查单对账（第 7 章讲为什么必须有它）。

**这就是可插拔的全部含义**：`MockPayChannel` 与未来的 `AlipaySandboxChannel` 是平行的两颗螺丝，`PaymentService` 只认接口。与 StorageService（local/minio）、EmbeddingClient（local/spring-ai）同一族设计——你的项目里这个模式已经出现**第三次**，面试可以把它讲成你的设计哲学。

## 4.5 MockPayChannel 逐段精读

```java
@Component
public class MockPayChannel implements PayChannel {

    @Value("${aimall.pay.mock.secret:aimall-mock-secret}")
    private String secret;         // HMAC 密钥（生产环境用环境变量注入）

    @Override
    public CashierInfo create(String paymentNo, BigDecimal amount, String subject) {
        // 真实渠道：调 SDK 拿支付页面 URL
        // 模拟渠道：拼一个本地"收银台"地址，前端据此弹模拟收银台
        String params = "paymentNo=" + paymentNo + "&amount=" + amount.toPlainString();
        return new CashierInfo(cashierUrl + "?" + params, CODE, params);
    }
    // verifyCallback 见第 5 章，query 见第 7 章
}
```

注意 `@Value` 的默认值写法 `${aimall.pay.mock.secret:aimall-mock-secret}`——冒号后是缺省，保证零配置也能跑。

## 本章动手作业

1. 下一单（不支付），查库看 `t_payment`：status=PAYING、expire_time=+30min、amount 与订单 total_amount 一致。
2. 思考：为什么 `uk_payment_no` 是唯一索引而不是普通索引？（答：幂等的物理兜底——重复创建同一单号直接被数据库拒绝。第 7 章展开。）
3. 说出支付单与订单的状态在哪一步"分道扬镳"。（提示：订单 CANCELLED 时支付单可能是 CLOSED 也可能是 PAID——后者要走退款。）

---

# 第 5 章 签名与验签：0 基础手撕 HMAC

> 核心机制：[哈希函数](V3支付与Redis详解【知识词典】.md#哈希函数) · [HMAC-SHA256](V3支付与Redis详解【知识词典】.md#hmac-sha256) · [RSA2](V3支付与Redis详解【知识词典】.md#rsa2) · [时序攻击](V3支付与Redis详解【知识词典】.md#时序攻击)

## 5.1 先撞南墙：不验签的世界

假设你的回调接口只做了"取参数、改状态"：

```java
@PostMapping("/callback/MOCK")
public String callback(@RequestParam Map<String, String> params) {
    paymentService.markPaid(params.get("payment_no"));   // 无验签版
    return "success";
}
```

现在任何人在任何地方执行：

```bash
curl -X POST "http://你的公网IP:8080/api/v1/payments/callback/MOCK?payment_no=P123"
```

**这单就被"支付"了**。攻击者可以白嫖任何订单（改状态不付钱）、篡改金额（1 分钱买 399）、伪造退款……你的回调接口 = 向全世界开放的"改单入口"。

所以回调处理的**第一件事永远是验签**——回答"这条报文真的来自第三方、且中途没被改过一个字吗？"

## 5.2 从哈希到 HMAC：三步演进（0 基础）

**第一步：普通哈希**。`SHA256("amount=399&payment_no=P123")` = 一串指纹。收到报文后重算指纹比对——能发现"报文被改"（改一个字指纹全变）。**但攻击者也知道你的拼接规则**：他改完 amount 重新拼一遍、重新算个哈希附上，照样骗过你。缺的是"只有双方知道的秘密"。

**第二步：加盐哈希**。双方约定密钥 `secret`，算 `SHA256(明文 + secret)`。攻击者不知道 secret，改了报文就伪造不出正确哈希。**但密码学上这种手搓拼接有已知弱点**（长度扩展攻击），不能自己发明。

**第三步：HMAC（Hash-based Message Authentication Code）**。标准化的"带密钥哈希"构造，内部用两轮哈希巧妙嵌套密钥，免疫长度扩展攻击。Java 三行：

```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(key.getBytes(UTF_8), "HmacSHA256"));
byte[] bytes = mac.doFinal(data.getBytes(UTF_8));   // 输出 32 字节，转 64 位十六进制串
```

**一句话：HMAC = 篡改检测（哈希）+ 身份证明（密钥）的工程化合体**。支付宝回调报文里的 `sign` 字段就是干这个的。

## 5.3 sign() 逐行精读：那个排序的坑

```java
public String sign(Map<String, String> params) {
    Map<String, String> sorted = new TreeMap<>(params);      // ① 按 key 字典序排序
    List<String> parts = new ArrayList<>();
    sorted.forEach((k, v) -> {
        if (!SIGN_PARAM.equals(k) && v != null && !v.isBlank()) {   // ② sign 自己不参与
            parts.add(k + "=" + v);
        }
    });
    String plain = String.join("&", parts);                  // ③ 拼接 k=v&k=v
    return hmacSha256(plain, secret);                        // ④ HMAC
}
```

**①为什么必须 TreeMap 排序**——新手必踩的坑：`HashMap` 的遍历顺序**不稳定**（由 hash 桶决定），同样的参数两次遍历可能拼出 `a=1&b=2` 和 `b=2&a=1`——**签名时对时错**，是那种"本地好好的、一上线随机失败"的诡异 bug。各家支付网关文档都写"参数按 ASCII 升序拼接"，原因就在这。

**②为什么 sign 不参与签名**——签名不能签自己（循环依赖）。验签方拿到报文后把 sign 摘出来、用剩余参数重算，再比对。

验签侧：

```java
@Override
public boolean verifyCallback(Map<String, String> params) {
    String sign = params.get(SIGN_PARAM);
    if (sign == null || sign.isBlank()) return false;
    String expected = sign(params);                          // 同一算法+同一密钥重算
    // ★ constant-time 比较，防时序攻击（见 5.4）
    return MessageDigest.isEqual(expected.getBytes(UTF_8), sign.getBytes(UTF_8));
}
```

## 5.4 时序攻击：为什么不用 equals

用 `expected.equals(sign)` 有一个极隐蔽的漏洞：`equals` 碰到第一个不同字符**立即返回 false**——比对耗时与"正确前缀长度"成正比。攻击者据此**逐字节爆破**：先试 256 种首字符，哪个响应慢 1μs 哪个就对；再试第二位……这就是[时序攻击](V3支付与Redis详解【知识词典】.md#时序攻击)。

`MessageDigest.isEqual` 是 constant-time 比较——无论差异在第几位，耗时恒定。**安全代码的细节就是魔鬼**，这一行是加分项。

## 5.5 HMAC vs RSA2：对称与非对称

| | HMAC-SHA256（本项目 Mock） | RSA2（真实支付宝） |
|---|---|---|
| 密钥 | 收发双方**共享**同一个 | 我方私钥签 / 对方公钥验（**不共享**） |
| 适合 | 自建渠道（收发都是自己） | 多方互信（我方 ↔ 支付宝） |
| 泄露面 | 密钥给谁谁就能伪造 | 私钥只在自己手里 |

换 RSA2 只需替换 `hmacSha256`/`verifyCallback` 两个私有方法——接口形状不变，这就是抽象的价值。

## 本章动手作业

1. curl 直接 POST `/api/v1/payments/callback/MOCK` 带假签名 → 返回 `sign-error`，日志出现"疑似伪造请求"。
2. 思考：同一组参数用 HashMap 拼 100 次签名，为什么"看起来总是对的"却仍然是 bug？（答：HashMap 顺序**语义上不保证**，换数据量/换 JDK/扩容后顺序会变——工程不能赌概率。）
3. 为什么支付回调验签用"对方的公钥"（RSA2）而不是"我方的 token"？（答：token 证明"我的用户是谁"，验签证明"消息是谁发的、有没有被改"——两个问题两套答案。）

---

# 第 6 章 回调：一次支付通知的完整旅程

> 核心机制：[异步回调](V3支付与Redis详解【知识词典】.md#异步回调) · [公网端点的身份模型](V3支付与Redis详解【知识词典】.md#公网端点的身份模型) · [状态机 CAS](V3支付与Redis详解【知识词典】.md#状态机-cas)

## 6.1 全链路时序（先把全景刻进脑子）

```
 用户                    后端(8080)                    第三方(MOCK)
  │  下单                  │                              │
  │──────────────────►   │ 订单=PENDING_PAY             │
  │                       │ (afterCommit 投递延迟消息)    │
  │  点"去支付"           │                              │
  │──────────────────►   │ 建支付单(PAYING,30min过期)    │
  │  ◄────收银台URL────── │                              │
  │                       │                              │
  │  付款                  │                              │
  │─────────────────────────────────────────────────────►│
  │                       │   ◄────异步回调(带sign)──────│
  │                       │ ①验签 ②定位 ③幂等快路径     │
  │                       │ ④金额核对 ⑤支付单CAS        │
  │                       │ ⑥订单CAS: PENDING_PAY→PAID  │
  │                       │ ────回"success"────────────►│
  │  查订单=已支付         │                              │
```

## 6.2 Controller：三个反直觉的设计

```java
@PostMapping("/callback/{channel}")
public String callback(@PathVariable String channel,
                       @RequestParam Map<String, String> params) {
    try {
        CallbackResult r = paymentService.handleCallback(channel, params);
        return r.message();               // success 或 错误码
    } catch (Exception e) {
        log.error("支付回调处理异常 channel={}", channel, e);
        return "system-error";            // ★ 异常也返回字符串，永不抛出
    }
}
```

**① 返回 String 而不是 R\<Void\>**——这是给**机器**（支付宝服务器）看的接口，不是给前端看的。支付宝的约定：收到字面量 `success` 才停止重发；其他任何内容（包括 500 状态码）都会触发重试（8 次以上）。所以方法**永不抛异常**——抛了就是给对方返回 500，等于邀请它无限重试。

**② 不加登录校验，且在 SaTokenConfig 白名单里**：

```java
.excludePathPatterns(
    "/api/v1/payments/callback/**",   // 回调：第三方没有你的用户 token
    ...
)
```

第三方服务器**不可能**持有你用户的登录态。回调的身份模型与业务接口完全不同：业务接口靠 token 回答"你是谁"（认证），回调靠验签回答"消息是谁发的、有没有被改"（完整性）。很多人第一反应"给回调也加个 token"——没想清楚调用方是谁。详见词典[公网端点的身份模型](V3支付与Redis详解【知识词典】.md#公网端点的身份模型)。

**③ 参数用 @RequestParam Map 接**——支付回调是表单编码（k=v&k=v），不是 JSON，与前端习惯相反。

## 6.3 handleCallback 逐段精读（六步全贴）

```java
@Transactional(rollbackFor = Exception.class)
public CallbackResult handleCallback(String channel, Map<String, String> params) {

    // ---------- ① 验签：红线中的红线 ----------
    PayChannel payChannel = router.resolve(channel);
    if (!payChannel.verifyCallback(params)) {
        log.warn("支付回调验签失败，疑似伪造请求 channel={} params={}", channel, params);
        return new CallbackResult(false, null, "sign-error");
        // 验签失败必须拒绝——不存在"为了成功率放行"
    }

    // ---------- ② 定位支付单 ----------
    String paymentNo = router.paymentNoOf(params);
    Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
    if (payment == null) {
        return new CallbackResult(false, paymentNo, "payment-not-found");
    }

    // ---------- ③ 幂等快路径：已支付直接回 success ----------
    if (Payment.STATUS_PAID.equals(payment.getStatus())) {
        log.info("重复支付回调（幂等跳过）paymentNo={}", paymentNo);
        return new CallbackResult(true, paymentNo, "success");
        // ★ 必须回 success：不回的话第三方认为通知失败，永远重发
    }

    // ---------- ④ 金额核对：缺失即拒绝 ----------
    String amountStr = params.get("amount") == null ? params.get("total_amount") : params.get("amount");
    if (amountStr == null) {
        return new CallbackResult(false, paymentNo, "amount-missing");
        // ★ 评审修正：早期写法 amountStr != null 才核对——
        //   "回调不带金额字段"就能绕过核对。缺失本身就是异常。
    }
    BigDecimal paid;
    try {
        paid = new BigDecimal(amountStr);
    } catch (NumberFormatException e) {
        return new CallbackResult(false, paymentNo, "amount-invalid");
    }
    if (paid.compareTo(payment.getAmount()) != 0) {
        log.error("回调金额与支付单不一致 期望={} 回调={}", payment.getAmount(), paid);
        return new CallbackResult(false, paymentNo, "amount-mismatch");
    }

    // ---------- ⑤ 支付单状态机 CAS（幂等第二防线） ----------
    String tradeNo = router.tradeNoOf(params);
    int rows = paymentMapper.updateToPaid(paymentNo, Payment.STATUS_PAYING,
            tradeNo, String.valueOf(params));
    if (rows == 0) {
        // 并发下被别的线程处理了——正常，直接返回成功
        return new CallbackResult(true, paymentNo, "success");
    }

    // ---------- ⑥ 订单状态机 CAS ----------
    int orderRows = orderMapper.updateStatus(payment.getOrderId(),
            Order.STATUS_PENDING_PAY, Order.STATUS_PAID, LocalDateTime.now());
    if (orderRows == 0) {
        // 极端：支付成功瞬间订单已被超时任务取消 → 钱收了货没发 → 退款/人工兜底
        log.error("支付成功但订单流转失败（可能已被取消，需退款）paymentNo={}", paymentNo);
    }
    return new CallbackResult(true, paymentNo, "success");
}
```

对应的 SQL（幂等的核心在 WHERE 里）：

```xml
<update id="updateToPaid">
    UPDATE t_payment
    SET status = 'PAID', third_trade_no = #{thirdTradeNo},
        callback_body = #{callbackBody}, paid_time = NOW()
    WHERE payment_no = #{paymentNo}
    AND status = #{expectStatus}          <!-- ★ 只有 PAYING 才能变 PAID -->
</update>
```

**为什么 ⑤ 用 CAS 而不是"先查再改"**——两个回调**同时**穿过第 ③ 步（都查到 PAYING），先查后改的两个都会执行 UPDATE（第二个可能用旧报文覆盖交易号）；CAS 的第二个拿到 0 行自然落空。**把并发裁决交给数据库的原子性**——这是本项目第四次用同一招（扣库存/审核回写/订单取消/支付流转），一个模式吃遍天下。

**⑥ 为什么 0 行只记日志不回滚**——钱**确实收到了**（第三方已扣款），把支付单也回滚反而制造假账。正确语义：支付单保持 PAID（资金事实），订单已是 CANCELLED（库存事实），差异交给退款流程——**资金状态与业务状态分开记账，差异显式化**，这是支付对账的起点思维。

## 6.4 mock-pay：模拟收银台的闭环设计

模拟渠道没有"第三方页面"，怎么触发回调？`PaymentController.mockPay`：

```java
@PostMapping("/mock-pay/{paymentNo}")
public R<String> mockPay(@PathVariable String paymentNo) {
    StpUtil.checkLogin();
    Payment p = paymentService.detail(paymentNo);
    // 归属校验（评审修正）：不能替别人付款
    if (!p.getUserId().equals(StpUtil.getLoginIdAsLong())) {
        throw new BusinessException(ResultCode.NOT_FOUND, "支付单不存在");
    }
    // ★ 关键设计：不直接改状态！构造带签名的报文，走真实回调链路
    Map<String, String> params = new LinkedHashMap<>();
    params.put("payment_no", p.getPaymentNo());
    params.put("amount", p.getAmount().toPlainString());
    params.put("trade_no", "MOCK" + System.currentTimeMillis());
    params.put("status", "PAID");
    params.put(MockPayChannel.SIGN_PARAM, mockPayChannel.sign(params));

    CallbackResult r = paymentService.handleCallback(MockPayChannel.CODE, params);
    return r.success() ? R.ok("模拟支付成功") : R.fail("处理失败：" + r.message());
}
```

**它自己不碰状态**，而是以"第三方"的身份构造带签名的报文去调 `handleCallback`——验签、幂等、金额核对、CAS 全部真实走过。**模拟的只有"第三方服务器不存在"，工程链路 100% 真**。前端模拟收银台（MockCashier.vue）点"确认支付"就是打的它。

## 本章动手作业

1. 全链路走一遍：下单 → 去支付 → 模拟收银台 → 确认支付 → 订单页变已支付；对照 6.1 时序图在日志里找到六步的每一行。
2. 对同一支付单重放 `POST /api/v1/payments/mock-pay/{no}` 两次，第二次日志出现"幂等跳过"且状态不变。
3. curl 假签名打 `/callback/MOCK` → `sign-error`；把 amount 改小再签（用 MockPayChannel.sign 手动生成）→ `amount-mismatch`。
4. 用别人的账号登录，对他人的支付单调 mock-pay → 404（归属校验）。

---

# 第 7 章 幂等三层 + 主动查单对账

> 核心机制：[幂等回调](V3支付与Redis详解【知识词典】.md#幂等回调) · [主动查单对账](V3支付与Redis详解【知识词典】.md#主动查单对账) · [本地消息表](V3支付与Redis详解【知识词典】.md#本地消息表)

## 7.1 先立事实：重复回调必然发生

新手以为"回调一次就完事"。真实世界：网络超时重传、你的服务器重启的瞬间通知失败、第三方风控重发……支付宝文档明确：**未收到 success 会重试 8 次以上，间隔逐渐拉长**。

所以幂等不是锦上添花，是**支付系统的入场券**。而且注意方向：已支付的重复回调**必须返回 success**（让对方停止重试），只有业务性失败才返回失败让它重发。

## 7.2 三层防线（面试画这张图）

```
                 回调到达
                    │
     ┌──────────────▼───────────────┐
     │ 1. uk(payment_no) 唯一索引     │ ← 物理层：重复"创建"直接被数据库拒绝
     ├──────────────────────────────┤
     │ 2. 已PAID → 回 success 跳过    │ ← 快路径：挡"串行重复"（对方重发，最常见）
     ├──────────────────────────────┤
     │ 3. WHERE status='PAYING' CAS  │ ← 慢路径：挡"并发重复"（同时穿过第2层）
     └──────────────────────────────┘
```

为什么第 2 层挡不住第 3 层的场景：两个回调**同时**到达，**都**查到 PAYING（谁都没提交），**都**往下走——只有 CAS 让其中一个拿 0 行。**先查后判在并发下永远有这个洞**——这也是"幂等"和"唯一索引/条件更新"在面试里总是绑定出现的原因。

## 7.3 回调会丢：主动查单对账

回调除了重复还会**丢**（第三方认为通知成功、你的服务器实际没收到）。所以 `PaymentService.sync`：

```java
@Transactional(rollbackFor = Exception.class)
public boolean sync(String paymentNo) {
    Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
    if (payment == null || Payment.STATUS_PAID.equals(payment.getStatus())) {
        return false;                       // 已终态，不用对
    }
    PayChannel ch = router.resolve(payment.getChannel());
    PayChannel.QueryResult r = ch.query(paymentNo);
    if (PayChannel.QueryResult.UNKNOWN.equals(r.status())) {
        return false;                       // ★ 查不到≠没付，绝不擅改状态
    }
    if (PayChannel.QueryResult.PAID.equals(r.status())) {
        paymentMapper.updateToPaid(paymentNo, Payment.STATUS_PAYING,
                r.thirdTradeNo(), "query-sync");
        orderMapper.updateStatus(payment.getOrderId(),
                Order.STATUS_PENDING_PAY, Order.STATUS_PAID, LocalDateTime.now());
        return true;                        // 补偿成功
    }
    return false;
}
```

原则：**以第三方为准修正本地，但 UNKNOWN 不动状态**。前端订单页轮询它（回调丢失时用户刷新页面也能把状态拉正）；生产再加日终对账文件批量核。

诚实说明：MOCK 渠道的 `query` 恒返 UNKNOWN（没有真第三方可查），所以 sync 在 MOCK 下是死路径——**它为真实渠道而生，接口契约先立好**。这个"诚实边界"面试可以主动讲。

## 7.4 支付单复用与过期（一个反直觉的细节）

用户反复点"去支付"会创建几张支付单？看 `create` 的复用逻辑：

> **★ 2026-09-09 修缮更新**：下面这段"关旧建新"是 V3 原始实现，已升级为**一订单一行模型**——Flyway V4 给 `t_payment` 加了 `uk_order_id` 唯一索引，并发双击撞索引抛 DuplicateKey → 捕获后回查复用（与点赞幂等同款"唯一索引+冲突回查"）。CLOSED/过期后不再是"插新行"，而是**复用该行重开**（换 payment_no、重置渠道/金额/过期时间，`updateForRecreate` 带 `status != 'PAID'` 条件守卫）。原始实现保留在下方供对照——"查完再插"的窗口正是这次修缮要消灭的东西。

```java
Payment exist = paymentMapper.selectByOrderId(orderId);
if (exist != null && Payment.STATUS_PAID.equals(exist.getStatus())) {
    return toVO(exist, ...);                // 已支付：直接复用返回
}
if (exist != null && Payment.STATUS_PAYING.equals(exist.getStatus())) {
    boolean expired = exist.getExpireTime() != null
            && exist.getExpireTime().isBefore(LocalDateTime.now());
    if (!expired) {
        return toVO(exist, ...);            // 未过期的 PAYING：复用
    }
    paymentMapper.updateToClosed(exist.getPaymentNo(), Payment.STATUS_PAYING);
    // ★ 过期的 PAYING：关旧建新——
    //   过期单在渠道侧已不可支付，复用它用户会永远付不了款（评审修正）
}
// ... 创建新支付单
```

## 本章动手作业

1. 手动把某支付单 status 改回 PAYING，再重放回调 → CAS 重新生效变 PAID（模拟"漏掉的回调被补偿"）。
2. 思考：如果去掉第 ③ 步快路径（只留 CAS），功能还正确吗？（答：正确但浪费——每次重复都跑一遍 UPDATE。快路径是性能优化，CAS 才是正确性保证，分层各司其职。）
3. 思考：金额核对放在幂等快路径之后还是之前？（提示：先确认"要不要处理"，再确认"处理得对不对"——顺序不影响正确性，但日志语义更清晰。）

---

# 第 8 章 延迟消息：订单 30 分钟自动取消

> 核心机制：[TTL](V3支付与Redis详解【知识词典】.md#ttl) · [死信队列再认识](V3支付与Redis详解【知识词典】.md#死信队列再认识) · [队头阻塞](V3支付与Redis详解【知识词典】.md#队头阻塞) · [延迟消息插件](V3支付与Redis详解【知识词典】.md#延迟消息插件) · [本地消息表](V3支付与Redis详解【知识词典】.md#本地消息表)

## 8.1 需求与方案对比

用户下单不付款 → 库存被占 → 30 分钟后应自动取消并回补。三种实现：

| 方案 | 实时性 | DB 压力 | 备注 |
|---|---|---|---|
| 被动等用户付款时检查 | 差 | 无 | 用户不回来库存就一直占着 |
| 定时任务扫超时单 | 平均晚 30s | 周期性扫描 | 兜底方案（补偿任务用它） |
| **MQ 延迟消息** | **到期即触发** | 无扫描 | 本项目主方案 |

## 8.2 AMQP 复习 + 一个新概念

V2 审核已经用过 exchange/queue/binding（DirectExchange 精确路由）。V3 引入的是**消息的死亡**：RabbitMQ 里消息"死掉"（被 reject/nack/**TTL 过期**/队列满）时，若队列配了死信参数，Broker **不是丢弃它，而是按死信路由规则重新投递**：

```
x-dead-letter-exchange    → 死了之后投给哪个交换机
x-dead-letter-routing-key → 重新路由用什么 key
```

## 8.3 TTL + DLX：让消息"睡"30 分钟

`OrderDelayMqConfig` 的拓扑（逐参数讲）：

```java
@Bean
public Queue orderDelayQueue() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-message-ttl", ORDER_TTL_MILLIS);           // 队列里所有消息 30 分钟过期
    args.put("x-dead-letter-exchange", ORDER_EXCHANGE);     // 过期后投给业务交换机
    args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY); // 用这个 key 重新路由
    return QueueBuilder.durable(DELAY_QUEUE).withArguments(args).build();
}
```

数据流（把它画在脑子里）：

```
下单成功(afterCommit)
   │ convertAndSend(DELAY_QUEUE, orderId)     ← 直接发到队列（default exchange 直达）
   ▼
[order.delay.queue]  ← ★ 没有任何消费者！消息进来就是"睡觉"
   │ ...30 分钟后 TTL 到期，消息"死亡" ...
   │ Broker 按死信参数重新投递
   ▼
[order.exchange] ──routing: order.cancel.dlx──► [order.cancel.queue]
                                                     │
                                                     ▼
                                            OrderCancelConsumer
                                            仍待支付？→ CAS取消+回补库存+关支付单
```

**精髓**：延迟队列本身是个"没有任何消费者的仓库"，消息的唯一出路是**过期死亡后被死信路由转运到真正的工作队列**。

## 8.4 队头阻塞：为什么用队列级 TTL

RabbitMQ **只检查队首**消息是否过期（性能考虑，不扫描全队列）。数字例子：

```
队首消息 A：TTL = 60 分钟
第二条  B：TTL = 1 分钟
→ B 不会 1 分钟后被投递！它必须等 A 先死（60 分钟后），跟着一起走
→ B 的实际延迟 = 60 分钟 ≠ 你设置的 1 分钟
```

这叫[队头阻塞](V3支付与Redis详解【知识词典】.md#队头阻塞)，是**消息级 TTL**（每条消息自带 expiration 属性）的固有坑。本项目所有订单统一 30 分钟 → 用**队列级 TTL**（参数设在队列上，整个队列一个时钟）天然免疫。将来要"VIP 15 分钟/普通 30 分钟"差异化 → 换[官方延迟插件](V3支付与Redis详解【知识词典】.md#延迟消息插件)（x-delayed-message 交换机）或多条不同 TTL 的队列。

## 8.5 消费端：三方竞争同一订单

`OrderCancelConsumer`（逐段讲，2026-09-09 修缮后版本）：

```java
@RabbitListener(queues = OrderDelayMqConfig.CANCEL_QUEUE)
@Transactional(rollbackFor = Exception.class)
public void onCancelMessage(Long orderId) {
    cancelIfStillPending(orderId);   // ★ 异常刻意不 catch——见下方"事务边界"
}

public boolean cancelIfStillPending(Long orderId) {   // ★ 刻意不加 @Transactional（自调用失效，见第 11 章问题7）
    Order order = orderMapper.selectById(orderId);
    if (order == null) return false;                   // 幽灵消息防御
    if (!Order.STATUS_PENDING_PAY.equals(order.getStatus())) {
        log.info("订单已非待支付，跳过 orderId={} status={}", orderId, order.getStatus());
        return false;                                  // 已支付/已取消：最常见的正常分支
    }
    // 状态机 CAS：谁先改到谁生效
    int rows = orderMapper.updateStatus(orderId,
            Order.STATUS_PENDING_PAY, Order.STATUS_CANCELLED, LocalDateTime.now());
    if (rows == 0) return false;                       // 输家：被别的线程抢先
    // ★ 只有取消成功者才回补库存——顺序错了=状态没变库存却加了（虚增）
    orderItemMapper.selectByOrderId(orderId)
            .forEach(oi -> skuMapper.addStock(oi.getSkuId(), oi.getQuantity()));
    paymentService.closeIfPaying(orderId);             // 联动关支付单
    return true;
}
```

**★ 事务边界（P2 修缮，高频考点）**：早期版本在 `@Transactional` 方法**内部** try-catch 吞掉异常——异常不冒出 AOP 代理，Spring 无从感知，**半截事务照样提交**：比如状态已改、库存已回补，最后 `closeIfPaying` 抛异常 → 回滚不生效，支付单永远 PAYING。正确姿势：让异常从监听方法冒出 → ① 当前事务回滚（不留半截状态）→ ② 监听容器 reject（`default-requeue-rejected=false`，不重入队防毒消息循环）→ ③ 队列死信路由进 DLQ 等人工。**"在事务方法里吞异常"和"事务内发消息"是同一族反模式：都骗过了事务管理器。**

> 另外：补偿任务（`CompensationTask.cancelTimeoutOrders`）也直接调用 `onCancelMessage`——它与消息路径完全同语义（同事务、同 CAS），这是"补偿=重放"能成立的前提。

**为什么这里并发如此真实**——三个线程会同时盯上同一个订单：

```
线程A：用户点"取消订单"      ─┐
线程B：超时延迟消息到达        ─┼─► 都要做 PENDING_PAY → CANCELLED
线程C：支付回调同时到达        ─┘   （C 是反方向：PENDING_PAY → PAID）
```

A、B 竞争：CAS 先到先得，输家 0 行返回。B、C 竞争（取消 vs 支付撞车）：闸门都是 PENDING_PAY，只放一个过——**如果取消赢了、支付回调随后到达**，就出现 6.3 ⑥ 的"钱收了货没发"，走退款兜底。**资金状态与业务状态分开记账**再次登场。

## 8.6 发送侧：afterCommit（评审修正的反模式）

最初的版本在 `@Transactional` 的 create 方法体内直接发消息：

```java
// 反模式（已修）：
@Transactional
public OrderVO create(...) {
    // ... 插订单 ...
    rabbit.convertAndSend(DELAY_QUEUE, order.getId());   // ★ 事务还没提交，消息已发出！
    // 若后续步骤异常 → 事务回滚 → 但消息收不回来了
    // → 30 分钟后消费者查到"幽灵订单"（null 防御救一命，但这是错误路径）
}
```

修正——注册"提交后回调"：

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override public void afterCommit() {
            sendDelayMessage(order.getId());     // ★ commit 成功才发
        }
    });
```

MQ **不参与数据库事务**（两个独立系统），"事务内发消息"永远存在"回滚但消息已飞"的窗口。标准解法两档：`afterCommit`（轻量，本项目用）/[本地消息表](V3支付与Redis详解【知识词典】.md#本地消息表)（消息与业务同事务落库、定时扫描投递，强一致场景用）。这个知识点面试出现率极高。

**★ 2026-09-09 修缮：收敛为唯一出口**。这段注册逻辑如今封装在 `OrderDelayMessageSender.sendAfterCommit()`——早期只有普通下单发这条消息，后来限量发售也建订单却**忘了发**（P0：抢购单永不超时取消，限量库存被死单永久占用）。根因是"建单必发超时取消"这个约束散落各处靠人记。收敛成唯一组件后，任何建单路径（普通/秒杀/将来的拼团）都调它，漏发=漏调方法，一眼可见。**"散落的隐式约束"是迭代型项目最常见的腐化方式。**

## 本章动手作业

1. 把 `ORDER_TTL_MILLIS` 临时改成 `60000`（1 分钟），下一单不付款，掐表看 1 分钟后日志"订单超时未支付已自动取消并回补库存"；验证 t_product_sku 的 stock 加回来了。
2. 再下一单，1 分钟内手动取消 → 超时消息到达时日志"已非待支付，跳过"。
3. 再下一单并 40 秒时完成支付 → 超时消息到达时同样跳过（三方竞争的 CAS 演示）。测完把 TTL 改回 1800000。
4. 去虚拟机 RabbitMQ 管理台看 `aimall.order.delay.queue` 的消息数变化（下单 +1，30 分钟后 -1 同时 cancel 队列闪过）。
5. 思考：消费者收到消息时订单一定是"待支付"吗？（答：不一定——30 分钟里什么都可能发生。所以消费端必须回查+状态机 CAS，这就是"消息只带 id、消费时查最新"的哲学。）

---

# 第 9 章 限量发售：三层防护防超卖

> 核心机制：[Lua 脚本原子性](V3支付与Redis详解【知识词典】.md#lua-脚本原子性) · [预减库存](V3支付与Redis详解【知识词典】.md#预减库存) · [看门狗](V3支付与Redis详解【知识词典】.md#看门狗)

## 9.1 业务：为什么是"限量发售"不是"秒杀"

本项目对标得物/潮玩。得物什么时候搞过"秒杀"？它的真实玩法是**限量发售**（球鞋、潮玩、联名款）。凭空长出秒杀模块，面试官一句"你们业务为什么需要秒杀"就露馅——设计文档 5.3.1 的三问筛伪需求结论。**防超卖的技术含量一点没少，但业务讲得通**。

## 9.2 超卖是怎么发生的（数字演示）

库存 1 件，两个请求同时到达：

```
时刻     线程A                         线程B
t1      SELECT stock → 读到 1
t2                                    SELECT stock → 读到 1      ← 都认为有货！
t3      UPDATE stock=0, 下单成功
t4                                    UPDATE stock=-1, 下单成功   ← 超卖！
```

"先查后改"在并发下的固有漏洞——两步之间别人可以插队。V1 的答案是行锁 CAS（`UPDATE ... WHERE stock>=?`），V3 在它前面再加一层 Redis。

## 9.3 削峰链路全景（2026-09-09 重构：Redis 预减 + MQ 异步下单）

> **重构说明**：V3 原始实现是"同步建单"——HTTP 线程里直接跑完预减+CAS+建单。2026-09-09 修缮为经典**削峰形态**：HTTP 线程只做内存级操作，建单交给 MQ 消费者异步完成。为什么必须改：发售瞬间 1 万 QPS 直怼 DB，行锁排队 + 连接池打爆——**同步模型里"DB 扛不扛得住"与"是否超卖"是两件事，前者同样致命**。

```
HTTP 线程（毫秒级返回，只有内存操作）        MQ 消费线程（匀速，扛 DB 写）
──────────────────────────────────        ──────────────────────────────────
① 校验活动/时间/限购（读库一次）
② Lua 原子预减（判重+扣减同一脚本）           ① 回查活动（不信任消息体）
③ 投递 DropOrderMessage ──────► ──────►    ② DB 行锁 CAS 扣库存（防超卖底线）
④ 立即返回 QUEUED（前端开始轮询）            ③ 建订单 + t_drop_record(uk 幂等)
                                            ④ afterCommit 挂 30min 超时取消
                                            失败 → 回补 Redis + 失败标记
```

**"挡量"与"削峰"是两件事，缺一不可**（面试金句）：
- Lua 预减是**准入闸门**——放行的消息数 ≤ 库存量，抢完后洪峰在 Redis 纯内存被拒绝，DB 完全无感；
- MQ 是**节奏器**——通过闸门的瞬时洪峰被摊平成消费速率，DB 行锁不再被万级请求争抢。

**接口形态也随之改变**：`buy` 只返回 `{status:"QUEUED"}`（订单还不存在！），前端每 1~2 秒轮询 `GET /drops/{id}/result`，拿到 `SUCCESS(orderId)` 才跳订单页——这是秒杀类系统的标准交互。

## 9.4 第一层：Lua 逐行讲（0 基础：Lua 是什么）

Lua 是一门轻量脚本语言，Redis 内置了它的解释器——**EVAL 一段脚本，Redis 单线程执行，期间不插入任何其他命令**。这就是它的全部价值：把"读-判断-写"这个多步操作焊成一个原子操作。

```lua
local stock = tonumber(redis.call('get', KEYS[1]) or '-1')
--          ↑ KEYS[1] = 库存键 aimall:drop:stock:{活动id}
if stock < 0 then return -2 end
--          ↑ -2 = 键不存在（未预热）→ 触发懒预热后让用户重试
if redis.call('exists', KEYS[2]) == 1 then return -3 end
--          ↑ KEYS[2] = 受理标记 aimall:drop:user:{活动id}:{userId}
--            -3 = 重复提交（同一用户的消息还在队列里）
if stock < tonumber(ARGV[1]) then return -1 end
--          ↑ -1 = 库存不足（ARGV[1] = 购买数量）
redis.call('set', KEYS[2], ARGV[1], 'EX', tonumber(ARGV[2]))
--          ↑ 判重通过才写受理标记（ARGV[2] = 标记 TTL）
return redis.call('decrby', KEYS[1], ARGV[1])
--          ↑ 原子扣减，返回剩余量（≥0）
```

**为什么"判重+扣减"必须在同一脚本**：拆成两步（先 EXISTS 再 DECRBY）的话，同一用户的两个并发请求可能都通过判重、各自扣一次库存——名额被同一人占两份。这也是把"防重复提交"从应用层下沉到 Redis 的原因：HTTP 线程无状态，挡不住同一用户的双击。

**为什么不用分布式锁**——"判断+扣减"是一次原子计数，Lua 一发入魂；加锁方案每次"加锁→读→改→解锁"两次网络往返还全局串行化。**锁适合保护复杂临界区（一长段业务逻辑），简单原子计数用 Lua 更快更简单**——这句对比是面试金句。

**库存怎么进 Redis 的**（预热，P0 修缮后版本）：

```java
public void warmUpIfAbsent(Long activityId) {
    if (redisOps.get(stockKey(activityId)).isPresent()) return;   // 快路径
    // ★ SET NX：判断+写入原子化（早期 GET 判空再 SET 是 check-then-act，
    //   并发首访两个请求都判"未预热"，后一个 SET 会把已扣减的库存重置回全量=超卖）
    boolean written = redisOps.setIfAbsent(stockKey(activityId),
            String.valueOf(act.getDropStock()), 24 * 3600);
}
```

## 9.5 消费端：事务建单 + 失败三分流（评审揪出的 P0 集中营）

消费端是 `DropOrderExecutor`（独立 bean 而非 DropService 私有方法——本地降级路径要经代理调用事务方法，自调用不走 AOP 会裸奔）：

```java
public void execute(DropOrderMessage msg) {
    try {
        createDropOrder(msg);                    // @Transactional：CAS 扣库存 + 建单 + uk 限购
    } catch (DuplicateKeyException e) {
        dropRedis.rollbackDeduct(...);            // 重复投递：uk 已有记录，幂等跳过+回补
    } catch (BusinessException e) {
        dropRedis.rollbackDeduct(...);           // 业务失败（库存不足等）：回补+写失败标记
        dropRedis.markFailed(...);               //   前端轮询到 FAILED（重试也不会成功，ACK）
    }
    // 其他异常原样抛出 → 容器 reject → 死信队列人工兜底
}
```

三类异常三套路数，这是削峰版"失败回补"的完整形态：

| 异常 | 含义 | 处理 |
|---|---|---|
| `DuplicateKeyException` | uk 冲突 = 该用户已建单成功（重复投递/并发提交） | 回补本条消息的预减，幂等跳过 |
| `BusinessException` | 业务性失败（库存不足/时间不符） | 回补预减 + 失败标记，ACK 不重试 |
| 其他 `Exception` | 未知故障（DB 抖动） | 重抛 → reject → **DLQ 等人工** |

**回补的 Lua 也是原子的**（库存加回 + 删受理标记同一脚本）——拆开执行时，两步之间用户重试提交会被删了一半的标记挡住或放过，窗口内行为不可预期。

**建单事务内的关键一行**：`delayMessageSender.sendAfterCommit(order.getId())`——秒杀订单同样挂 30 分钟超时取消（P0-3 修缮：早期抢购单永不超时，限量库存被死单永久占用）。

**语义澄清（评审问题 12）**：唯一索引硬保证**一人一次成功**；`per_limit` 约束**单次**购买上限。组合语义="每人可成功一次、单次最多 per_limit 件"。

## 9.6 一致性边界（诚实说，面试加分）

预减（Redis）与实扣（DB）是两个存储，**没有事务**：回补也可能失败（Redis 恰好挂了）、TTL 到期重灌会覆盖。所以这是**最终一致**：
- 活动结束后对账（Redis 剩余 vs DB 应余）校正；
- "已受理但订单还没建好"的窗口期由前端轮询 `result` 掩盖（QUEUED → SUCCESS/FAILED）；
- 消息彻底丢失（MQ 宕机）由降级路径兜住：MQ 不可用时改走本地线程池异步建单（与审核降级同款），Redis 也不可用则 DB CAS + uk 硬扛。

能主动讲清"我知道边界在哪、怎么补"的候选人不多——大多数只会背"Redis 预减防超卖"。

## 本章动手作业

1. 手工插一条活动数据（drop_stock=5, per_limit=1），`GET /api/v1/drops/{id}` 触发预热，`redis-cli GET aimall:drop:stock:{id}` 应为 5。
2. `POST /drops/{id}/buy` → 立刻返回 QUEUED（注意：不再是订单号！）；1~2 秒后 `GET /drops/{id}/result` → SUCCESS 带 orderId。
3. 同一用户抢两次 → 第二次"请勿重复抢购"（Lua 判重 -3）；换不同用户各抢一次看 Redis 计数递减。
4. 换 5 个用户并发抢 5 件 → 5 个 QUEUED + 5 个 SUCCESS；第 6 个 → "已被抢完"且**日志无建单 SQL**（被第 1 层挡住）。
5. 思考：如果不回补，攻击者可以用什么手法把库存"刷没"？（答：用一个永远会失败的身份反复触发抢购——每次都白吃一个名额。）

---

# 第 10 章 AI 限流：给烧钱的接口上闸

> 核心机制：[固定窗口](V3支付与Redis详解【知识词典】.md#固定窗口) · [滑动窗口](V3支付与Redis详解【知识词典】.md#滑动窗口) · [令牌桶](V3支付与Redis详解【知识词典】.md#令牌桶) · [降级方向](V3支付与Redis详解【知识词典】.md#降级方向)

## 10.1 为什么 AI 接口必须限流而普通接口可以宽容

两条硬理由：
1. **按 token 计费**——一次问答真实花钱，被脚本刷一晚 = 真金白银的账单；
2. **秒级耗时占线程池**——AI 调用 3~10 秒，刷接口会把 Tomcat 线程占满，**正常用户全被连坐**。

对比：商品列表接口多查一次的成本≈0，限流收益低；AI 接口成本线性且高——**限流是成本治理，不是炫技**。

## 10.2 实现：固定窗口 + Lua 原子化

```java
// RedisOps 里被 V3 评审修正过的一处：
private static final String RATE_LIMIT_LUA =
    "local n = redis.call('incr', KEYS[1]) " +
    "if n == 1 then redis.call('expire', KEYS[1], ARGV[1]) end " +   // 首次才设过期
    "return n";

public boolean allow(String key, int limit, int windowSeconds) {
    Long n = safe(() -> stringRedisTemplate.execute(
            new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class),
            Collections.singletonList(key), String.valueOf(windowSeconds)),
            null, "allow", key);
    if (n == null) return true;      // Redis 挂 → 放行（fail-open，见 2.3 的表格）
    return n <= limit;
}
```

**为什么 INCR+EXPIRE 必须也是 Lua**——早期两步写法：INCR 成功、EXPIRE 恰好失败（Redis 瞬断）→ key 存在但**永不过期** → 该用户被**永久**限流到 10 次。两步之间永远有"恰好失败"的窗口——与预减库存同一个教训：**多命令复合语义必须 Lua**（本项目第三次应用）。

调用点（ChatRestController）：

```java
private void checkRateLimit() {
    Long userId = StpUtil.getLoginIdAsLong();
    boolean allowed = redisOps.allow("aimall:rate:chat:" + userId, 10, 60);
    if (!allowed) {
        throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                "问得太频繁啦，休息一下再来～（每分钟 10 次）");
    }
}
// chat() 与 stream() 两个入口都先 checkRateLimit()
```

## 10.3 固定窗口的边界（知道缺陷才敢说会用）

窗口切换瞬间最多放行 2 倍：第 1 分钟最后 1 秒打满 10 次 + 第 2 分钟第 1 秒再打 10 次——1 秒内 20 次穿过。对"防刷"够用；要严格就上[滑动窗口](V3支付与Redis详解【知识词典】.md#滑动窗口)（zset 按时间精确统计）或[令牌桶](V3支付与Redis详解【知识词典】.md#令牌桶)（允许受控突发，网关常用）。**能说出自己方案的缺陷比堆砌高级方案更显功底**。

## 本章动手作业

连问 AI 11 次，第 11 次收到"问得太频繁啦"；`redis-cli TTL aimall:rate:chat:你的userId` 看窗口剩余秒数；等 1 分钟后再问恢复。

---

# 第 11 章 评审复盘：12 个真实问题（面试弹药库）

本章是 V3 code review 揪出的问题清单。**每一行都是真实踩过（或差点踩）的坑**——面试官问"你项目踩过什么坑"，这里的素材比任何八股都真：

| # | 级别 | 问题 | 后果（如果不修） | 修复 |
|---|---|---|---|---|
| 1 | **P0** | ProductCacheService 写了但没接线 | 缓存三兄弟整个不生效，白写 | Controller 层接入 detailWithCache |
| 2 | **P0** | 抢购失败不回补 Redis 预减 | 名额白吃、库存虚低=用户侧资损 | catch 里 INCRBY 回补 |
| 3 | **P0** | mock-pay 不校验支付单归属 | 任何登录用户能"替别人付款" | 加 userId 归属校验 |
| 4 | **P0** | 金额核对写成可选（null 跳过） | 回调不带 amount 字段绕过核对 | 缺失/非法即拒绝 |
| 5 | P1 | 热门榜 zset 工具没接线 | 设计承诺的榜单不存在 | refreshHotScore 同步 zAdd + /notes/hot |
| 6 | P1 | 事务内发 MQ 消息 | 回滚产生幽灵消息 | afterCommit 再发 |
| 7 | P1 | cancelIfStillPending 的 @Transactional 自调用 | 注解是摆设（外层有事务侥幸正确） | 删掉+注释说明 |
| 8 | P1 | 限流 INCR/EXPIRE 两步非原子 | key 永不过期→用户被永久限流 | Lua 原子化 |
| 9 | P1 | 复用支付单不检查过期 | 过期单被复用，用户永远付不了款 | 过期即关旧建新 |
| 10 | P1 | 抢购 orderNo 用纯毫秒时间戳 | 并发碰撞面大 | 复用 V1 生成器风格 |
| 11 | P1 | warmUp 用总量覆盖进行中库存 | 键过期重灌重置已抢数量 | warmUpIfAbsent 只灌无键场景 |
| 12 | P2 | per_limit 与 uk 语义矛盾 | 字段形同虚设 | 注释澄清组合语义 |

**三条元教训**（比修 bug 更值钱，直接可用于"你从中学到什么"的追问）：
1. **"写了"≠"生效了"**——1、5 号的共同模式：代码漂亮但没人调用。评审的第一动作是 grep 调用点。
2. **失败路径是主要路径**——2、4 号都出在失败分支（回滚/回补/字段缺失）。写代码顺着成功路径想，评审要专挑失败路径问。
3. **同类坑会反复出现**——7 号（@Transactional 自调用）与 V2 修过的 @Async 自调用同源，还是犯了。框架代理类陷阱值得单独记一条笔记。

---

# 第 12 章 验收清单 + 面试速查 + 报错速查

## 12.1 全链路验收清单

```bash
# 0. 环境：虚拟机 redis(6381)/rabbitmq(5673)（不启动则对应功能降级——降级本身也是验收项）

# 1. 启动 → Flyway 执行 V3__pay_and_drop.sql（t_payment/t_drop_activity/t_drop_record/t_refund）

# 2. 支付闭环（对照第 6 章时序图逐步勾）
#    [ ] 下单 → 订单"待支付"出现【去支付】→ 模拟收银台（金额/单号/30min 倒计时）
#    [ ] 确认支付 → 订单变【已支付】，日志可 grep 到六步
#    [ ] 幂等：重放 mock-pay → 返回成功但状态不变（"幂等跳过"）
#    [ ] 验签：curl 假签名 → sign-error
#    [ ] 金额：篡改 amount → amount-mismatch；不带 amount → amount-missing
#    [ ] 归属：他人支付单 mock-pay → 404
#    [ ] 回调端点免登录可访问（白名单），业务接口仍需登录

# 3. 延迟消息（TTL 临时改 60000 测）
#    [ ] 不支付 → 1 分钟后自动 CANCELLED + 库存回补 + 支付单 CLOSED
#    [ ] 已支付订单到期 → "已非待支付，跳过"
#    [ ] 1 分钟内手动取消 → 超时消息到达时跳过（三方竞争 CAS）

# 4. 缓存
#    [ ] 商品详情二次请求无 SQL（MONITOR 观察）
#    [ ] DEL key 后互斥重建；查不存在 id 两次后空值标记生效
#    [ ] 停 Redis → 接口照常 + WARN 降级日志；限流放行（fail-open）

# 5. 限量发售（自建活动数据，削峰链路）
#    [ ] POST /drops/{id}/buy → 立即返回 QUEUED（不是订单号）
#    [ ] 轮询 GET /drops/{id}/result → 1~2 秒内 SUCCESS 带 orderId
#    [ ] 预热后 Redis 计数正确；并发不多卖不虚卖
#    [ ] 同用户重复抢购 → "请勿重复抢购"（Lua 判重）；抢完 → 第1层拦截（日志无建单 SQL）
#    [ ] 失败后 Redis 计数回补 + result 轮询到 FAILED
#    [ ] 抢购成功不付款 → 30min 超时自动取消并回补库存（与普通订单同款）
# 6. 限流：11 连问第 11 次拒绝
# 7. 定时任务（@EnableScheduling 已开）
#    [ ] CompensationTask：订单超时兜底/审核堆积重送/支付查单对账（日志可 grep "[补偿]"）
#    [ ] NoteCounterFlushTask：点赞后 60s 内 t_note.like_count 落库 + hot_score 重算
#    [ ] HotRankRebuildTask：DEL 掉 zset 后 30min 内自动重建
# 8. 下单幂等：结算弹窗双击"提交订单"两次 → 第二次"请勿重复提交订单"
# 9. 单测：mvn test → PaymentServiceTest 5 用例全绿
```

## 12.2 面试速查表（30 秒一答）

| 问题 | 一句话答案 |
|---|---|
| 回调怎么保证安全 | 验签（HMAC/RSA2）+ 金额核对（缺失即拒）+ 白名单放行；验证"完整性"不是"认证" |
| 重复回调怎么办 | payment_no 唯一 + 已付回 success + 状态机 CAS 三层；先查后判并发下双过 |
| 回调丢了怎么办 | 主动查单对账；UNKNOWN 不改状态（查不到≠没付） |
| 支付撞上超时取消怎么办 | 两边都是 PENDING_PAY 的 CAS，只放一个过；钱收了货没发走退款——资金与业务分开记账 |
| 订单超时怎么取消 | TTL+DLX 队列级 TTL（免疫队头阻塞）；afterCommit 投递防幽灵消息 |
| 缓存三兄弟 | 穿透=空值缓存；击穿=互斥锁重建；雪崩=TTL 随机抖动 |
| 为什么 Lua 不用分布式锁 | 原子计数一发入魂；锁适合复杂临界区，两次往返+串行化不划算 |
| 预减失败怎么办 | INCRBY 回补；回补也失败靠 warmUp 重灌 + 活动后对账，最终一致 |
| Redis 挂了会怎样 | 门面统一降级走 DB——变慢不可用；限流方向 fail-open（拒绝服务比多花钱糟） |
| 超卖几层保险 | Redis 预减（流量）+ DB 行锁 CAS（正确性）+ 限购唯一索引（幂等）；失败回补闭环 |
| 事务里能发 MQ 吗 | 不能（回滚=幽灵消息）；afterCommit 或本地消息表 |
| Redis 序列化为什么不用 JDK 默认 | 乱码不可排查 / 类名强耦合 / 反序列化漏洞——跨进程一律 JSON |

## 12.3 报错速查表

| 症状 | 原因 | 处置 |
|---|---|---|
| 启动报 `Detected failed migration` | 上次 Flyway V3 执行了一半 | `DELETE FROM flyway_schema_history WHERE success=0;` 重启 |
| 回调 `sign-error` 但你确定报文是真的 | 密钥不一致（yml 的 mock.secret 与签名方不同/被环境变量覆盖） | 核对 `aimall.pay.mock.secret` 两侧一致 |
| 回调 `amount-mismatch` | 报文金额与支付单不符（手改过数据库？） | 以支付单为准重新发起支付 |
| 订单 30 分钟没自动取消 | MQ 没连上（投递被降级跳过）/ 旧 delay 队列没有 TTL 参数 | 看启动日志 MQ 连接；**删除旧的 aimall.order.delay.queue 队列再重启**让其带参重建 |
| 商品详情不走缓存 | Redis 未连接（看 WARN）或 key 前缀不匹配 | `KEYS aimall:product:*` 核对 |
| Redis 全部 WARN 降级 | 虚拟机 Redis 没起/密码错 | `redis-cli -h 192.168.6.102 -p 6381 -a 123456 ping` |
| 限流 10 次后永远被拒 | （已修的）两步限流永不过期 bug | 已 Lua 原子化；若复现 DEL 对应 key |

---

> 读完建议回到顶部把【易错关键点】再背一遍，然后做 12.1 的验收清单——**动手过的才是你的**。
> 配套词典：[V3支付与Redis详解【知识词典】.md](V3支付与Redis详解【知识词典】.md)
> 下一站：[V2内容社区与RAG详解.md](V2内容社区与RAG详解.md)（若还没精读）或项目设计文档的 V4 规划。
