package com.aimall.pay.mq;

/**
 * 抢购建单消息（MQ 传输载体，JSON 序列化）。
 *
 * <p>只带"谁在哪个活动买了多少"三个事实字段——消费端建单所需的其余信息
 * （价格、SKU、库存）全部回查 DB 拿最新值。消息是"请求的快照"不是"数据的快照"：
 * 消息在队列里躺的几秒内活动/库存都可能变化，回查才能保证建单用的是当下真实数据。</p>
 *
 * <p>不带 orderId/orderNo：订单此刻还不存在，正是这条消息要去创造它。</p>
 */
public record DropOrderMessage(Long activityId, Long userId, int quantity) {
}
