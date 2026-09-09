package com.aimall.pay.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 抢购建单消费者 —— 削峰链路的消费端，薄壳：只做日志与委托。
 *
 * <p>异常分流全部在 {@link DropOrderExecutor#execute}：
 * BusinessException/DuplicateKey 在那里被"处理掉"（正常 ACK），
 * 未知异常从这里冒出 → 监听容器 basic.reject（default-requeue-rejected=false，
 * 不重入队防毒消息循环）→ 队列死信路由到 DLQ 等人工。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DropOrderConsumer {

    private final DropOrderExecutor dropOrderExecutor;

    @RabbitListener(queues = DropMqConfig.ORDER_QUEUE)
    public void onDropOrderMessage(DropOrderMessage msg) {
        log.info("收到抢购建单消息 activityId={} userId={} quantity={}",
                msg.activityId(), msg.userId(), msg.quantity());
        dropOrderExecutor.execute(msg);
    }
}
