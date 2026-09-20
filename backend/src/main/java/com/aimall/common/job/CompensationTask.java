package com.aimall.common.job;

import com.aimall.content.bean.Note;
import com.aimall.content.mapper.NoteMapper;
import com.aimall.content.service.AuditService;
import com.aimall.order.bean.Order;
import com.aimall.order.mapper.OrderMapper;
import com.aimall.order.mq.OrderCancelConsumer;
import com.aimall.pay.bean.Payment;
import com.aimall.pay.mapper.PaymentMapper;
import com.aimall.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兜底补偿任务 —— 把"尽力而为"的异步链路最终收敛为"确定完成"。
 *
 * <h2>★ 为什么必须有它（异步系统的不等式）</h2>
 * 本项目的三条异步链路各自都有"最优路径"，但最优路径每一环都可能掉链子：
 * <pre>
 *   订单超时取消：延迟消息（TTL+DLX）── MQ 宕机/消息丢失/投递失败 ──► 单永远待支付
 *   内容审核：    MQ 异步 AI 审    ── 消息丢失/消费失败被 ACK ──► 笔记永远审核中
 *   支付状态：    第三方异步回调   ── 回调网络丢失 ──► 用户付了钱但订单不流转
 * </pre>
 * <b>主链路追求快（异步），补偿任务保证最终对（定时扫描）</b>——这是"最终一致性"
 * 在本项目的具体形态：不追求每条消息必达，而是接受丢失率，用周期性对账收敛。
 * 代价是分钟级延迟，对这三个场景完全可接受（订单多挂 5 分钟、审核晚 10 分钟）。
 *
 * <h2>设计要点（面试可讲）</h2>
 * <ul>
 *   <li><b>幂等是前提</b>：三个扫描的重放动作全部复用各自的状态机 CAS
 *       （updateStatus WHERE status=旧值），扫 100 次和扫 1 次结果一致；</li>
 *   <li><b>分批限量</b>：每次最多处理一批（LIMIT），失败下一轮再来，不一次性拖垮自己；</li>
 *   <li><b>独立线程池</b>：Spring @Scheduled 默认单线程调度——任务互相等待会拖延，
 *       生产应配置独立调度线程池（当前任务量小，默认够用，注释为升级路径）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationTask {

    private final OrderMapper orderMapper;
    private final NoteMapper noteMapper;
    private final PaymentMapper paymentMapper;
    private final AuditService auditService;
    private final PaymentService paymentService;
    /** 复用消费端的取消逻辑（@Transactional + 状态机 CAS + 回补库存 + 关支付单） */
    private final OrderCancelConsumer orderCancelConsumer;

    /**
     * 订单超时兜底：扫描"本该被延迟消息取消却仍是待支付"的订单。
     * 宽限 5 分钟（30 分钟 TTL + 5），避免与正常延迟消息重复劳动（重复也无害，幂等）。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void cancelTimeoutOrders() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(35);
        List<Order> stale = orderMapper.selectTimeoutPending(before, 100);
        if (stale.isEmpty()) {
            return;
        }
        log.info("[补偿] 发现 {} 笔超时未取消订单，逐笔执行取消", stale.size());
        for (Order o : stale) {
            // 直接调消费端方法：它自带事务 + CAS + 全部取消副作用，与消息路径完全同语义
            try {
                orderCancelConsumer.onCancelMessage(o.getId());
            } catch (Exception e) {
                log.error("[补偿] 订单取消失败 orderId={}: {}", o.getId(), e.getMessage());
            }
        }
    }

    /**
     * 审核堆积兜底：扫描"审核中超 10 分钟"的笔记重新送审。
     *
     * <p><b>为什么这条补偿能生效</b>（2026-09-20 修复后确认）：</p>
     * <ul>
     *   <li>重审用的是<b>同一个版本号</b>（{@code t_note.audit_version} 未变），
     *       所以它对「同一次审核」的重复执行——这正是幂等键要保护的场景；</li>
     *   <li>而 t_audit_record 的唯一键含 {@code status}，AI 失败留下的 ERROR 流水
     *       <b>不会占用终态幂等位</b>，因此补审拿到 PASS/REJECT 时能正常落库并流转。
     *       修复前唯一键不含 status，ERROR 会把后续所有补审挡在 INSERT IGNORE 之外——
     *       那正是"永久卡在 AUDITING"的真正成因。</li>
     * </ul>
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 90 * 1000)
    public void retryStuckAudits() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(10);
        List<Note> stuck = noteMapper.selectAuditingBefore(before, 100);
        if (stuck.isEmpty()) {
            return;
        }
        log.info("[补偿] 发现 {} 篇审核堆积笔记，重新送审", stuck.size());
        for (Note n : stuck) {
            try {
                int version = n.getAuditVersion() == null ? 1 : n.getAuditVersion();
                auditService.auditOnce(n, version);
            } catch (Exception e) {
                log.error("[补偿] 笔记重审失败 noteId={}: {}", n.getId(), e.getMessage());
            }
        }
    }

    /**
     * 支付对账兜底：扫描"超时仍 PAYING"的支付单，主动向渠道查单。
     * 查到已支付 → 补偿订单状态（用户付了钱系统却不知道的资损场景）。
     * sync 内部按渠道真实状态 CAS 更新，未支付不动。
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 120 * 1000)
    public void syncStuckPayments() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(10);
        List<Payment> paying = paymentMapper.selectPayingBefore(before, 100);
        if (paying.isEmpty()) {
            return;
        }
        log.info("[补偿] 发现 {} 笔超时 PAYING 支付单，主动查单对账", paying.size());
        for (Payment p : paying) {
            try {
                boolean recovered = paymentService.sync(p.getPaymentNo());
                if (recovered) {
                    log.info("[补偿] 支付单查单补偿成功 paymentNo={} orderId={}",
                            p.getPaymentNo(), p.getOrderId());
                }
            } catch (Exception e) {
                log.error("[补偿] 支付查单失败 paymentNo={}: {}",
                        p.getPaymentNo(), e.getMessage());
            }
        }
    }
}
