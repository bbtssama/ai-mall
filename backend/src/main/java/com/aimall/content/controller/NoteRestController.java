package com.aimall.content.controller;

import com.aimall.common.api.R;
import com.aimall.common.page.PageResult;
import com.aimall.content.dto.NoteCreateRequest;
import com.aimall.content.dto.NoteQuery;
import com.aimall.content.dto.NoteVO;
import com.aimall.content.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记接口（内容社区入口）。
 *
 * <pre>
 *   GET  /api/v1/notes                 Feed 流（游标分页/关键词/标签）
 *   GET  /api/v1/notes/{id}            详情（浏览+1）
 *   POST /api/v1/notes                 发布（秒回，异步审核）
 *   POST /api/v1/notes/{id}/like       点赞/取消（body: {"liked":true}）
 *   POST /api/v1/notes/{id}/collect    收藏/取消
 *   POST /api/v1/notes/{id}/resubmit   驳回后重新送审
 *   DELETE /api/v1/notes/{id}          作者下架（软删除）
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteRestController {

    private final NoteService noteService;

    @GetMapping
    public R<PageResult<NoteVO>> page(NoteQuery query) {
        return R.ok(noteService.page(query));
    }

    @GetMapping("/{id}")
    public R<NoteVO> detail(@PathVariable Long id) {
        return R.ok(noteService.detail(id));
    }

    @PostMapping
    public R<NoteVO> create(@Valid @RequestBody NoteCreateRequest req) {
        return R.ok(noteService.create(req));
    }

    /** 点赞态用 POST + body 表达（同一端点承担"点赞/取消"两个语义，前端传 liked 布尔） */
    @PostMapping("/{id}/like")
    public R<Void> like(@PathVariable Long id, @RequestBody LikeBody body) {
        noteService.like(id, Boolean.TRUE.equals(body.liked()));
        return R.ok();
    }

    @PostMapping("/{id}/collect")
    public R<Void> collect(@PathVariable Long id, @RequestBody LikeBody body) {
        noteService.collect(id, Boolean.TRUE.equals(body.collected()));
        return R.ok();
    }

    @PostMapping("/{id}/resubmit")
    public R<Void> resubmit(@PathVariable Long id) {
        noteService.resubmit(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> offline(@PathVariable Long id) {
        noteService.offline(id);
        return R.ok();
    }

    /** 点赞/收藏请求体 */
    public record LikeBody(Boolean liked, Boolean collected) {
    }
}
