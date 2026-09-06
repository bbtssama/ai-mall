package com.aimall.content.bean;

import lombok.Data;

/**
 * 笔记配图（t_note_image）
 *
 * <p>V1.5 起图片走 {@code StorageService} 上传，这里存返回的 URL——
 * 不再存本地裸路径（对比 V1 的 t_message.extra_json 直接塞 base64 的做法，
 * 那是为了省依赖的临时方案，笔记图片正式接入对象存储）。</p>
 */
@Data
public class NoteImage {

    private Long id;
    private Long noteId;
    private String url;
    private Integer sort;
}
