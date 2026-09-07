package com.aimall.goods.service.impl;

import com.aimall.common.redis.RedisOps;
import com.aimall.goods.dto.ProductVO;
import com.aimall.goods.mapper.ProductMapper;
import com.aimall.goods.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 商品详情缓存 —— <b>缓存三兄弟（穿透/击穿/雪崩）的真实落地案例</b>。
 *
 * <h2>① 缓存穿透：查一个根本不存在的 id</h2>
 * 攻击/误刷不停地查 {@code productId=-1} → 缓存永远不命中 → 每次都打 DB。
 * <b>对策：空值缓存</b>——查不到也往缓存里写一个空标记（__NULL__），TTL 设短一点（2 分钟），
 * 既挡住刷子，又不会因为"真上架了却查不到"而长时间脏读。
 *
 * <h2>② 缓存击穿：热点 key 过期的瞬间</h2>
 * 某个爆款商品缓存到期，同一瞬间几千个请求全部未命中 → 全部涌向 DB。
 * <b>对策：互斥锁重建</b>——只有一个线程去查库并回写缓存，其余线程等待后重读缓存。
 * （另一条路是"逻辑过期 + 异步刷新"，适合极致热点；本项目用互斥锁，简单可靠更好讲。）
 *
 * <h2>③ 缓存雪崩：大量 key 同时过期</h2>
 * 商品批量导入时给了相同 TTL → 同一时刻集体失效 → DB 瞬时被打爆。
 * <b>对策：TTL 加随机抖动</b>——基础 30 分钟 + 0~10 分钟随机，把失效时间点打散。
 *
 * <h2>★ 为什么不用 @Cacheable</h2>
 * 注解方便，但三兄弟的防护（空值/互斥锁/随机TTL）都得自己写，那还不如
 * 显式写在 Service 里——逻辑一目了然，也便于在面试时逐条指给别人看。
 * 这也是本项目一贯的取舍：<b>可读性优先于"看起来高级"</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    /** 缓存空值的标记（防穿透）。用特殊串而不是 null，因为很多客户端不存 null。 */
    private static final String NULL_MARK = "__NULL__";
    /** 基础 TTL（秒） */
    private static final long BASE_TTL = 30 * 60;
    /** TTL 随机抖动上限（秒），防雪崩 */
    private static final int TTL_JITTER = 10 * 60;
    /** 空值 TTL（秒）：比正常短，避免"刚上架就查不到" */
    private static final long NULL_TTL = 2 * 60;

    private static final String KEY_PREFIX = "aimall:product:";
    private static final String LOCK_PREFIX = "aimall:lock:product:";

    private final RedisOps redisOps;
    private final ProductMapper productMapper;
    private final ProductService productService;

    /**
     * 查询商品详情（带完整缓存防护）。
     *
     * <p>流程：读缓存 → 命中直接返回（含空值标记）→ 未命中则加锁重建：
     * 拿到锁的线程查库回写，没拿到锁的线程短暂等待后重读缓存，
     * 仍读不到就直接查库（兜底，绝不返回空）。</p>
     */
    public ProductVO detailWithCache(Long productId) {
        String key = KEY_PREFIX + productId;

        // ---- 1. 读缓存 ----
        var cached = redisOps.getObject(key, ProductVO.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        // 命中"空值标记"：说明这个商品确实不存在，直接返回 null（防穿透）
        if (redisOps.get(NULL_MARK_KEY(productId)).isPresent()) {
            return null;
        }

        // ---- 2. 未命中：互斥锁重建（防击穿）----
        String lockKey = LOCK_PREFIX + productId;
        String lockValue = redisOps.tryLock(lockKey, 3000);
        try {
            if (lockValue != null) {
                // 拿到锁：查库 → 回写缓存
                return loadAndCache(productId, key);
            }
            // 没拿到锁：别人在重建，等一下再读
            sleepQuietly(50);
            var retry = redisOps.getObject(key, ProductVO.class);
            if (retry.isPresent()) {
                return retry.get();
            }
            // 兜底：仍读不到就直接查库（缓存只是旁路，不能让它决定可用性）
            return productService.detail(productId);
        } finally {
            if (lockValue != null) {
                redisOps.unlock(lockKey, lockValue);
            }
        }
    }

    /** 商品更新/下架时主动删除缓存（保证一致性） */
    public void evict(Long productId) {
        redisOps.delete(KEY_PREFIX + productId);
        redisOps.delete(NULL_MARK_KEY(productId));
    }

    // ------------------------------------------------------------------

    private ProductVO loadAndCache(Long productId, String key) {
        ProductVO vo = productService.detail(productId);
        if (vo == null) {
            // 防穿透：不存在也写标记，短期挡住重复查询
            redisOps.set(NULL_MARK_KEY(productId), "1", NULL_TTL);
            return null;
        }
        // 防雪崩：TTL 加随机抖动，避免同批商品同时失效
        long ttl = BASE_TTL + ThreadLocalRandom.current().nextInt(TTL_JITTER);
        redisOps.setObject(key, vo, ttl);
        return vo;
    }

    private String NULL_MARK_KEY(Long productId) {
        return KEY_PREFIX + "null:" + productId;
    }

    private void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
