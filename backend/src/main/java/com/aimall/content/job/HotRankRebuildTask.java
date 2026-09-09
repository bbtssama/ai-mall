package com.aimall.content.job;

import com.aimall.content.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热门榜全量重建任务 —— zset 的"冷启动自愈 + 周期性对账"。
 *
 * <p>Redis 是内存库：重启/淘汰后数据全失。热门榜若只靠"每次互动时 zAdd"
 * 增量恢复，一次重启后榜单会空置很久（越冷门恢复越慢）。
 * 周期性以 DB 为真值全量重建，才是"缓存数据丢了怎么办"的正解：
 * <b>缓存永远可以由源数据重建</b>——这也是"DB 才是真值，Redis 只是加速"的再次体现。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotRankRebuildTask {

    private final NoteService noteService;

    /** 每 30 分钟重建一次（覆盖毫秒级空窗在 hotRank 的注释里说明） */
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 3 * 60 * 1000)
    public void rebuild() {
        try {
            noteService.rebuildHotRank();
        } catch (Exception e) {
            log.error("热门榜重建失败（下轮重试）: {}", e.getMessage(), e);
        }
    }
}
