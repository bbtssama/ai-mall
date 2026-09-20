package com.aimall.common.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 「事务提交后执行」的唯一出口 —— 用于跨系统副作用（发消息、调用外部服务）。
 *
 * <h2>为什么需要它（本类解决的真实缺陷）</h2>
 * 数据库事务只覆盖数据库。向 MQ 发消息、调用外部服务这些动作<b>不参与事务</b>，
 * 于是产生两个方向相反的错误，本项目两个都踩过：
 *
 * <pre>
 *   ① 事务内直发 → 消费者比事务提交先跑
 *      · 内容审核：消费者回查不到刚插入的笔记 → 判定"笔记不存在"丢弃消息 → 笔记卡在审核中
 *        （真实缺陷，2026-09-20 复核，见 docs/复核-审核MQ竞态-20260920.md）
 *
 *   ② 事务内直发 + 事务回滚 → 消息已飞出，但数据没落库
 *      · 订单延迟取消：消费者 30 分钟后查到一个"幽灵订单"
 * </pre>
 *
 * 两种失败的共同解法只有一个：<b>把副作用推迟到 commit 之后</b>。
 * 本类把这个模式收敛成唯一出口，避免"每个发送点各写一遍、总有人忘"。
 *
 * <h2>三个必须遵守的约定</h2>
 * <ol>
 *   <li><b>异常必须在这里吞掉，不能往外抛。</b>
 *       commit 之后再抛异常，事务已经提交无法回滚，异常只会冒泡给调用方
 *       （用户看到"下单失败"，可数据其实成功了——比静默更糟）。
 *       所以本类统一 catch 并记 ERROR，由补偿任务兜底。</li>
 *   <li><b>无事务上下文时退化为立即执行并记 WARN</b>，而不是静默跳过。
 *       "宁可早发，不可不发"——早发的后果由消费端的 null 防御兜住，
 *       不发则会造成永久不一致（且没有任何日志指向问题所在）。</li>
 *   <li><b>传入的动作本身不要再往外抛异常</b>（内部尽量自行降级），
 *       因为这里已经是最外层防线。</li>
 * </ol>
 */
@Slf4j
public final class AfterCommitExecutor {

    private AfterCommitExecutor() {
    }

    /**
     * 在当前事务提交后执行 {@code action}；若当前没有事务，则立即执行。
     *
     * @param what   用于日志的动作描述（如 "审核消息 noteId=4"），出问题时能直接定位
     * @param action 要执行的副作用（发消息 / 调用外部服务）
     */
    public static void run(String what, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 没有事务：立即执行。记为 WARN 是因为这通常意味着调用链上游少了 @Transactional，
            // 属于需要留意的边界（但要能跑通，所以不抛异常）。
            log.warn("AfterCommitExecutor 在无事务上下文调用，退化为立即执行：{}", what);
            runQuietly(what, action);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runQuietly(what, action);
            }
        });
    }

    /**
     * 执行并吞掉一切异常 —— commit 之后抛异常无法回滚，只会给调用方制造假故障。
     * 失败记 ERROR（含描述），交由定时补偿任务收敛。
     */
    private static void runQuietly(String what, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            // 故意捕获 Throwable：连 Error（如 NoClassDefFoundError）也不能让它影响已提交的事务结果
            log.error("事务提交后动作执行失败（已提交的数据不受影响，待补偿任务收敛）：{}", what, t);
        }
    }
}
