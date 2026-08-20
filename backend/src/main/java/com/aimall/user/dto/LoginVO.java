package com.aimall.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录结果：Sa-Token 令牌 + 用户信息
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** Sa-Token 令牌，后续请求放入 Authorization 头 */
    private String token;

    private UserVO user;
}