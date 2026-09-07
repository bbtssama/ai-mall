package com.aimall.pay.service;

import com.aimall.order.bean.Order;
import com.aimall.order.mapper.OrderMapper;
import com.aimall.pay.bean.Payment;
import com.aimall.pay.channel.MockPayChannel;
import com.aimall.pay.dto.CallbackResult;
import com.aimall.pay.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付回调测试 —— 验签 / 幂等 / 金额核对 三条安全红线。
 *
 * <p>测试策略：用<b>真实的</b> {@link MockPayChannel} 做签名/验签（HMAC-SHA256 算法真实执行），
 * 只 mock 掉数据库层——这样测试覆盖的是"签名算法 + 校验逻辑"的组合行为，
 * 而不是 mock 出来的假阳性。</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private OrderMapper orderMapper;

    private PaymentService paymentService;
    private MockPayChannel mockChannel;

    private static final String PAYMENT_NO = "P202609070900001231";
    private static final long ORDER_ID = 99L;

    @BeforeEach
    void setUp() {
        mockChannel = new MockPayChannel();
        // @Value 在纯单测中不生效，用反射注入与生产一致的密钥
        ReflectionTestUtils.setField(mockChannel, "secret", "aimall-mock-secret");

        PayChannelRouter router = new PayChannelRouter(List.of(mockChannel));
        paymentService = new PaymentService(paymentMapper, orderMapper, router);
    }

    /** 构造一个"第三方风格"的合法回调报文（带正确签名） */
    private Map<String, String> signedParams(String amount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("payment_no", PAYMENT_NO);
        params.put("order_no", "O20260907");
        params.put("trade_no", "THIRD-123");
        params.put("amount", amount);
        params.put("status", "PAID");
        params.put(MockPayChannel.SIGN_PARAM, mockChannel.sign(params));
        return params;
    }

    private Payment payingOrder() {
        Payment p = new Payment();
        p.setPaymentNo(PAYMENT_NO);
        p.setOrderId(ORDER_ID);
        p.setOrderNo("O20260907");
        p.setUserId(1L);
        p.setAmount(new BigDecimal("399.00"));
        p.setChannel(MockPayChannel.CODE);
        p.setStatus(Payment.STATUS_PAYING);
        return p;
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("验签失败（篡改金额）→ 拒绝处理，不更新任何状态")
    void callback_whenSignInvalid_shouldRejectAndNeverUpdate() {
        Map<String, String> params = signedParams("399.00");
        // 模拟攻击者篡改金额后重放（签名未重算 → 验签必失败）
        params.put("amount", "0.01");

        CallbackResult r = paymentService.handleCallback("MOCK", params);

        assertFalse(r.success());
        assertEquals("sign-error", r.message());
        // 安全线：验签失败绝不能触发任何状态变更
        verify(paymentMapper, never()).updateToPaid(anyString(), anyString(), anyString(), anyString());
        verify(orderMapper, never()).updateStatus(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("重复回调（支付单已是 PAID）→ 幂等返回 success，不再重复更新")
    void callback_whenAlreadyPaid_shouldSkipIdempotently() {
        Map<String, String> params = signedParams("399.00");
        Payment paid = payingOrder();
        paid.setStatus(Payment.STATUS_PAID);   // 已是终态
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO)).thenReturn(paid);

        CallbackResult r = paymentService.handleCallback("MOCK", params);

        // 必须回 success：否则第三方会认为通知失败而持续重发
        assertTrue(r.success());
        verify(paymentMapper, never()).updateToPaid(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("金额不一致（回调 1 分钱买 399 的单）→ 拒绝，不更新状态")
    void callback_whenAmountMismatch_shouldReject() {
        Map<String, String> params = signedParams("0.01");   // 篡改金额并重签（模拟篡改报文攻击）
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO)).thenReturn(payingOrder());

        CallbackResult r = paymentService.handleCallback("MOCK", params);

        assertFalse(r.success());
        assertEquals("amount-mismatch", r.message());
        verify(paymentMapper, never()).updateToPaid(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("合法回调 → 支付单与订单状态流转成功")
    void callback_whenValid_shouldUpdatePaymentAndOrder() {
        Map<String, String> params = signedParams("399.00");
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO)).thenReturn(payingOrder());
        when(paymentMapper.updateToPaid(eq(PAYMENT_NO), eq(Payment.STATUS_PAYING), anyString(), anyString()))
                .thenReturn(1);
        when(orderMapper.updateStatus(eq(ORDER_ID), eq(Order.STATUS_PENDING_PAY),
                eq(Order.STATUS_PAID), any())).thenReturn(1);

        CallbackResult r = paymentService.handleCallback("MOCK", params);

        assertTrue(r.success());
        assertEquals("success", r.message());
        ArgumentCaptor<String> tradeNo = ArgumentCaptor.forClass(String.class);
        verify(paymentMapper).updateToPaid(eq(PAYMENT_NO), eq(Payment.STATUS_PAYING),
                tradeNo.capture(), anyString());
        assertTrue(tradeNo.getValue().startsWith("THIRD-"));
    }

    @Test
    @DisplayName("并发竞态（updateToPaid 返回 0 行）→ 幂等返回 success，不动订单")
    void callback_whenStateRaceLoses_shouldSkipGracefully() {
        Map<String, String> params = signedParams("399.00");
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO)).thenReturn(payingOrder());
        // 另一个线程已经把支付单改成 PAID → 本线程 CAS 未命中
        when(paymentMapper.updateToPaid(eq(PAYMENT_NO), eq(Payment.STATUS_PAYING), anyString(), anyString()))
                .thenReturn(0);

        CallbackResult r = paymentService.handleCallback("MOCK", params);

        assertTrue(r.success());
        verify(orderMapper, never()).updateStatus(any(), anyString(), anyString(), any());
    }
}
