package com.aimall.user.dto;

import lombok.Data;

/**
 * 用户信息视图对象（不含敏感字段）
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer role;
}