package com.aimall.content.controller;

import com.aimall.ai.rag.RagIndexService;
import com.aimall.common.api.R;
import com.aimall.content.dto.NoteGenerateRequest;
import com.aimall.content.service.impl.NoteGenerateService;
import com.aimall.goods.mapper.ProductMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 内容创作接口（V2）。
 *
 * <pre>
 *   POST /api/v1/ai/note-draft    AI 生成种草笔记草稿（用户编辑后走正常发布+审核）
 *   POST /api/v1/ai/index-product 手动触发商品说明书入 RAG 索引（管理用）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiContentController {

    private final NoteGenerateService noteGenerateService;
    private final RagIndexService ragIndexService;
    private final com.aimall.content.service.impl.RagIndexBuildService indexBuildService;
    private final ProductMapper productMapper;

    @PostMapping("/note-draft")
    public R<Map<String, Object>> generateDraft(@Valid @RequestBody NoteGenerateRequest req) {
        return R.ok(noteGenerateService.generate(req));
    }

    /**
     * 商品说明书入索引。模拟数据导入后调用一次即可全量建索引；
     * 真实场景应由商品上架事件触发（V3 演进方向）。
     */
    @PostMapping("/index-product")
    public R<String> indexProduct(@RequestBody Map<String, Long> body) {
        Long productId = body.get("productId");
        if (productId == null) {
            return R.fail("productId 不能为空");
        }
        var product = productMapper.selectById(productId);
        if (product == null) {
            return R.fail("商品不存在");
        }
        ragIndexService.index(com.aimall.content.bean.KnowledgeDoc.SOURCE_MANUAL,
                productId, product.getSpuName(),
                product.getDetail() == null ? product.getSubTitle() : product.getDetail());
        return R.ok("已索引商品 " + productId);
    }

    /**
     * 批量建索引：把全量商品说明书 + 全部已发布笔记灌进 RAG 知识库。
     *
     * <p>用途：执行 mock_data.sql 之后调用一次，检索即可用。
     * 500 笔记 + 100 商品 ≈ 数千切片，本地哈希向量毫秒级完成；
     * 真实 embedding 时会慢（受 API 限速），走 @Async 不阻塞请求。</p>
     */
    @PostMapping("/index-all")
    public R<String> indexAll() {
        indexBuildService.buildAll();
        return R.ok("批量索引任务已提交（后台执行，查看日志确认进度）");
    }
}
