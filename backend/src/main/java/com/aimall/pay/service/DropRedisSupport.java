package com.aimall.pay.service;

import com.aimall.common.redis.RedisOps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 限量发售的 Redis 库存支持组件 —— 预减/回补/状态标记的唯一归属。
 *
 * <h2>为什么单独一个类（而不是塞在 DropService 里）</h2>
 * 预减发生在<b>提交侧</b>（DropService.submitBuy），回补发生在<b>消费侧</b>
 * （DropOrderExecutor）。两侧都要操作同一组 key 和 Lua 脚本，若各写一份必然漂移。
 * 收敛到一个组件：key 命名、脚本语义、回补对称性只维护一处。
 *
 * <h2>三个 key 的语义（面试易混，写清楚）</h2>
 * <ul>
 *   <li>{@code aimall:drop:stock:{activityId}} —— 库存计数（准入层，非真值）；
 *       真值在 DB 的 t_product_sku.stock，由 CAS 兜底防超卖</li>
 *   <li>{@code aimall:drop:user:{activityId}:{userId}} —— 受理标记（防重复提交），
 *       Lua 里与库存扣减<b>同一脚本原子写入</b></li>
 *   <li>{@code aimall:drop:fail:{activityId}:{userId}} —— 失败标记（消费端建单失败时写），
 *       前端轮询据此区分"处理中"和"失败"</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DropRedisSupport {

    private static final String STOCK_KEY_PREFIX = "aimall:drop:stock:";
    private static final String USER_KEY_PREFIX = "aimall:drop:user:";
    private static final String FAIL_KEY_PREFIX = "aimall:drop:fail:";

    /** 受理/失败标记的存活时间（秒）：覆盖"排队→建单"的正常窗口，过期自动放行重试 */
    private static final int MARKER_TTL = 600;

    /**
     * 原子预减：判重 + 扣库存一步完成（经典秒杀 Lua）。
     *
     * <p>为什么"判重+扣减"必须在同一脚本：拆成两步（先 EXISTS 再 DECRBY）的话，
     * 同一用户的两个并发请求可能都通过判重，各自扣一次库存——名额被同一人占两份。
     * Lua 在 Redis 单线程里原子执行，天然串行。</p>
     *
     * <p>返回值约定：<b>null</b>=Redis 不可用（降级走 DB 兜底）；
     * -2=库存键不存在（未预热）；-3=重复提交；-1=库存不足；&gt;=0=扣减后剩余。</p>
     */
    private static final String DEDUCT_LUA =
            "local stock = tonumber(redis.call('get', KEYS[1]) or '-1') " +
            "if stock < 0 then return -2 end " +
            "if redis.call('exists', KEYS[2]) == 1 then return -3 end " +
            "if stock < tonumber(ARGV[1]) then return -1 end " +
            "redis.call('set', KEYS[2], ARGV[1], 'EX', tonumber(ARGV[2])) " +
            "return redis.call('decrby', KEYS[1], ARGV[1])";

    /**
     * 回补：库存加回 + 删受理标记（与预减严格对称的逆操作）。
     *
     * <p>为什么"加回+删标记"也要 Lua 原子：拆开执行时，两步之间用户重试提交
     * 会被已删除一半的标记挡住或放过，窗口内行为不可预期。
     * 单脚本单网络往返，还省一次 RTT。</p>
     */
    private static final String ROLLBACK_LUA =
            "redis.call('incrby', KEYS[1], ARGV[1]) " +
            "redis.call('del', KEYS[2]) " +
            "return 1";

    private final RedisOps redisOps;

    /** 库存键（预热时也用它） */
    public String stockKey(Long activityId) {
        return STOCK_KEY_PREFIX + activityId;
    }

    /**
     * 原子预减（判重+扣减）。见 DEDUCT_LUA 的返回值约定。
     */
    public Long tryDeduct(Long activityId, Long userId, int quantity) {
        return redisOps.eval(DEDUCT_LUA,
                List.of(stockKey(activityId), USER_KEY_PREFIX + activityId + ":" + userId),
                String.valueOf(quantity), String.valueOf(MARKER_TTL));
    }

    /** 回补预减（库存加回 + 删受理标记，原子） */
    public void rollbackDeduct(Long activityId, Long userId, int quantity) {
        redisOps.eval(ROLLBACK_LUA,
                List.of(stockKey(activityId), USER_KEY_PREFIX + activityId + ":" + userId),
                String.valueOf(quantity));
    }

    /** 写失败标记（消费端建单失败时）：前端轮询据此展示"失败"，TTL 后允许重试 */
    public void markFailed(Long activityId, Long userId) {
        redisOps.set(FAIL_KEY_PREFIX + activityId + ":" + userId, "1", MARKER_TTL);
    }

    /** 是否已有受理标记（区分"处理中"与"未参与"） */
    public boolean hasQueuedMarker(Long activityId, Long userId) {
        return redisOps.get(USER_KEY_PREFIX + activityId + ":" + userId).isPresent();
    }

    /** 是否失败标记（区分"失败"与"处理中"） */
    public boolean hasFailedMarker(Long activityId, Long userId) {
        return redisOps.get(FAIL_KEY_PREFIX + activityId + ":" + userId).isPresent();
    }
}
