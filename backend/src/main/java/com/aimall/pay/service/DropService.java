package com.aimall.pay.service;

import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.common.redis.RedisOps;
import com.aimall.pay.bean.DropActivity;
import com.aimall.pay.bean.DropRecord;
import com.aimall.pay.dto.DropBuyVO;
import com.aimall.pay.dto.DropResultVO;
import com.aimall.pay.mapper.DropMapper;
import com.aimall.pay.mq.DropMqConfig;
import com.aimall.pay.mq.DropOrderExecutor;
import com.aimall.pay.mq.DropOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 限量发售（Limited Drop）—— 经典「Redis 预减 + MQ 异步下单削峰」的提交侧。
 *
 * <h2>★ 为什么是"限量发售"而不是"秒杀"</h2>
 * 本项目对标得物/潮玩电商，真实场景是<b>限量发售/抽签</b>（球鞋、潮玩、联名款），
 * 不是淘宝双十一秒杀。业务讲得通，技术含量一点没少。
 *
 * <h2>完整链路（削峰版，与同步版的本质差异）</h2>
 * <pre>
 *   HTTP 线程（快，只做内存级操作）            MQ 消费线程（匀速，扛 DB 写）
 *   ─────────────────────────────           ─────────────────────────────
 *   ① 校验活动/时间/限购（读库一次）
 *   ② Lua 原子预减（判重+扣库存）                ① 回查活动（消息不信任）
 *   ③ 投递 DropOrderMessage ──────► ──────►  ② CAS 扣 SKU 库存（防超卖底线）
 *   ④ 立即返回 QUEUED（前端开始轮询）            ③ 建订单 + drop_record(uk 幂等)
 *                                            ④ afterCommit 挂 30min 超时取消
 *                                            失败 → 回补 Redis + 失败标记
 * </pre>
 *
 * <h2>为什么"预减"挡得住流量（回答"Redis 到底挡了什么"）</h2>
 * Lua 预减是<b>准入闸门</b>：放行的消息数 ≤ 库存量，库存抢完后洪峰在 Redis 就被拒绝
 * （纯内存操作），DB 完全无感；<b>同时</b> MQ 把通过闸门的瞬时洪峰摊平成消费速率，
 * DB 行锁不再被万级请求争抢——"挡量"（Redis）与"削峰"（MQ）是两件事，缺一不可。
 *
 * <h2>降级链（与内容审核同款"可插拔"原则）</h2>
 * <pre>
 *   ① Redis 可用 + MQ 可用  ──► 预减 + 投递（标准链路）
 *   ② MQ 不可用              ──► 预减 + 本地线程池异步建单（无削峰但体验一致）
 *   ③ 投递失败（AmqpException）──► 立即回补预减 + 提示重试（名额不白占）
 *   ④ Redis 不可用           ──► 本地线程池异步建单（DB CAS + uk 兜底防超卖）
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DropService {

    private final DropMapper dropMapper;
    private final RedisOps redisOps;
    private final DropRedisSupport dropRedis;
    private final DropOrderExecutor dropOrderExecutor;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    /** 本地降级路径用的线程池（AsyncConfig 的 taskExecutor：有界队列+CallerRuns） */
    private final ThreadPoolTaskExecutor taskExecutor;

    /** 活动开始时把库存预热进 Redis（强制覆盖语义：开售事件触发，以 DB 为准重置） */
    public void warmUp(Long activityId) {
        DropActivity act = dropMapper.selectActivity(activityId);
        if (act == null) {
            return;
        }
        redisOps.set(dropRedis.stockKey(activityId), String.valueOf(act.getDropStock()), 24 * 3600);
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
        if (redisOps.get(dropRedis.stockKey(activityId)).isPresent()) {
            return;
        }
        DropActivity act = dropMapper.selectActivity(activityId);
        if (act == null) {
            return;
        }
        // NX 写入：并发下只有第一个成功；Redis 恰好不可用时 safe() 返回 false（静默降级走 DB）
        boolean written = redisOps.setIfAbsent(
                dropRedis.stockKey(activityId), String.valueOf(act.getDropStock()), 24 * 3600);
        if (written) {
            log.info("限量发售库存懒预热完成 activityId={} stock={}", activityId, act.getDropStock());
        }
    }

    /**
     * 提交抢购（HTTP 线程内完成，不含任何 DB 写操作——这是削峰的前提）。
     *
     * <p>返回"已受理"而非订单号：订单由消费者异步创建，前端轮询
     * {@link #result} 获取最终结果（QUEUED → SUCCESS/FAILED）。</p>
     */
    public DropBuyVO submitBuy(Long userId, Long activityId, int quantity) {
        // ---- ① 前置校验（读库一次：活动的权威数据，不做缓存以免校验口径漂移）----
        DropActivity act = dropMapper.selectActivity(activityId);
        if (act == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "发售活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(act.getStartTime()) || now.isAfter(act.getEndTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不在发售时间内");
        }
        if (quantity > act.getPerLimit()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "超出单次限购数量");
        }
        // 已有成功记录（重复抢购）：uk 视角用户已"抢到"，直接告知
        DropRecord existRecord = dropMapper.selectRecord(activityId, userId);
        if (existRecord != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "每人限购一次，已抢到过");
        }

        // ---- ② Redis 原子预减（判重+扣减同脚本）----
        Long remain = dropRedis.tryDeduct(activityId, userId, quantity);
        if (remain != null && remain == -3) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请勿重复抢购，正在处理中");
        }
        if (remain != null && remain == -1) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "已被抢完");
        }
        if (remain != null && remain == -2) {
            // 未预热（Redis 刚清空/首次）：懒预热后让用户重试，本次不放行
            warmUpIfAbsent(activityId);
            throw new BusinessException(ResultCode.BAD_REQUEST, "系统就绪中，请重试");
        }

        DropOrderMessage msg = new DropOrderMessage(activityId, userId, quantity);
        if (remain == null) {
            // ---- ④ Redis 不可用：降级本地异步建单（DB CAS + uk 兜底防超卖）----
            submitLocal(msg);
            return DropBuyVO.queued();
        }

        // ---- ③ 投递 MQ（削峰核心：HTTP 线程到这就该结束了）----
        RabbitTemplate rabbit = rabbitTemplateProvider.getIfAvailable();
        if (rabbit == null) {
            submitLocal(msg);   // MQ 未启动：本地线程池异步建单
            return DropBuyVO.queued();
        }
        try {
            rabbit.convertAndSend(DropMqConfig.EXCHANGE, DropMqConfig.ORDER_ROUTING_KEY, msg);
            log.info("抢购消息已入队 activityId={} userId={} remain={}", activityId, userId, remain);
        } catch (AmqpException e) {
            // 投递失败必须立即回补预减：名额不白占，用户可重试
            dropRedis.rollbackDeduct(activityId, userId, quantity);
            log.error("抢购消息投递失败（已回补预减）activityId={} userId={}: {}",
                    activityId, userId, e.getMessage());
            throw new BusinessException(ResultCode.BAD_REQUEST, "系统繁忙，请稍后重试");
        }
        return DropBuyVO.queued();
    }

    /**
     * 抢购结果查询（前端轮询）。
     *
     * <p>判定优先级：DB 记录（真值）&gt; 失败标记 &gt; 受理标记（处理中）&gt; 未参与。
     * 前端拿到 SUCCESS 才跳订单页；QUEUED 间隔 1~2 秒继续轮询。</p>
     */
    public DropResultVO result(Long userId, Long activityId) {
        DropRecord record = dropMapper.selectRecord(activityId, userId);
        if (record != null) {
            return DropResultVO.success(record.getOrderId());
        }
        if (dropRedis.hasFailedMarker(activityId, userId)) {
            return DropResultVO.failed();
        }
        if (dropRedis.hasQueuedMarker(activityId, userId)) {
            return DropResultVO.queued();
        }
        return DropResultVO.none();
    }

    /** 活动列表 */
    public List<DropActivity> listOngoing() {
        return dropMapper.selectOngoing(LocalDateTime.now());
    }

    public DropActivity detail(Long activityId) {
        return dropMapper.selectActivity(activityId);
    }

    /**
     * 本地降级路径：手动提交线程池（与审核降级同款）。
     * 不用 @Async 的原因：自调用不走代理（AuditServiceImpl 有完整复盘）。
     * execute() 内部的失败分流（回补+标记）对降级路径同样生效。
     */
    private void submitLocal(DropOrderMessage msg) {
        taskExecutor.execute(() -> {
            try {
                dropOrderExecutor.execute(msg);
            } catch (Exception e) {
                // 未知异常在本地路径无处死信，只能 ERROR 流水（与审核消费端同取舍）
                log.error("本地异步建单失败 activityId={} userId={}: {}",
                        msg.activityId(), msg.userId(), e.getMessage(), e);
            }
        });
    }
}
