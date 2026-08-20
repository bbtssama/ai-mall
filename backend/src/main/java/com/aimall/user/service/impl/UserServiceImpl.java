package com.aimall.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.user.dto.LoginRequest;
import com.aimall.user.dto.LoginVO;
import com.aimall.user.dto.RegisterRequest;
import com.aimall.user.dto.UserVO;
import com.aimall.user.bean.User;
import com.aimall.user.mapper.UserMapper;
import com.aimall.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserVO register(RegisterRequest req) {
        // 1. 用户名唯一性校验（靠 t_user.uk_username 兜底，防并发重复注册）
        if (userMapper.selectByUsername(req.getUsername()) != null) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(StringUtils.hasText(req.getNickname())
                ? req.getNickname().trim()
                : "种草用户" + ThreadLocalRandom.current().nextInt(1000, 9999));
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);
        return toVO(user);
    }

    @Override
    public LoginVO login(LoginRequest req) {
        User user = userMapper.selectByUsername(req.getUsername());
        // 统一提示，避免暴露"用户名是否存在"
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        StpUtil.login(user.getId());
        return new LoginVO(StpUtil.getTokenValue(), toVO(user));
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserVO me() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        return vo;
    }
}