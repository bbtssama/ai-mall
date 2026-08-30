package com.aimall.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话消息视图对象（VO = View Object，给前端渲染用的出参）。
 *
 * <h2>VO 和实体(bean)为什么要分开？</h2>
 * 实体 ChatMessage 是"数据库行长什么样"（有 extraJson 这种存储导向的字段）；
 * VO 是"前端需要什么"（语义化的 image 字段）。两者通过 ChatServiceImpl.toMsgVO() 转换。
 * 分开的好处：存储结构变化（比如 V2 图片改成存 OSS URL）不影响前端契约，
 * 反过来前端要的字段也不会倒逼数据库表加列。
 *
 * <h2>image 字段从哪来？</h2>
 * DB 里存的是 t_message.extra_json（用户发图时的 base64），toMsgVO() 把它映射成
 * 名字更直白的 image。前端拿到后直接 &lt;img :src="m.image"&gt; 回显历史消息。
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 5 章（图片回显）、第 7 章（消息接口）。</p>
 */
@Data
public class MessageVO {

    /** 消息 id（前端列表渲染的 key） */
    private Long id;

    /** 角色：user（右侧气泡）/ assistant（左侧气泡）。前端据此决定气泡样式和对齐方向 */
    private String role;

    /** 消息正文。纯图片消息可能为空串，前端用 v-if 判断有无内容再渲染文本层 */
    private String content;

    /**
     * 消息附带的图片（base64 dataURL），来自实体的 extraJson 字段映射。
     * 仅用户发的带图消息有值；AI 回答恒为 null。
     */
    private String image;

    /** 发送时间。全局 Jackson 配置输出 yyyy-MM-dd HH:mm:ss（application.yml） */
    private LocalDateTime createdAt;
}
