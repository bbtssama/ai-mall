package com.aimall.pay.service;

import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.common.redis.RedisOps;
import com.aimall.goods.bean.ProductSku;
import com.aimall.goods.mapper.ProductSkuMapper;
import com.aimall.order.bean.Order;
import com.aimall.order.mapper.OrderMapper;
import com.aimall.pay.bean.DropActivity;
import com.aimall.pay.bean.DropRecord;
import com.aimall.pay.mapper.DropMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 限量发售（Limited Drop）—— 防超卖的三层防护。
 *
 * <h2>★ 为什么是"限量发售"而不是"秒杀"</h2>
 * 本项目对标得物/潮玩电商，真实场景是<b>限量发售/抽签</b>（球鞋、潮玩、联名款），
 * 不是淘宝双十一秒杀。业务讲得通，技术含量一点没少——详见设计文档 5.3.1。
 *
 * <h2>三层防护（对比"只用一把锁"的高下）</h2>
 * <pre>
 *   第1层 Redis 原子预减（Lua）  —— 挡住 99% 流量，内存操作，不碰 DB
 *   第2层 行锁 CAS 扣库存        —— 与 V1 下单同一套（UPDATE ... WHERE stock>=?）
 *   第3层 限购唯一索引           —— uk(activity_id,user_id) 保证一人一单
 * </pre>
 *
 * <h2>为什么第 1 层必须用 Lua 脚本</h2>
 * "先 GET 判断余量，再 DECR 扣减"是两步操作，<b>并发下会超卖</b>：
 * 100 个请求同时读到还剩 1 件，于是全部通过判断。
 * Lua 脚本在 Redis 里<b>原子执行</b>（单线程，脚本不被打断），
 * "判断+扣减"成为一个不可分割的操作。
 *
 * <h2>为什么不用 Redisson 分布式锁</h2>
 * 预减是<b>一次原子计数操作</b>，用 Lua 直接完成，比"加锁→读→改→解锁"更快也更简单
 * （加锁方案每次要两次网络往返，还会成为串行瓶颈）。
 * Redisson 的锁适合"临界区是一段复杂业务逻辑"的场景——那是另一回事。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DropService {

    /** 预减库存的 Lua：库存不足返回 -1，否则扣减并返回剩余量 */
    private static final String DEDUCT_LUA =
            "local stock = tonumber(redis.call('get', KEYS[1]) or '-1') " +
            "if stock < 0 then return -2 end " +              // -2 = 未预热/键不存在
            "if stock < tonumber(ARGV[1]) then return -1 end " + // -1 = 库存不足
            "return redis.call('decrby', KEYS[1], ARGV[1])";

    /** 回补库存的 Lua（与 DECRBY 对称的 INCRBY）：抢购失败时归还预减的名额 */
    private static final String INCRBY_LUA =
            "return redis.call('incrby', KEYS[1], ARGV[1])";

    private static final String STOCK_KEY_PREFIX = "aimall:drop:stock:";

    private final DropMapper dropMapper;
    private final RedisOps redisOps;
    private final OrderMapper orderMapper;
    private final ProductSkuMapper skuMapper;

    /** 活动开始时把库存预热进 Redis（强制覆盖语义：开售事件触发，以 DB 为准重置） */
    public void warmUp(Long activityId) {
        DropActivity act = dropMapper.selectActivity(activityId);
        if (act == null) {
            return;
        }
        redisOps.set(STOCK_KEY_PREFIX + activityId, String.valueOf(act.getDropStock()), 24 * 3600);
        log.info("限量发售库存已预热 activityId={} stock={}", activityId, act.getDropStock());
    }

    /**
     * 懒预热：仅当 Redis 里<b>确实没有</b>库存键时才灌入。
     *
     * <p>★ P0 修复：早期实现是 check-then-set（GET 判空 → SET 覆盖），
     * 并发首访时两个请求都会判"未预热"，后一个 SET 会把<b>已经扣减过的库存重置回全量</b>
     * ——直接超卖。现在用 SET NX（{@code setIfAbsent}）原子化"判断+写入"，
     * 只有一个请求能真正灌入，其余的发现键已存在自然跳过。</p>
     */
    public void warmUpIfAbsent(Long activityId) {
        if (redisOps.get(STOCK_KEY_PREFIX + activityId).isPresent()) {
            return;
        }
        DropActivity act = dropMapper.selectActivity(activityId);
        if (act == null) {
            return;
        }
        // NX 写入：并发下只有第一个成功；Redis 恰好不可用时 safe() 返回 false（静默降级走 DB）
        boolean written = redisOps.setIfAbsent(
                STOCK_KEY_PREFIX + activityId, String.valueOf(act.getDropStock()), 24 * 3600);
        if (written) {
            log.info("限量发售库存懒预热完成 activityId={} stock={}", activityId, act.getDropStock());
        }
    }

    /**
     * 抢购：三层防护依次生效。
     *
     * @return 订单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long buy(Long userId, Long activityId, int quantity) {
        DropActivity act = dropMapper.selectActivity(activityId);
        if (act == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "发售活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(act.getStartTime()) || now.isAfter(act.getEndTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不在发售时间内");
        }
        // 语义说明：per_limit 约束【单次】购买上限；"一人一次"由第 3 层的 uk(activity_id,user_id) 硬保证
        if (quantity > act.getPerLimit()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "超出单次限购数量");
        }

        // ---- 第 1 层：Redis 原子预减 ----
        // 返回值约定：-2=键不存在(Redis 不可用/未预热) -1=库存不足 >=0=扣减后剩余
        Long remain = redisOps.eval(DEDUCT_LUA,
                java.util.List.of(STOCK_KEY_PREFIX + activityId), String.valueOf(quantity));
        boolean redisDeducted = remain != null && remain >= 0;   // 后续失败要回补的标记
        if (remain != null && remain == -1) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "已被抢完");
        }
        // remain == null 或 -2：Redis 不可用/未预热 → 降级，直接交给数据库兜底（下面第 2 层）

        try {
            return doBuyInDb(userId, act, quantity);
        } catch (RuntimeException e) {
            // ★ 失败必须回补 Redis 预减，否则白吃名额：
            //   限购撞 uk / DB CAS 未命中 / 订单插入失败……任一失败，
            //   Redis 已扣的量若不还，会出现"看起来抢完了实际有货"（对用户是资损）。
            //   注：回补用 INCRBY，与预减的 DECRBY 对称；Redis 恰好挂了则由
            //   warmUp 重灌兜底（最终一致，见 warmUp 注释）。
            if (redisDeducted) {
                redisOps.eval(INCRBY_LUA,
                        java.util.List.of(STOCK_KEY_PREFIX + activityId), String.valueOf(quantity));
            }
            throw e;
        }
    }

    /** 第 2/3 层：DB 行锁 CAS 扣库存 + 建单 + 限购唯一索引（独立方法便于异常统一回补） */
    private Long doBuyInDb(Long userId, DropActivity act, int quantity) {
        // ---- 第 2 层：行锁 CAS 扣库存（与 V1 下单同一套）----
        ProductSku sku = skuMapper.selectById(act.getSkuId());
        if (sku == null) {
            throw new BusinessException(ResultCode.SKU_NOT_FOUND);
        }
        if (sku.getStock() < quantity) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "库存不足");
        }
        if (skuMapper.deductStock(sku.getId(), quantity) == 0) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "库存不足");
        }

        // 建订单：单号沿用 V1 风格（17位时间戳+4位随机+用户尾号），
        // uk_order_no 兜底——纯毫秒时间戳并发下碰撞面大（评审修正）
        Order order = new Order();
        order.setOrderNo(generateOrderNo(userId));
        order.setUserId(userId);
        order.setTotalAmount(act.getDropPrice().multiply(java.math.BigDecimal.valueOf(quantity)));
        order.setStatus(Order.STATUS_PENDING_PAY);
        orderMapper.insert(order);

        // ---- 第 3 层：限购唯一索引（uk_activity_user）幂等 ----
        // DuplicateKey 必须让业务感知（提示"限购"），与点赞的"静默幂等"语义相反
        try {
            DropRecord record = new DropRecord();
            record.setActivityId(act.getId());
            record.setUserId(userId);
            record.setOrderId(order.getId());
            record.setQuantity(quantity);
            dropMapper.insertRecord(record);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "每人限购一次");
        }

        log.info("限量发售抢购成功 userId={} activityId={} orderId={}", userId, act.getId(), order.getId());
        return order.getId();
    }

    /** 与 OrderServiceImpl.generateOrderNo 同风格（17位时间戳+4位随机+用户尾号），uk 兜底 */
    private String generateOrderNo(Long userId) {
        return "D" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(java.time.LocalDateTime.now())
                + java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000)
                + (userId % 1000);
    }

    /** 活动列表 */
    public List<DropActivity> listOngoing() {
        return dropMapper.selectOngoing(LocalDateTime.now());
    }

    public DropActivity detail(Long activityId) {
        return dropMapper.selectActivity(activityId);
    }
}
