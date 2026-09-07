package com.aimall.pay.mapper;

import com.aimall.pay.bean.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 支付单 Mapper（SQL 见 resources/mapper/PaymentMapper.xml）
 */
@Mapper
public interface PaymentMapper {

    int insert(Payment payment);

    Payment selectByPaymentNo(@Param("paymentNo") String paymentNo);

    Payment selectByOrderId(@Param("orderId") Long orderId);

    /**
     * ★ 幂等核心：条件更新（状态机 CAS）。
     * 只有当前状态是 expectStatus 才更新——重复回调时第二次返回 0 行，
     * 天然防"重复加钱/重复发货"，无需先查后判（并发下先查后判会失效）。
     */
    int updateToPaid(@Param("paymentNo") String paymentNo,
                     @Param("expectStatus") String expectStatus,
                     @Param("thirdTradeNo") String thirdTradeNo,
                     @Param("callbackBody") String callbackBody);

    /** 关闭支付单（超时/订单取消）：同样带状态机条件 */
    int updateToClosed(@Param("paymentNo") String paymentNo,
                       @Param("expectStatus") String expectStatus);
}
