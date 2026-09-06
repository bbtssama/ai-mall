package com.aimall.content.service.impl;

import com.aimall.ai.rag.RagIndexService;
import com.aimall.content.bean.KnowledgeDoc;
import com.aimall.content.bean.Note;
import com.aimall.content.mapper.NoteMapper;
import com.aimall.goods.bean.Product;
import com.aimall.goods.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 批量建索引任务。
 *
 * <p>场景：mock 数据导入后 / 存量数据迁移后，一次性把全量商品说明书 + 已发布笔记灌进知识库。</p>
 *
 * <p>@Async 走 AsyncConfig 的线程池（有界队列 + MDC 传递 + 优雅停机）；
 * 全量任务耗时随数据量增长（真实 embedding 时受 API 限速），绝不能阻塞 HTTP 线程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagIndexBuildService {

    private final RagIndexService ragIndexService;
    private final ProductMapper productMapper;
    private final NoteMapper noteMapper;

    /** 全量建索引：商品说明书 + 已发布笔记 */
    @Async
    public void buildAll() {
        log.info("批量索引开始：全量商品 + 已发布笔记");
        long start = System.currentTimeMillis();

        // ① 商品说明书（用 selectAllOnSale，V1 已有该查询）
        List<Product> products = productMapper.selectAllOnSale();
        int manualOk = 0;
        for (Product p : products) {
            try {
                String text = p.getDetail() != null ? p.getDetail() : p.getSubTitle();
                if (text != null && !text.isBlank()) {
                    ragIndexService.index(KnowledgeDoc.SOURCE_MANUAL, p.getId(), p.getSpuName(), text);
                    manualOk++;
                }
            } catch (Exception e) {
                // 单条失败不中断整体（索引可重跑，upsert 幂等）
                log.warn("商品索引失败 id={}: {}", p.getId(), e.getMessage());
            }
        }

        // ② 已发布笔记（分批拉取，避免一次加载 500 篇全文撑爆内存）
        int noteOk = 0;
        Long cursor = null;
        while (true) {
            List<Note> batch = fetchPublishedBatch(cursor);
            if (batch.isEmpty()) {
                break;
            }
            for (Note n : batch) {
                try {
                    ragIndexService.index(KnowledgeDoc.SOURCE_NOTE, n.getId(),
                            n.getTitle(), n.getTitle() + "\n" + n.getContent());
                    noteOk++;
                } catch (Exception e) {
                    log.warn("笔记索引失败 id={}: {}", n.getId(), e.getMessage());
                }
                cursor = n.getId();
            }
        }

        log.info("批量索引完成：说明书 {} 篇 / 笔记 {} 篇，耗时 {}ms",
                manualOk, noteOk, System.currentTimeMillis() - start);
    }

    /** 按 id 游标分批取已发布笔记（复用 NoteQuery 的 newest 排序路径） */
    private List<Note> fetchPublishedBatch(Long cursor) {
        com.aimall.content.dto.NoteQuery q = new com.aimall.content.dto.NoteQuery();
        q.setStatus(Note.STATUS_PUBLISHED);
        q.setOrderBy("newest");
        q.setCursorId(cursor);
        q.setPageSize(100);
        return noteMapper.selectPage(q);
    }
}
