package com.aimall.user.service;

import com.aimall.user.dto.LoginRequest;
import com.aimall.user.dto.LoginVO;
import com.aimall.user.dto.RegisterRequest;
import com.aimall.user.dto.UserVO;

/**
 * 用户服务：注册 / 登录 / 登出 / 当前用户
 */
public interface UserService {

    UserVO register(RegisterRequest req);

    LoginVO login(LoginRequest req);

    void logout();

    UserVO me();
}