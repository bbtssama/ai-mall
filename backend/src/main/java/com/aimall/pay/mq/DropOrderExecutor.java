package com.aimall.pay.mq;

import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.order.bean.Order;
import com.aimall.order.mapper.OrderMapper;
import com.aimall.order.mq.OrderDelayMessageSender;
import com.aimall.pay.bean.DropActivity;
import com.aimall.pay.bean.DropRecord;
import com.aimall.pay.mapper.DropMapper;
import com.aimall.pay.service.DropRedisSupport;
import com.aimall.goods.bean.ProductSku;
import com.aimall.goods.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抢购建单执行器 —— MQ 消费端与本地降级路径共用的「真正建单」逻辑。
 *
 * <h2>为什么独立于 DropService（一个类能写完为什么拆两个）</h2>
 * 本地降级路径（MQ 不可用时线程池执行）需要<b>事务方法经代理调用</b>——
 * 若 createDropOrder 是 DropService 的私有方法，DropService 内部
 * {@code this.createDropOrder(...)} 自调用不走 AOP 代理，@Transactional 失效变裸奔
 * （与 @Async 自调用失效同源）。拆成独立 bean 后，消费端/降级端都通过 Spring 代理
 * 调用它，事务语义稳定。
 *
 * <h2>失败处理矩阵（消费端三类异常，三套路数）</h2>
 * <pre>
 *   DuplicateKeyException（uk_activity_user 冲突）
 *     → 用户已有成功记录（重复投递/并发提交）→ 回补 Redis 预减，幂等跳过
 *   BusinessException（库存不足/时间不符等业务性失败）
 *     → 回补 Redis 预减 + 写失败标记，ACK（重试也不会成功，不浪费）
 *   其他 Exception（DB 抖动等未知故障）
 *     → 原样重抛 → default-requeue-rejected=false → 进死信队列等人工
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DropOrderExecutor {

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final DropMapper dropMapper;
    private final OrderMapper orderMapper;
    private final ProductSkuMapper skuMapper;
    private final DropRedisSupport dropRedis;
    private final OrderDelayMessageSender delayMessageSender;

    /**
     * 建单编排入口（MQ 消费者与本地降级都调它）：建单 + 三类失败分流。
     */
    public void execute(DropOrderMessage msg) {
        try {
            createDropOrder(msg);
            log.info("抢购建单成功 activityId={} userId={} quantity={}",
                    msg.activityId(), msg.userId(), msg.quantity());
        } catch (DuplicateKeyException e) {
            // 重复投递/并发提交：uk 已有记录说明该用户此前已建单成功。
            // 回补本条消息的预减（并发提交场景下它的名额确实没被兑现）。
            // 注：若是纯重投（首条已消费），这次回补会让 Redis 多记一点库存——
            // 多放几个请求进来但 DB CAS 仍兜底，方向安全；少记则会让用户"有货抢不到"。
            dropRedis.rollbackDeduct(msg.activityId(), msg.userId(), msg.quantity());
            log.info("抢购建单重复（uk 幂等跳过并回补）activityId={} userId={}",
                    msg.activityId(), msg.userId());
        } catch (BusinessException e) {
            // 业务性失败：回补预减 + 标记失败（前端轮询到 FAILED），消息 ACK 不重试
            dropRedis.rollbackDeduct(msg.activityId(), msg.userId(), msg.quantity());
            dropRedis.markFailed(msg.activityId(), msg.userId());
            log.warn("抢购建单业务失败（已回补+标记）activityId={} userId={} reason={}",
                    msg.activityId(), msg.userId(), e.getMessage());
        }
        // 其他异常原样抛出：交给监听容器 reject（不重入队）→ 死信队列人工兜底
    }

    /**
     * 事务建单：CAS 扣库存 + 建订单 + 限购唯一索引。
     *
     * <p>与早期同步版的三层防护完全一致，只是"谁来调它"从 HTTP 线程变成了 MQ 消费者
     * ——削峰改变的是<b>流量到达 DB 的节奏</b>，防超卖的兜底层次一寸不少。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void createDropOrder(DropOrderMessage msg) {
        DropActivity act = dropMapper.selectActivity(msg.activityId());
        if (act == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "发售活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(act.getStartTime()) || now.isAfter(act.getEndTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不在发售时间内");
        }

        // 第 2 层：DB 行锁 CAS 扣库存（真正的防超卖底线，Redis 只是准入层）
        ProductSku sku = skuMapper.selectById(act.getSkuId());
        if (sku == null) {
            throw new BusinessException(ResultCode.SKU_NOT_FOUND);
        }
        if (skuMapper.deductStock(sku.getId(), msg.quantity()) == 0) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "已被抢完");
        }

        // 建订单（金额来自 DB 回查，不信任消息体）
        Order order = new Order();
        order.setOrderNo(generateOrderNo(msg.userId()));
        order.setUserId(msg.userId());
        order.setTotalAmount(act.getDropPrice().multiply(
                java.math.BigDecimal.valueOf(msg.quantity())));
        order.setStatus(Order.STATUS_PENDING_PAY);
        orderMapper.insert(order);

        // 第 3 层：限购唯一索引 uk(activity_id, user_id)
        // DuplicateKey 会作为事务回滚信号抛给 execute() 分流（幂等）
        DropRecord record = new DropRecord();
        record.setActivityId(act.getId());
        record.setUserId(msg.userId());
        record.setOrderId(order.getId());
        record.setQuantity(msg.quantity());
        dropMapper.insertRecord(record);

        // 建单成功必挂超时取消（与普通下单同一出口：OrderDelayMessageSender）
        delayMessageSender.sendAfterCommit(order.getId());
    }

    /** 与 OrderServiceImpl.generateOrderNo 同风格（17位时间戳+4位随机+用户尾号），uk_order_no 兜底 */
    private String generateOrderNo(Long userId) {
        return "D" + ORDER_NO_FMT.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(1000, 10000)
                + (userId % 1000);
    }
}
