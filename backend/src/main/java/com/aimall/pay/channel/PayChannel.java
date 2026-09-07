package com.aimall.pay.channel;

/**
 * 支付渠道抽象 —— 与 StorageService / EmbeddingClient 一脉相承的可插拔设计。
 *
 * <h2>★ 为什么必须抽象：一个绕不开的现实约束</h2>
 * 真实接入支付宝/微信沙箱需要<b>商户资质</b>：APPID、应用私钥、支付宝公钥（微信还要商户号+APIv3密钥）。
 * 个人学习项目拿不到这些凭证——如果代码硬依赖 SDK，那么没有凭证就<b>整套支付链路跑不起来</b>，
 * 只能"看代码理解"，无法验证。
 *
 * <p>抽象之后：</p>
 * <table>
 *   <tr><th>实现</th><th>何时用</th><th>能否跑通</th></tr>
 *   <tr><td>{@code MockPayChannel}</td><td>默认，零依赖</td><td>✅ 完整链路可跑（HMAC-SHA256 真签名）</td></tr>
 *   <tr><td>{@code AlipaySandboxChannel}</td><td>配了沙箱凭证</td><td>✅ 需真实凭证（业务代码零改动）</td></tr>
 * </table>
 *
 * <h2>接口为什么是这三个方法</h2>
 * 任何第三方支付都逃不出这三件事，抽象出来的接口才不会"为某家定制"：
 * <ol>
 *   <li><b>下单</b>（create）：本地支付单 → 换第三方支付参数/收银台地址</li>
 *   <li><b>验签</b>（verifyCallback）：确认回调确实来自对方、报文未被篡改</li>
 *   <li><b>查单</b>（query）：以我方为准主动核对，兜住"通知丢了"的情况</li>
 * </ol>
 */
public interface PayChannel {

    /** 渠道标识，与 t_payment.channel 对应：MOCK / ALIPAY / WECHAT */
    String code();

    /**
     * 发起支付：返回给前端的"收银台"信息。
     *
     * @param paymentNo 我方支付单号
     * @param amount    金额
     * @param subject   商品标题
     * @return 收银台信息（真实渠道是支付宝页面 URL，模拟渠道是本地模拟页）
     */
    CashierInfo create(String paymentNo, java.math.BigDecimal amount, String subject);

    /**
     * 校验回调报文签名。
     *
     * @param params 回调的完整参数（含 sign）
     * @return true 表示签名有效、报文未被篡改
     */
    boolean verifyCallback(java.util.Map<String, String> params);

    /**
     * 主动查单：用于"回调丢失"时的对账兜底。
     *
     * @return 第三方侧的交易状态；查询失败返回 UNKNOWN（不应据此改本地状态）
     */
    QueryResult query(String paymentNo);

    /** 收银台信息 */
    record CashierInfo(String payUrl, String channel, String raw) {
    }

    /** 查单结果 */
    record QueryResult(String status, String thirdTradeNo) {
        public static final String PAID = "PAID";
        public static final String NOT_PAY = "NOT_PAY";
        public static final String UNKNOWN = "UNKNOWN";
    }
}
