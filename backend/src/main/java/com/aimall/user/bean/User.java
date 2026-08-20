package com.aimall.user.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（t_user）
 */
@Data
public class User {

    /** 主键（用户 id，登录后 Sa-Token 的 loginId 就是它） */
    private Long id;

    /** 登录名（唯一，注册时校验） */
    private String username;

    /** BCrypt 密码哈希（存的是加密串，绝不是明文密码） */
    private String passwordHash;

    /** 昵称（展示用，注册时可留空自动生成） */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 身份：0普通用户 1商家 */
    private Integer role;

    /** 状态：1正常 0禁用（禁用后无法登录） */
    private Integer status;

    private LocalDateTime createdAt;
}