package com.aimall.goods.service;

import com.aimall.goods.dto.AddCartRequest;
import com.aimall.goods.dto.CartItemVO;
import com.aimall.goods.dto.UpdateCartRequest;

import java.util.List;

/**
 * 购物车服务（V1 MySQL 版；规划：V3 拟迁 Redis Hash）
 */
public interface CartService {

    List<CartItemVO> list();

    CartItemVO add(AddCartRequest req);

    void update(Long id, UpdateCartRequest req);

    void remove(Long id);

    void clear();
}