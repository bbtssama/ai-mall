package com.aimall.content.service;

import com.aimall.content.dto.NoteCreateRequest;
import com.aimall.content.dto.NoteQuery;
import com.aimall.content.dto.NoteVO;
import com.aimall.common.page.PageResult;

/**
 * 笔记服务：发布 / Feed / 详情 / 点赞 / 收藏 / 下架。
 */
public interface NoteService {

    /**
     * 发布笔记：落库为「审核中」并触发 AI 审核（异步，不阻塞返回）。
     *
     * @return 完整笔记 VO（含 id，前端可跳详情页）
     */
    NoteVO create(NoteCreateRequest req);

    /**
     * Feed 流 / 搜索 / 个人主页（按 NoteQuery 组合条件）。
     * 列表页只回显已发布（PUBLISHED）的笔记，未审核/驳回内容不外泄。
     */
    PageResult<NoteVO> page(NoteQuery query);

    /** 详情：浏览数 +1；非本人只能看已发布的（驳回原因只有作者可见） */
    NoteVO detail(Long id);

    /** 点赞 / 取消点赞（幂等：重复点赞静默成功） */
    void like(Long noteId, boolean liked);

    /** 收藏 / 取消收藏（幂等） */
    void collect(Long noteId, boolean collected);

    /** 作者下架自己的笔记（软删除） */
    void offline(Long noteId);

    /**
     * 重新送审：驳回后的笔记修改完可再次送审（状态 REJECTED → AUDITING）。
     * 会触发新一轮审核。
     */
    void resubmit(Long noteId);

    /**
     * 热门榜（V3）：Redis zset 倒序 Top-N。
     * Redis 不可用时返回空榜（榜单是展示优化，不决定可用性）。
     */
    java.util.List<NoteVO> hotRank(int limit);

    /**
     * 全量重建热门榜 zset（定时任务调用）：
     * Redis 数据丢失/漂移后，以 DB 为真值恢复（冷启动自愈）。
     */
    void rebuildHotRank();
}
