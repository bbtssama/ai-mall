package com.aimall.pay.service;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.order.bean.Order;
import com.aimall.order.mapper.OrderMapper;
import com.aimall.pay.bean.Payment;
import com.aimall.pay.channel.PayChannel;
import com.aimall.pay.dto.CallbackResult;
import com.aimall.pay.dto.PayCreateVO;
import com.aimall.pay.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务 —— V3 商业闭环的最后一环。
 *
 * <h2>完整链路</h2>
 * <pre>
 *   create(订单) → 建支付单(PAYING) → 换收银台地址
 *        │
 *   用户付款（真实渠道跳支付宝；模拟渠道点本地收银台）
 *        │
 *   第三方异步回调 /callback/{channel}
 *        │
 *   ① 验签（防伪造回调）        ← 不验签 = 任何人都能把订单改成已支付
 *   ② 幂等（支付单状态机 CAS）   ← 不幂等 = 重复回调重复发货/重复记账
 *   ③ 金额核对（防篡改金额）     ← 不核对 = 1 分钱买走 iPhone
 *   ④ 订单状态流转 + 回写支付单
 *   ⑤ 返回 success 给第三方（否则对方会持续重发）
 * </pre>
 *
 * <h2>★ 三个"不做会出事"的点（面试必答）</h2>
 * <ol>
 *   <li><b>验签</b>：回调接口是公网可访问的，不验签等于把"修改订单状态"的权限开放给全世界。</li>
 *   <li><b>幂等</b>：第三方回调<b>必然重复</b>（网络超时重传、商户处理慢对方重试）。
 *       靠 payment_no 唯一键 + 状态机条件更新双保险。</li>
 *   <li><b>金额核对</b>：回调里的金额必须与我方支付单一致——这是最容易被忽略的一条，
 *       也是真实事故里最常见的一种（篡改金额下单）。</li>
 * </ol>
 *
 * <h2>为什么还要"主动查单"</h2>
 * 回调可能因网络抖动丢失（第三方认为通知成功、我方没收到）。
 * 所以订单详情页/定时任务要能<b>主动查单对账</b>（{@link #sync(String)}），
 * 不能只依赖被动通知——这是支付可靠性的兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 支付单默认有效期（分钟），超时由延迟消息关闭 */
    public static final int PAY_EXPIRE_MINUTES = 30;

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final PayChannelRouter router;

    /**
     * 发起支付：为订单创建支付单，返回收银台信息。
     *
     * <p>幂等：同一订单重复发起支付时，若已有有效支付单（PAYING/PAID）直接复用，
     * 避免产生两张待支付的单。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public PayCreateVO create(Long orderId, String channel) {
        Long userId = StpUtil.getLoginIdAsLong();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!Order.STATUS_PENDING_PAY.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "仅待支付订单可发起支付");
        }

        // 复用已有支付单（幂等）：避免用户反复点"去支付"产生多张单。
        // ★评审修正：PAYING 但已过期的单不能复用——过期单的回调/支付渠道侧已不可支付，
        // 复用它用户会永远付不了款。正确语义：关旧建新。
        Payment exist = paymentMapper.selectByOrderId(orderId);
        if (exist != null && Payment.STATUS_PAID.equals(exist.getStatus())) {
            return toVO(exist, router.resolve(exist.getChannel()));
        }
        if (exist != null && Payment.STATUS_PAYING.equals(exist.getStatus())) {
            boolean expired = exist.getExpireTime() != null
                    && exist.getExpireTime().isBefore(LocalDateTime.now());
            if (!expired) {
                return toVO(exist, router.resolve(exist.getChannel()));
            }
            paymentMapper.updateToClosed(exist.getPaymentNo(), Payment.STATUS_PAYING);
        }

        Payment p = new Payment();
        p.setPaymentNo(generatePaymentNo(userId));
        p.setOrderId(orderId);
        p.setOrderNo(order.getOrderNo());
        p.setUserId(userId);
        p.setAmount(order.getTotalAmount());
        p.setChannel(channel);
        p.setStatus(Payment.STATUS_PAYING);
        p.setExpireTime(LocalDateTime.now().plusMinutes(PAY_EXPIRE_MINUTES));
        paymentMapper.insert(p);

        return toVO(p, router.resolve(channel));
    }

    /**
     * 处理支付回调（核心）。
     *
     * @param channel 渠道码
     * @param params  回调原始参数（含 sign）
     * @return 处理结果；success=false 时第三方会重试
     */
    @Transactional(rollbackFor = Exception.class)
    public CallbackResult handleCallback(String channel, Map<String, String> params) {
        // ---------- ① 验签 ----------
        PayChannel payChannel = router.resolve(channel);
        if (!payChannel.verifyCallback(params)) {
            log.warn("支付回调验签失败，疑似伪造请求 channel={} params={}", channel, params);
            // 验签失败必须拒绝——这是安全红线，不能"为了成功率"放行
            return new CallbackResult(false, null, "sign-error");
        }

        // ---------- ② 定位支付单 ----------
        String paymentNo = router.paymentNoOf(params);
        Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
        if (payment == null) {
            log.warn("回调中的支付单不存在 paymentNo={}", paymentNo);
            return new CallbackResult(false, paymentNo, "payment-not-found");
        }

        // ---------- ③ 幂等：已支付则直接返回成功 ----------
        // 第三方重传时必须回 success，否则它会一直重发（很多支付网关重试 8 次以上）
        if (Payment.STATUS_PAID.equals(payment.getStatus())) {
            log.info("重复支付回调（幂等跳过）paymentNo={}", paymentNo);
            return new CallbackResult(true, paymentNo, "success");
        }

        // ---------- ④ 金额核对（红线：缺失即拒绝，不允许可选）----------
        // 评审修正：早期写法 amountStr != null 才核对——"回调不带金额字段"就能绕过核对，
        // 等于给"1 分钱买 399"留了门。金额是回调和支付单的必配字段，缺失本身就是异常。
        String amountStr = params.get("amount") == null ? params.get("total_amount") : params.get("amount");
        if (amountStr == null) {
            log.warn("回调缺少金额字段，拒绝 paymentNo={}", paymentNo);
            return new CallbackResult(false, paymentNo, "amount-missing");
        }
        BigDecimal paid;
        try {
            paid = new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            log.warn("回调金额非法 amount={} paymentNo={}", amountStr, paymentNo);
            return new CallbackResult(false, paymentNo, "amount-invalid");
        }
        if (paid.compareTo(payment.getAmount()) != 0) {
            log.error("回调金额与支付单不一致 paymentNo={} 期望={} 回调={}",
                    paymentNo, payment.getAmount(), paid);
            return new CallbackResult(false, paymentNo, "amount-mismatch");
        }

        // ---------- ⑤ 状态机 CAS 更新支付单（幂等第二道防线）----------
        String tradeNo = router.tradeNoOf(params);
        int rows = paymentMapper.updateToPaid(paymentNo, Payment.STATUS_PAYING,
                tradeNo, String.valueOf(params));
        if (rows == 0) {
            // 并发下被别的线程处理了，或被关闭了——都不需要再处理
            log.info("支付单状态已变更，跳过处理 paymentNo={} status={}",
                    paymentNo, payment.getStatus());
            return new CallbackResult(true, paymentNo, "success");
        }

        // ---------- ⑥ 订单状态流转 ----------
        // 同样用条件更新：只有待支付才转为已支付（防与"超时取消"竞态）
        int orderRows = orderMapper.updateStatus(payment.getOrderId(),
                Order.STATUS_PENDING_PAY, Order.STATUS_PAID, LocalDateTime.now());
        if (orderRows == 0) {
            // 极端情况：支付成功瞬间订单已被超时任务取消 → 需要走退款/人工兜底
            log.error("支付成功但订单状态流转失败（可能已被取消，需退款）paymentNo={} orderId={}",
                    paymentNo, payment.getOrderId());
        }

        log.info("支付成功 paymentNo={} orderId={} amount={}",
                paymentNo, payment.getOrderId(), payment.getAmount());
        return new CallbackResult(true, paymentNo, "success");
    }

    /**
     * 主动查单对账：回调丢失时的兜底（订单详情页轮询 / 定时任务调用）。
     *
     * <p>以<b>第三方</b>结果为准修正本地状态——这是"回调+查单"双保险的查单侧。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean sync(String paymentNo) {
        Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
        if (payment == null || Payment.STATUS_PAID.equals(payment.getStatus())) {
            return false;
        }
        PayChannel ch = router.resolve(payment.getChannel());
        PayChannel.QueryResult r = ch.query(paymentNo);
        if (PayChannel.QueryResult.UNKNOWN.equals(r.status())) {
            // 查不到就以本地为准，绝不擅改状态
            return false;
        }
        if (PayChannel.QueryResult.PAID.equals(r.status())) {
            paymentMapper.updateToPaid(paymentNo, Payment.STATUS_PAYING, r.thirdTradeNo(), "query-sync");
            orderMapper.updateStatus(payment.getOrderId(),
                    Order.STATUS_PENDING_PAY, Order.STATUS_PAID, LocalDateTime.now());
            log.info("主动查单补偿支付成功 paymentNo={}", paymentNo);
            return true;
        }
        return false;
    }

    /** 关闭支付单（订单取消 / 超时未支付） */
    @Transactional(rollbackFor = Exception.class)
    public void closeIfPaying(Long orderId) {
        Payment p = paymentMapper.selectByOrderId(orderId);
        if (p != null) {
            paymentMapper.updateToClosed(p.getPaymentNo(), Payment.STATUS_PAYING);
        }
    }

    public Payment detail(String paymentNo) {
        return paymentMapper.selectByPaymentNo(paymentNo);
    }

    public Payment byOrder(Long orderId) {
        return paymentMapper.selectByOrderId(orderId);
    }

    // ------------------------------------------------------------------

    private PayCreateVO toVO(Payment p, PayChannel ch) {
        String subject = "订单 " + p.getOrderNo();
        PayChannel.CashierInfo info = ch.create(p.getPaymentNo(), p.getAmount(), subject);
        PayCreateVO vo = new PayCreateVO();
        vo.setPaymentNo(p.getPaymentNo());
        vo.setCashierUrl(info.payUrl());
        vo.setChannel(p.getChannel());
        vo.setExpireAt(p.getExpireTime() == null ? null
                : p.getExpireTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        return vo;
    }

    private String generatePaymentNo(Long userId) {
        return "P" + NO_FMT.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(1000, 10000)
                + (userId % 1000);
    }
}
