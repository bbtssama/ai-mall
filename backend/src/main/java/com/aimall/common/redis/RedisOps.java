package com.aimall.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 访问门面 —— 本项目"可降级"原则在 V3 的落地。
 *
 * <h2>★ 为什么要有这一层</h2>
 * Redis 是<b>旁路缓存</b>：它挂了，业务应该"变慢"（直接查库）而不是"不可用"。
 * 但如果在业务代码里到处裸调 {@code redisTemplate.opsForValue().get(...)}，
 * 那么 Redis 一断连，所有请求都会抛异常 —— 缓存反而成了故障放大器。
 *
 * <p>本门面把"异常→降级"收敛在一处：任何 Redis 操作失败都记录日志并返回
 * 兜底值（空/0/false），业务侧拿到"缓存未命中"的信号，自然走 DB。</p>
 *
 * <h2>降级时的可观测性</h2>
 * 每次降级打 WARN 日志（带 traceId），并且可以统计降级次数——
 * "Redis 挂了你却不知道"比"Redis 挂了"更可怕。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisOps {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    // ------------------------------------------------------------------
    // 字符串 / 对象
    // ------------------------------------------------------------------

    public Optional<String> get(String key) {
        return safe(() -> Optional.ofNullable(stringRedisTemplate.opsForValue().get(key)),
                Optional.empty(), "get", key);
    }

    public void set(String key, String value, long ttlSeconds) {
        safe(() -> {
            stringRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            return true;
        }, false, "set", key);
    }

    /**
     * 仅当 key 不存在时写入（SET NX PX）。
     *
     * <p>★ 典型场景：库存预热。check-then-set（先 GET 再 SET）在并发首访时
     * 会把两个请求都判为"未预热"，后一个无条件 SET 会<b>覆盖进行中的扣减</b>——
     * 库存被重置回全量 = 超卖。NX 语义把"判断+写入"原子化，天然幂等。</p>
     *
     * @return 是否真的写入了（false = key 已存在或 Redis 不可用）
     */
    public boolean setIfAbsent(String key, String value, long ttlSeconds) {
        return safe(() -> Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                        .setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS)),
                false, "setIfAbsent", key);
    }

    /** 写入对象（走 JSON 序列化，见 RedisConfig） */
    public void setObject(String key, Object value, long ttlSeconds) {
        safe(() -> {
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            return true;
        }, false, "setObject", key);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getObject(String key, Class<T> type) {
        return safe(() -> {
            Object v = redisTemplate.opsForValue().get(key);
            return v != null && type.isInstance(v) ? Optional.of((T) v) : Optional.<T>empty();
        }, Optional.empty(), "getObject", key);
    }

    public void delete(String key) {
        safe(() -> {
            stringRedisTemplate.delete(key);
            return true;
        }, false, "delete", key);
    }

    // ------------------------------------------------------------------
    // 计数
    // ------------------------------------------------------------------

    /** 自增计数器（点赞/浏览计数），返回自增后的值；降级返回 null 表示"不可用，请走 DB" */
    public Long incr(String key) {
        return safe(() -> stringRedisTemplate.opsForValue().increment(key), null, "incr", key);
    }

    public Long decr(String key) {
        return safe(() -> stringRedisTemplate.opsForValue().decrement(key), null, "decr", key);
    }

    /** 设置计数并带过期时间（用于"定时落库后重置计数"） */
    public void setCount(String key, long value, long ttlSeconds) {
        set(key, String.valueOf(value), ttlSeconds);
    }

    public Long getCount(String key) {
        return get(key).map(s -> {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }).orElse(null);
    }

    // ------------------------------------------------------------------
    // 分布式锁（SET key value NX PX + Lua 释放）
    // ------------------------------------------------------------------

    /** 释放锁的 Lua：必须校验 value 再删（防误删别人的锁） */
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    /**
     * 尝试加锁：SET key value NX PX ttl。
     *
     * @return 锁的 value（用于释放），失败返回 null
     */
    public String tryLock(String lockKey, long ttlMillis) {
        String value = java.util.UUID.randomUUID().toString();
        Boolean ok = safe(() -> stringRedisTemplate.opsForValue()
                        .setIfAbsent(lockKey, value, ttlMillis, TimeUnit.MILLISECONDS),
                false, "tryLock", lockKey);
        return Boolean.TRUE.equals(ok) ? value : null;
    }

    /** 释放锁（Lua 保证"校验+删除"原子性） */
    public void unlock(String lockKey, String lockValue) {
        if (lockValue == null) {
            return;
        }
        safe(() -> stringRedisTemplate.execute(
                        new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
                        Collections.singletonList(lockKey), lockValue),
                0L, "unlock", lockKey);
    }

    // ------------------------------------------------------------------
    // 排行榜（zset）
    // ------------------------------------------------------------------

    public void zAdd(String key, String member, double score) {
        safe(() -> stringRedisTemplate.opsForZSet().add(key, member, score), false, "zAdd", key);
    }

    public void zIncr(String key, String member, double delta) {
        safe(() -> stringRedisTemplate.opsForZSet().incrementScore(key, member, delta), null, "zIncr", key);
    }

    /** 取榜单前 N 名（分数从高到低） */
    public List<String> zTop(String key, int topN) {
        return safe(() -> {
            var set = stringRedisTemplate.opsForZSet().reverseRange(key, 0, topN - 1L);
            return set == null ? List.<String>of() : new java.util.ArrayList<>(set);
        }, List.<String>of(), "zTop", key);
    }

    /** 带分数的榜单（展示用） */
    public List<RankItem> zTopWithScore(String key, int topN) {
        return safe(() -> {
            var set = stringRedisTemplate.opsForZSet()
                    .reverseRangeWithScores(key, 0, topN - 1L);
            if (set == null) {
                return List.<RankItem>of();
            }
            return set.stream()
                    .map(t -> new RankItem(t.getValue(), t.getScore() == null ? 0 : t.getScore()))
                    .toList();
        }, List.<RankItem>of(), "zTopWithScore", key);
    }

    public record RankItem(String member, double score) {
    }

    // ------------------------------------------------------------------
    // Lua 脚本（原子复合操作）
    // ------------------------------------------------------------------

    /**
     * 执行 Lua 脚本。
     *
     * <p>为什么需要它：Redis 单条命令是原子的，但"读-判断-写"的组合不是。
     * Lua 脚本在 Redis 里被当作<b>一条命令</b>原子执行（期间不会被其他命令插入），
     * 是实现"检查再设置（CAS）"类操作的标准手段（限量发售预减库存就靠它）。</p>
     *
     * @return 脚本返回值；Redis 不可用时返回 null（调用方应降级到数据库兜底）
     */
    public Long eval(String script, List<String> keys, String... args) {
        return safe(() -> stringRedisTemplate.execute(
                        new DefaultRedisScript<>(script, Long.class), keys, (Object[]) args),
                null, "eval", String.join(",", keys));
    }

    // ------------------------------------------------------------------
    // 限流（固定窗口计数，够用且好讲）
    // ------------------------------------------------------------------

    /**
     * 固定窗口限流（★评审修正：INCR+EXPIRE 用 Lua 原子化）。
     *
     * <p>早期两步写法（INCR 后再 EXPIRE）有个隐蔽 bug：INCR 成功但 EXPIRE 恰好失败
     * （Redis 瞬断）→ key 存在但永不过期 → 该用户被<b>永久</b>限流到 N 次。
     * Lua 把两步合成原子操作，从根上消除这个窗口。这也是"多命令复合语义必须 Lua"
     * 的又一实例（与限量发售预减同理）。</p>
     *
     * <p>为什么不用更复杂的滑动窗口/令牌桶：限流的目的是"防刷与成本控制"，
     * 固定窗口实现简单、性能好；边界处最多放行 2 倍（窗口切换瞬间）对本场景可接受。
     * 真要严格限流用 Redisson 的 RRateLimiter 或网关层限流。</p>
     */
    private static final String RATE_LIMIT_LUA =
            "local n = redis.call('incr', KEYS[1]) " +
            "if n == 1 then redis.call('expire', KEYS[1], ARGV[1]) end " +
            "return n";

    public boolean allow(String key, int limit, int windowSeconds) {
        Long n = safe(() -> stringRedisTemplate.execute(
                        new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class),
                        Collections.singletonList(key), String.valueOf(windowSeconds)),
                null, "allow", key);
        if (n == null) {
            return true;    // Redis 不可用 → 放行（限流组件不应成为可用性瓶颈）
        }
        return n <= limit;
    }

    // ------------------------------------------------------------------
    // Hash 计数桶（浏览/点赞/收藏的"Redis 攒增量 + 定时落库"）
    // ------------------------------------------------------------------

    /**
     * HINCRBY：field 计数加 delta。
     *
     * <p>★ 计数类写操作为什么要走 Redis：热点笔记的每次浏览/点赞都是对
     * 同一行的 UPDATE（行锁串行 + redo log 落盘），高流量下写放大明显。
     * 改为内存 HINCRBY 攒<b>增量</b>，定时批量回写 DB——写 DB 频率与流量解耦。</p>
     *
     * @return 自增后的值；降级返回 null（调用方应回退 DB 直写）
     */
    public Long hIncrBy(String key, String field, long delta) {
        return safe(() -> stringRedisTemplate.opsForHash().increment(key, field, delta),
                null, "hIncrBy", key);
    }

    /** HMGET 批量读计数增量；降级返回空 Map（调用方按 0 处理，读 DB 值即可） */
    public Map<String, Long> hmGetLongs(String key, java.util.Collection<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        List<Object> ordered = new java.util.ArrayList<>(fields);
        return safe(() -> {
            List<Object> vals = stringRedisTemplate.opsForHash().multiGet(key, ordered);
            Map<String, Long> r = new java.util.HashMap<>();
            for (int i = 0; i < ordered.size(); i++) {
                Object v = vals == null ? null : vals.get(i);
                if (v != null) {
                    try {
                        r.put(String.valueOf(ordered.get(i)), Long.parseLong(v.toString()));
                    } catch (NumberFormatException ignored) {
                        // 脏值当 0：计数是展示数据，不能因一条脏数据炸整个列表
                    }
                }
            }
            return r;
        }, Map.of(), "hmGet", key);
    }

    /**
     * 原子"取走全部并清空"（HGETALL + DEL 同一脚本）。
     *
     * <p>★ 为什么必须 Lua：拆成 HGETALL → DEL 两步的话，两步之间到达的
     * HINCRBY 会被随后的 DEL 一起删掉——增量凭空丢失（落库丢计数）。
     * 脚本内先取后删原子执行，取走之后新到的增量进入"新一轮"，下轮再落。</p>
     */
    private static final String TAKE_ALL_LUA =
            "local all = redis.call('hgetall', KEYS[1]) " +
            "redis.call('del', KEYS[1]) " +
            "return all";

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Long> takeAllAndClear(String key) {
        return safe(() -> {
            Object rawObj = stringRedisTemplate.execute(
                    new DefaultRedisScript(TAKE_ALL_LUA, List.class), List.of(key));
            Map<String, Long> r = new java.util.HashMap<>();
            if (rawObj instanceof List<?> raw) {
                for (int i = 0; i + 1 < raw.size(); i += 2) {
                    try {
                        r.put(String.valueOf(raw.get(i)), Long.parseLong(String.valueOf(raw.get(i + 1))));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return r;
        }, Map.of(), "takeAllAndClear", key);
    }

    // ------------------------------------------------------------------
    // 降级包装
    // ------------------------------------------------------------------

    private <T> T safe(Supplier<T> supplier, T fallback, String op, String key) {
        try {
            T v = supplier.get();
            return v == null ? fallback : v;
        } catch (Exception e) {
            // 注意：这是 WARN 不是 ERROR——降级是设计内的路径，不是故障
            log.warn("Redis 不可用，降级为空操作 op={} key={} : {}", op, key, e.getMessage());
            return fallback;
        }
    }
}
