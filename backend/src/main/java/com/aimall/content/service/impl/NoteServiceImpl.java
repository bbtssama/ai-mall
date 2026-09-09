package com.aimall.content.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.common.page.PageResult;
import com.aimall.content.bean.Note;
import com.aimall.content.bean.NoteImage;
import com.aimall.content.bean.NoteProduct;
import com.aimall.content.bean.NoteTag;
import com.aimall.content.dto.NoteCreateRequest;
import com.aimall.content.dto.NoteQuery;
import com.aimall.content.dto.NoteVO;
import com.aimall.content.mapper.NoteCollectMapper;
import com.aimall.content.mapper.NoteExtraMapper;
import com.aimall.content.mapper.NoteLikeMapper;
import com.aimall.content.mapper.NoteMapper;
import com.aimall.content.service.AuditService;
import com.aimall.content.service.NoteCounterStore;
import com.aimall.content.service.NoteService;
import com.aimall.goods.bean.Product;
import com.aimall.goods.mapper.ProductMapper;
import com.aimall.user.bean.User;
import com.aimall.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 笔记服务实现 —— Feed 流 / 点赞收藏 / 发布。
 *
 * <h2>一条贯穿始终的线索：N+1 查询</h2>
 * 列表 20 篇笔记 × (作者 1 次 + 图片 1 次 + 标签 1 次 + 关联商品 1 次 + 点赞态 1 次) = 100+ 条 SQL。
 * 本类所有列表组装一律"批量 IN 查询 + 内存分组"——20 篇笔记固定 5 条 SQL，与页大小无关。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;
    private final NoteExtraMapper extraMapper;
    private final NoteLikeMapper likeMapper;
    private final NoteCollectMapper collectMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final AuditService auditService;
    /** V3：Redis（热门榜 zset 等，可降级） */
    private final com.aimall.common.redis.RedisOps redisOps;
    /** V3：计数桶（浏览/点赞/收藏的 Redis 攒增量 + 定时落库） */
    private final com.aimall.content.service.NoteCounterStore counterStore;

    /** 热门榜的 zset key（与词典/文档中的命名一致） */
    private static final String HOT_RANK_KEY = "aimall:hot:notes";
    /** 榜单全量重建的候选规模：全站笔记量级远小于此，够覆盖 */
    private static final int HOT_RANK_REBUILD_SIZE = 1000;
    /** RAG 索引：下架笔记时移除语料（content → ai.rag 同应用依赖，V4 拆分后改 Feign） */
    private final com.aimall.ai.rag.RagIndexService ragIndexService;

    // ------------------------------------------------------------------
    // 发布
    // ------------------------------------------------------------------

    /**
     * 发布：落库 AUDITING + 触发异步审核，<b>立即返回</b>（不等 AI）。
     *
     * <p>图片/标签/关联商品与主表同事务写入：要么全成功要么全回滚，
     * 避免"主表有了但图片丢了"的脏数据。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteVO create(NoteCreateRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();

        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(req.getTitle().trim());
        note.setContent(req.getContent().trim());
        note.setCover(firstImage(req.getImages()));
        note.setStatus(Note.STATUS_AUDITING);   // 发布即审核中，秒回
        noteMapper.insert(note);

        saveExtras(note.getId(), req);

        // 事务提交后再发审核消息：避免"消费者先于事务提交读到空笔记"的竞态。
        // @Transactional 方法内直接发消息的坑：MQ 消费者立刻回查，事务还没 commit → 查不到。
        // 这里用最简单的做法：审核提交放在事务边界外（Controller 层事务已提交）。
        auditService.submit(note);

        return detail(note.getId());
    }

    // ------------------------------------------------------------------
    // Feed / 详情
    // ------------------------------------------------------------------

    @Override
    public PageResult<NoteVO> page(NoteQuery query) {
        // Feed 默认只展示已发布内容；个人主页（带 userId）在 Controller 决定是否放开状态
        if (!StringUtils.hasText(query.getStatus())) {
            query.setStatus(Note.STATUS_PUBLISHED);
        }
        List<Note> notes = noteMapper.selectPage(query);
        if (notes.isEmpty()) {
            return PageResult.of(List.of(), 0, query.getPage(), query.getPageSize());
        }
        List<NoteVO> vos = assembleList(notes);
        // 游标分页的"下一页"判断：本次拿满 pageSize 条即视为还有下一页（简单可靠）
        boolean hasMore = notes.size() >= query.getPageSize();
        return PageResult.of(hasMore ? vos : vos, notes.size(), query.getPage(), query.getPageSize());
    }

    @Override
    public NoteVO detail(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        boolean mine = note.getUserId().equals(userId);
        // 未发布内容只有作者本人可见（防审核中/驳回内容外泄）
        if (!Note.STATUS_PUBLISHED.equals(note.getStatus()) && !mine) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        // 浏览计数（V3 改造）：Redis HINCRBY 攒增量（内存，微秒级），定时落库。
        // 此前每次浏览 = incrViewCount + refreshHotScore（1 SELECT + 3 UPDATE）——
        // 热点笔记行锁被高频写打爆（写放大）。现在热度刷新也由落库任务统一做。
        if (!mine) {
            incrCount(NoteCounterStore.VIEW, id, 1);
        }
        return assembleDetail(note, mine);
    }

    // ------------------------------------------------------------------
    // 点赞 / 收藏（幂等）
    // ------------------------------------------------------------------

    /**
     * 点赞/取消：关系表唯一索引兜底幂等，计数走 Redis 增量桶。
     *
     * <p>并发场景：用户狂点红心 → 前端防抖失灵 → 两个"点赞"请求同时到达。
     * "先查后插"会插两条；这里直接 INSERT，撞 uk_note_user 抛
     * DuplicateKeyException → 捕获当作"已点过"静默成功。计数改为 Redis
     * HINCRBY 攒增量（incrCount），定时批量落库——互动不再直写计数行。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Long noteId, boolean liked) {
        Long userId = StpUtil.getLoginIdAsLong();
        ensurePublished(noteId);
        if (liked) {
            try {
                likeMapper.insert(noteId, userId);
                incrCount(NoteCounterStore.LIKE, noteId, 1);
            } catch (DuplicateKeyException e) {
                // 已点过赞：幂等静默（不报错，前端体验为"红心已是亮的"）
                log.debug("重复点赞已忽略 noteId={} userId={}", noteId, userId);
            }
        } else {
            int rows = likeMapper.delete(noteId, userId);
            if (rows > 0) {
                incrCount(NoteCounterStore.LIKE, noteId, -1);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void collect(Long noteId, boolean collected) {
        Long userId = StpUtil.getLoginIdAsLong();
        ensurePublished(noteId);
        if (collected) {
            try {
                collectMapper.insert(noteId, userId);
                incrCount(NoteCounterStore.COLLECT, noteId, 1);
            } catch (DuplicateKeyException e) {
                log.debug("重复收藏已忽略 noteId={} userId={}", noteId, userId);
            }
        } else {
            int rows = collectMapper.delete(noteId, userId);
            if (rows > 0) {
                incrCount(NoteCounterStore.COLLECT, noteId, -1);
            }
        }
    }

    // ------------------------------------------------------------------
    // 下架 / 重提
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long noteId) {
        Long userId = StpUtil.getLoginIdAsLong();
        int rows = noteMapper.offline(noteId, userId);
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在或无权操作");
        }
        // 下架内容必须从 RAG 语料中移除：不能让检索召回"已下架"的内容。
        // 跨包注入说明：content → ai.rag 属同应用内正常依赖；V4 拆 ai-service 后改 Feign。
        try {
            ragIndexService.remove(com.aimall.content.bean.KnowledgeDoc.SOURCE_NOTE, noteId);
        } catch (Exception e) {
            log.warn("下架笔记移除 RAG 索引失败（可补偿）noteId={}: {}", noteId, e.getMessage());
        }
    }

    @Override
    public void resubmit(Long noteId) {
        Long userId = StpUtil.getLoginIdAsLong();
        Note note = noteMapper.selectById(noteId);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        if (!Note.STATUS_REJECTED.equals(note.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅被驳回的笔记可重新送审");
        }
        noteMapper.updateStatus(noteId, Note.STATUS_REJECTED, Note.STATUS_AUDITING, null);
        auditService.submit(note);
    }

    // ------------------------------------------------------------------
    // 组装（反 N+1）
    // ------------------------------------------------------------------

    /** 列表组装：5 条批量 SQL 拿全所有附属信息，内存分组拼装 */
    private List<NoteVO> assembleList(List<Note> notes) {
        Long me = currentUserIdOrNull();
        List<Long> ids = notes.stream().map(Note::getId).toList();
        Set<Long> noteIdSet = new HashSet<>(ids);

        // ① 作者信息（批量）
        Map<Long, User> users = new HashMap<>();
        notes.forEach(n -> users.computeIfAbsent(n.getUserId(), userMapper::selectById));

        // ② 封面（批量：每篇取 sort 最小一张）
        Map<Long, String> covers = new HashMap<>();
        extraMapper.selectCoversByNotes(ids)
                .forEach(img -> covers.putIfAbsent(img.getNoteId(), img.getUrl()));

        // ③ 标签（批量）
        Map<Long, List<String>> tags = new HashMap<>();
        extraMapper.selectTagsByNotes(ids)
                .forEach(t -> tags.computeIfAbsent(t.getNoteId(), k -> new java.util.ArrayList<>())
                        .add(t.getTag()));

        // ④ 关联商品（批量）
        Map<Long, List<NoteVO.ProductRefVO>> products = new HashMap<>();
        for (NoteProduct np : extraMapper.selectProductsByNotes(ids)) {
            Product p = productMapper.selectById(np.getProductId());
            if (p == null) {
                continue;
            }
            NoteVO.ProductRefVO vo = new NoteVO.ProductRefVO();
            vo.setProductId(p.getId());
            vo.setProductName(p.getSpuName());
            vo.setMainImg(p.getMainImg());
            vo.setRemark(np.getRemark());
            products.computeIfAbsent(np.getNoteId(), k -> new java.util.ArrayList<>()).add(vo);
        }

        // ⑤ 我的互动态（批量：点赞/收藏）
        Set<Long> liked = me == null ? Set.of()
                : new HashSet<>(likeMapper.listLikedNoteIds(me, ids));
        Set<Long> collected = me == null ? Set.of()
                : new HashSet<>(collectMapper.listCollectedNoteIds(me, ids));

        // ⑥ 计数增量（批量 HMGET ×3：DB 快照 + Redis 未落库增量 = 近实时值）
        Map<Long, Long> likeD = counterStore.readDeltas(NoteCounterStore.LIKE, ids);
        Map<Long, Long> collectD = counterStore.readDeltas(NoteCounterStore.COLLECT, ids);
        Map<Long, Long> viewD = counterStore.readDeltas(NoteCounterStore.VIEW, ids);

        return notes.stream().map(n -> {
            NoteVO vo = new NoteVO();
            vo.setId(n.getId());
            vo.setUserId(n.getUserId());
            User u = users.get(n.getUserId());
            vo.setAuthorName(u != null ? u.getNickname() : "用户" + n.getUserId());
            vo.setAuthorAvatar(u != null ? u.getAvatar() : null);
            vo.setTitle(n.getTitle());
            vo.setCover(n.getCover() != null ? n.getCover() : covers.get(n.getId()));
            vo.setSummary(summarize(n.getContent()));
            vo.setStatus(n.getStatus());
            vo.setLikeCount(applyDelta(n.getLikeCount(), likeD.get(n.getId())));
            vo.setCollectCount(applyDelta(n.getCollectCount(), collectD.get(n.getId())));
            vo.setViewCount(applyDelta(n.getViewCount(), viewD.get(n.getId())));
            vo.setHotScore(n.getHotScore());
            vo.setLiked(liked.contains(n.getId()));
            vo.setCollected(collected.contains(n.getId()));
            vo.setMine(me != null && me.equals(n.getUserId()));
            vo.setTags(tags.getOrDefault(n.getId(), List.of()));
            vo.setProducts(products.getOrDefault(n.getId(), List.of()));
            vo.setCreatedAt(n.getCreatedAt());
            return vo;
        }).toList();
    }

    /** 详情组装：全文 + 全部图片（列表只给封面，详情给图集） */
    private NoteVO assembleDetail(Note note, boolean mine) {
        NoteVO vo = new NoteVO();
        vo.setId(note.getId());
        vo.setUserId(note.getUserId());
        User u = userMapper.selectById(note.getUserId());
        vo.setAuthorName(u != null ? u.getNickname() : "用户" + note.getUserId());
        vo.setAuthorAvatar(u != null ? u.getAvatar() : null);
        vo.setTitle(note.getTitle());
        vo.setCover(note.getCover());
        vo.setContent(note.getContent());
        vo.setStatus(note.getStatus());
        // 驳回原因只给作者本人看
        if (mine) {
            vo.setAuditResult(note.getAuditResult());
        }
        // 计数 = DB 快照 + Redis 未落库增量（与列表口径一致）
        Map<Long, Long> likeD = counterStore.readDeltas(NoteCounterStore.LIKE, List.of(note.getId()));
        Map<Long, Long> collectD = counterStore.readDeltas(NoteCounterStore.COLLECT, List.of(note.getId()));
        Map<Long, Long> viewD = counterStore.readDeltas(NoteCounterStore.VIEW, List.of(note.getId()));
        vo.setLikeCount(applyDelta(note.getLikeCount(), likeD.get(note.getId())));
        vo.setCollectCount(applyDelta(note.getCollectCount(), collectD.get(note.getId())));
        vo.setViewCount(applyDelta(note.getViewCount(), viewD.get(note.getId())));
        vo.setHotScore(note.getHotScore());
        Long me = currentUserIdOrNull();
        vo.setLiked(me != null && !likeMapper.listLikedNoteIds(me, List.of(note.getId())).isEmpty());
        vo.setCollected(me != null && !collectMapper.listCollectedNoteIds(me, List.of(note.getId())).isEmpty());
        vo.setMine(mine);
        vo.setImages(extraMapper.selectImages(note.getId())
                .stream().map(NoteImage::getUrl).toList());
        vo.setTags(extraMapper.selectTags(note.getId())
                .stream().map(NoteTag::getTag).toList());
        vo.setProducts(extraMapper.selectProducts(note.getId()).stream().map(np -> {
            NoteVO.ProductRefVO pvo = new NoteVO.ProductRefVO();
            pvo.setProductId(np.getProductId());
            pvo.setRemark(np.getRemark());
            Product p = productMapper.selectById(np.getProductId());
            if (p != null) {
                pvo.setProductName(p.getSpuName());
                pvo.setMainImg(p.getMainImg());
            }
            return pvo;
        }).toList());
        vo.setCreatedAt(note.getCreatedAt());
        return vo;
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void saveExtras(Long noteId, NoteCreateRequest req) {
        if (req.getImages() != null && !req.getImages().isEmpty()) {
            List<NoteImage> images = new java.util.ArrayList<>();
            for (int i = 0; i < req.getImages().size(); i++) {
                NoteImage img = new NoteImage();
                img.setUrl(req.getImages().get(i));
                img.setSort(i);
                images.add(img);
            }
            extraMapper.batchInsertImages(noteId, images);
        }
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            List<NoteTag> tags = req.getTags().stream().map(t -> {
                NoteTag nt = new NoteTag();
                nt.setTag(t.trim().replace("#", ""));
                return nt;
            }).toList();
            extraMapper.batchInsertTags(noteId, tags);
        }
        if (req.getProducts() != null && !req.getProducts().isEmpty()) {
            List<NoteProduct> products = req.getProducts().stream().map(rp -> {
                NoteProduct np = new NoteProduct();
                np.setProductId(rp.getProductId());
                np.setRemark(rp.getRemark());
                return np;
            }).toList();
            extraMapper.batchInsertProducts(noteId, products);
        }
    }

    /** 热度公式：点赞*3 + 收藏*5 + 浏览*1（权重：收藏是最强的兴趣信号）。
     *  V3：算完后同步写 Redis zset（热门榜）——zAdd 全量覆盖与 DB 一致；
     *  Redis 不可用时静默降级（榜单纯粹是展示优化，不决定可用性）。 */
    private void refreshHotScore(Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) {
            return;
        }
        int score = hotScoreOf(n);
        noteMapper.updateHotScore(noteId, score);
        redisOps.zAdd(HOT_RANK_KEY, String.valueOf(noteId), score);
    }

    /** 热门榜（V3）：Redis zset 倒序 Top-N，附当前热度分。
     *  ★ P2 修复：早期实现榜单循环逐条 selectById（Top50 = 50 次 SQL）——
     *  与列表接口辛苦做的"5 条批量 SQL"自相矛盾。现在 zset 只出 id 顺序，
     *  明细一次 IN 查询批量拿回（反 N+1 的又一实例）。 */
    @Override
    public List<NoteVO> hotRank(int limit) {
        List<com.aimall.common.redis.RedisOps.RankItem> rank =
                redisOps.zTopWithScore(HOT_RANK_KEY, limit);
        if (rank.isEmpty()) {
            return List.of();
        }
        // ① 收集榜单 id（保持 zset 顺序）
        List<Long> ids = new java.util.ArrayList<>(rank.size());
        for (var item : rank) {
            try {
                ids.add(Long.parseLong(item.member()));
            } catch (NumberFormatException ignored) {
                // 榜内混入脏数据（理论不可达）：跳过而不是让整个榜单接口失败
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        // ② 一次 IN 查询批量拿明细（而不是 N 次 selectById）
        Map<Long, Note> notes = new HashMap<>();
        for (Note n : noteMapper.selectByIds(ids)) {
            notes.put(n.getId(), n);
        }
        // ②' 计数增量（口径与列表一致：DB 快照 + 未落库增量）
        Map<Long, Long> likeD = counterStore.readDeltas(NoteCounterStore.LIKE, ids);
        Map<Long, Long> collectD = counterStore.readDeltas(NoteCounterStore.COLLECT, ids);
        Map<Long, Long> viewD = counterStore.readDeltas(NoteCounterStore.VIEW, ids);
        // ③ 按 zset 顺序组装，仅保留已发布的（下架/驳回的从榜上自然消失）
        List<NoteVO> result = new java.util.ArrayList<>(ids.size());
        for (var item : rank) {
            Long noteId = null;
            try {
                noteId = Long.parseLong(item.member());
            } catch (NumberFormatException ignored) {
            }
            Note n = noteId == null ? null : notes.get(noteId);
            if (n != null && Note.STATUS_PUBLISHED.equals(n.getStatus())) {
                NoteVO vo = new NoteVO();
                vo.setId(n.getId());
                vo.setTitle(n.getTitle());
                vo.setCover(n.getCover());
                vo.setLikeCount(applyDelta(n.getLikeCount(), likeD.get(n.getId())));
                vo.setCollectCount(applyDelta(n.getCollectCount(), collectD.get(n.getId())));
                vo.setViewCount(applyDelta(n.getViewCount(), viewD.get(n.getId())));
                vo.setHotScore((int) item.score());
                vo.setCreatedAt(n.getCreatedAt());
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 全量重建热门榜 zset（定时任务调用，见 HotRankRebuildTask）。
     *
     * <p>★ 解决两个问题：</p>
     * <ul>
     *   <li><b>冷启动</b>：Redis 重启数据全失，原实现只靠"逐条互动"慢慢恢复，
     *       榜单会越用越空。重建以 DB 热度分为准，一次灌满；</li>
     *   <li><b>漂移</b>：refreshHotScore 只在互动时写 zset，DB 与 zset 长期
     *       并行更新难免不一致（删 key 竞态/降级窗口），周期性对账收敛。</li>
     * </ul>
     * <p>语义：先删后全量灌入（覆盖式重建）。删除与灌入之间有毫秒级空窗，
     * 榜单接口短暂返回空——榜单是展示优化，可接受（若不可接受，
     * 换"写影子 key + RENAME 原子切换"）。</p>
     */
    @Override
    public void rebuildHotRank() {
        List<Note> top = noteMapper.selectPublishedForRank(HOT_RANK_REBUILD_SIZE);
        if (top.isEmpty()) {
            return;
        }
        redisOps.delete(HOT_RANK_KEY);
        for (Note n : top) {
            redisOps.zAdd(HOT_RANK_KEY, String.valueOf(n.getId()), hotScoreOf(n));
        }
        log.info("热门榜已全量重建，成员数={}", top.size());
    }

    /** 热度分 = 点赞*3 + 收藏*5 + 浏览*1（refreshHotScore/rebuildHotRank 共用同一公式） */
    private int hotScoreOf(Note n) {
        return n.getLikeCount() * 3 + n.getCollectCount() * 5 + n.getViewCount();
    }

    private void ensurePublished(Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null || !Note.STATUS_PUBLISHED.equals(n.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
    }

    // ------------------------------------------------------------------
    // 计数：Redis 攒增量 + 定时落库
    // ------------------------------------------------------------------

    /**
     * 计数变更的唯一入口：Redis 增量桶优先，Redis 不可用降级直写 DB。
     *
     * <p>降级映射回原有的 ±1 方法（incrViewCount/decrLikeCount 等）——
     * Redis 宕机时互动量直写 DB，体验回到 V2（可接受：计数是展示数据）。</p>
     */
    private void incrCount(String type, Long noteId, long delta) {
        Long applied = counterStore.incr(type, noteId, delta);
        if (applied != null) {
            return;
        }
        // Redis 不可用：降级直写 DB（delta 只会是 ±1）
        if (NoteCounterStore.VIEW.equals(type)) {
            noteMapper.incrViewCount(noteId);
        } else if (NoteCounterStore.LIKE.equals(type)) {
            if (delta >= 0) {
                noteMapper.incrLikeCount(noteId);
            } else {
                noteMapper.decrLikeCount(noteId);
            }
        } else if (NoteCounterStore.COLLECT.equals(type)) {
            if (delta >= 0) {
                noteMapper.incrCollectCount(noteId);
            } else {
                noteMapper.decrCollectCount(noteId);
            }
        }
    }

    /** DB 计数 + Redis 增量（读路径拼"快照+增量"）；null 增量按 0 */
    private Integer applyDelta(Integer base, Long delta) {
        int b = base == null ? 0 : base;
        return b + (delta == null ? 0 : delta.intValue());
    }

    /**
     * 落库（定时任务调用，见 NoteCounterFlushTask）：
     * 原子取走三类增量 → 加法回写 DB → 重算受影响笔记的热度分（含 zset）。
     *
     * <p>★ 热度刷新收敛在这里的意义：浏览/点赞不再各自触发热度重算
     * （原先一次浏览 = 3 次 DB 写），热度的更新频率 = 落库频率，
     * 与流量解耦——这就是"写放大治理"的完整闭环。</p>
     */
    @Override
    public void flushCounters() {
        java.util.Set<Long> touched = new java.util.HashSet<>();
        counterStore.takeAndClear(NoteCounterStore.VIEW).forEach((id, delta) -> {
            noteMapper.applyViewDelta(id, delta);
            touched.add(id);
        });
        counterStore.takeAndClear(NoteCounterStore.LIKE).forEach((id, delta) -> {
            noteMapper.applyLikeDelta(id, delta);
            touched.add(id);
        });
        counterStore.takeAndClear(NoteCounterStore.COLLECT).forEach((id, delta) -> {
            noteMapper.applyCollectDelta(id, delta);
            touched.add(id);
        });
        if (touched.isEmpty()) {
            return;
        }
        // 回写完成后 DB 是最新值：此刻重算热度才是准的
        touched.forEach(this::refreshHotScore);
        log.debug("计数增量已落库，触发热度重算的笔记数={}", touched.size());
    }

    private String summarize(String content) {
        if (content == null) {
            return "";
        }
        String oneLine = content.replace("\n", " ").trim();
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 120) + "…";
    }

    private String firstImage(List<String> images) {
        return images == null || images.isEmpty() ? null : images.get(0);
    }

    private Long currentUserIdOrNull() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}
