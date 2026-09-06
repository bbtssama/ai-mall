package com.aimall.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布笔记请求。
 *
 * <p>校验放在 DTO 注解上（{@code @Valid} 由 Controller 触发）：
 * 标题必填且限长、正文必填、图片最多 9 张（对齐主流社区的产品约束）。</p>
 */
@Data
public class NoteCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多 100 字")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 20000, message = "正文最多 20000 字")
    private String content;

    /** 图片 URL 列表（先用 V1.5 的 /api/v1/files/upload 上传，拿到 URL 再提交） */
    @Size(max = 9, message = "最多 9 张图片")
    private List<String> images;

    /** 标签，如 ["平价好物","通勤穿搭"] */
    @Size(max = 10, message = "最多 10 个标签")
    private List<String> tags;

    /** 关联商品（种草清单）：noteId 由服务端回填 */
    private List<RefProduct> products;

    @Data
    public static class RefProduct {
        @NotNull(message = "关联商品 id 不能为空")
        private Long productId;
        /** 推荐语，如"这个色号显白" */
        private String remark;
    }
}
