package com.aimall.content.job;

import com.aimall.content.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 笔记计数增量落库任务 —— 「Redis 攒增量」的另一半。
 *
 * <p>互动计数在 Redis Hash 里攒增量（aimall:cnt:view/like/collect），
 * 本任务周期性取走并加法回写 DB。间隔是"写 DB 频率 ↔ 计数新鲜度"的
 * 调节旋钮：60 秒意味着热点行最多每分钟被写一次，而用户看到的计数
 * 仍是近实时（读路径 = DB 快照 + 未落库增量）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteCounterFlushTask {

    private final NoteService noteService;

    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void flush() {
        try {
            noteService.flushCounters();
        } catch (Exception e) {
            log.error("计数落库失败（下轮重试）: {}", e.getMessage(), e);
        }
    }
}
