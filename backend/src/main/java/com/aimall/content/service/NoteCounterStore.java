package com.aimall.content.service;

import com.aimall.common.redis.RedisOps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 笔记计数桶 —— 浏览/点赞/收藏计数的「Redis 攒增量」侧。
 *
 * <h2>核心设计：存<b>增量（delta）</b>而不是全量</h2>
 * <pre>
 *   互动发生：  HINCRBY aimall:cnt:{type}  {noteId}  ±1     （内存，微秒级）
 *   读计数：    DB 快照值 + HMGET 增量                       （近实时）
 *   定时落库：  Lua 原子取走全部增量 → UPDATE x_count += delta（批量回写）
 * </pre>
 *
 * <p>为什么不存全量（GET-改-写回）：落库窗口内"Redis 全量值"与"DB 值"
 * 互相覆盖，丢失更新的窗口永远存在。存增量则两侧都是<b>加法</b>，
 * 天然可交换、可重试，没有覆盖语义。</p>
 *
 * <h2>一致性口径（面试可讲）</h2>
 * 计数是<b>非资金类展示数据</b>，允许秒级延迟（落库间隔内读的是
 * "DB + 增量"的拼接值，本身就近实时）；极端情况（Redis 宕机瞬间）
 * 最多丢宕机前未落库的增量，不丢单不资损——与订单/支付的一致性等级刻意区分。
 */
@Component
@RequiredArgsConstructor
public class NoteCounterStore {

    public static final String VIEW = "view";
    public static final String LIKE = "like";
    public static final String COLLECT = "collect";

    private static final String KEY_PREFIX = "aimall:cnt:";

    private final RedisOps redisOps;

    /**
     * 计数加 delta。
     *
     * @return 落在 Redis 后的增量值；null = Redis 不可用（调用方回退 DB 直写）
     */
    public Long incr(String type, Long noteId, long delta) {
        return redisOps.hIncrBy(key(type), String.valueOf(noteId), delta);
    }

    /** 批量读一批笔记的当前增量（读路径拼"DB值+增量"用）；降级空 Map */
    public Map<Long, Long> readDeltas(String type, List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }
        List<String> fields = noteIds.stream().map(String::valueOf).toList();
        Map<String, Long> raw = redisOps.hmGetLongs(key(type), fields);
        Map<Long, Long> r = new HashMap<>(raw.size());
        raw.forEach((k, v) -> {
            try {
                r.put(Long.parseLong(k), v);
            } catch (NumberFormatException ignored) {
            }
        });
        return r;
    }

    /**
     * 原子取走全部增量并清空（落库任务调用）。
     * 取走后新到的增量从零开始攒，进入下一轮落库——加法语义保证不丢不重。
     */
    public Map<Long, Long> takeAndClear(String type) {
        Map<String, Long> raw = redisOps.takeAllAndClear(key(type));
        Map<Long, Long> r = new HashMap<>(raw.size());
        raw.forEach((k, v) -> {
            try {
                r.put(Long.parseLong(k), v);
            } catch (NumberFormatException ignored) {
            }
        });
        return r;
    }

    private String key(String type) {
        return KEY_PREFIX + type;
    }
}
