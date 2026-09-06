package com.aimall.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * AI 种草文案生成请求。
 *
 * <p>业务场景：用户买了个东西想发种草笔记，但不知道怎么写。
 * 给关键词或关联商品 → AI 生成标题+正文+标签草稿 → 用户编辑后发布。</p>
 *
 * <p>★ 设计红线：<b>AI 只生成草稿，绝不自动发布</b>（设计文档 5.6 的"AI 不直写业务数据"）。
 * 生成的内容未经审核直接入库 = UGC 平台事故。</p>
 */
@Data
public class NoteGenerateRequest {

    /** 关联商品 id（有则带上商品卖点，生成更贴切） */
    private Long productId;

    /** 用户的素材/想法，如"通勤用，预算500，想要降噪" */
    @NotBlank(message = "请描述一下你的素材或想法")
    private String brief;

    /** 期望语气：默认 真诚分享 */
    private String tone = "真诚分享";
}
