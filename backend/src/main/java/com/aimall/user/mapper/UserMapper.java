package com.aimall.user.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.user.bean.User;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper（SQL 见 resources/mapper/UserMapper.xml）
 */
@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    User selectByUsername(@Param("username") String username);

    /** 返回受影响行数，主键回填到 user.id */
    int insert(User user);
}