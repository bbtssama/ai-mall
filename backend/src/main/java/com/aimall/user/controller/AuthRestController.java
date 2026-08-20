package com.aimall.user.controller;

import com.aimall.common.api.R;
import com.aimall.user.dto.LoginRequest;
import com.aimall.user.dto.LoginVO;
import com.aimall.user.dto.RegisterRequest;
import com.aimall.user.dto.UserVO;
import com.aimall.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 登出 / 当前用户
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final UserService userService;

    @PostMapping("/register")
    public R<UserVO> register(@RequestBody @Valid RegisterRequest req) {
        return R.ok(userService.register(req));
    }

    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody @Valid LoginRequest req) {
        return R.ok(userService.login(req));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        userService.logout();
        return R.ok();
    }

    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(userService.me());
    }
}