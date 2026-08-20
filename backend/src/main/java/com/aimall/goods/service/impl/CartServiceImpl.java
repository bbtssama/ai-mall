package com.aimall.goods.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.goods.dto.AddCartRequest;
import com.aimall.goods.dto.CartItemVO;
import com.aimall.goods.dto.UpdateCartRequest;
import com.aimall.goods.bean.Cart;
import com.aimall.goods.bean.Product;
import com.aimall.goods.bean.ProductSku;
import com.aimall.goods.mapper.CartMapper;
import com.aimall.goods.mapper.ProductMapper;
import com.aimall.goods.mapper.ProductSkuMapper;
import com.aimall.goods.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    @Override
    public List<CartItemVO> list() {
        return cartMapper.selectItemsByUserId(currentUserId());
    }

    @Override
    public CartItemVO add(AddCartRequest req) {
        Long userId = currentUserId();
        ProductSku sku = skuMapper.selectById(req.getSkuId());
        if (sku == null) {
            throw new BusinessException(ResultCode.SKU_NOT_FOUND);
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException(ResultCode.SKU_OFF_SHELF);
        }
        if (sku.getStock() < req.getQuantity()) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }

        Cart existing = cartMapper.selectByUserIdAndSkuId(userId, req.getSkuId());
        if (existing != null) {
            int newQuantity = Math.min(existing.getQuantity() + req.getQuantity(), 99);
            cartMapper.updateQuantity(existing.getId(), userId, newQuantity);
        } else {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setSkuId(req.getSkuId());
            cart.setQuantity(req.getQuantity());
            cartMapper.insert(cart);
        }
        // 返回最新条目
        return cartMapper.selectItemsByUserId(userId).stream()
                .filter(item -> item.getSkuId().equals(req.getSkuId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.CART_ITEM_NOT_FOUND));
    }

    @Override
    public void update(Long id, UpdateCartRequest req) {
        // deleteById 同理：update 带 userId 条件，行数 0 = 非本人条目
        if (cartMapper.updateQuantity(id, currentUserId(), req.getQuantity()) == 0) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND);
        }
    }

    @Override
    public void remove(Long id) {
        if (cartMapper.deleteById(id, currentUserId()) == 0) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND);
        }
    }

    @Override
    public void clear() {
        cartMapper.deleteByUserId(currentUserId());
    }
}