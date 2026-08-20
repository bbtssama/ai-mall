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
        Long cartId;
        if (existing != null) {
            // 已存在该 SKU：累加数量（上限 99）并更新，复用已有主键
            int newQuantity = Math.min(existing.getQuantity() + req.getQuantity(), 99);
            cartMapper.updateQuantity(existing.getId(), userId, newQuantity);
            cartId = existing.getId();
        } else {
            // 不存在该 SKU：新增一条购物车记录，insert 后主键回填到 cart.id
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setSkuId(req.getSkuId());
            cart.setQuantity(req.getQuantity());
            cartMapper.insert(cart);
            cartId = cart.getId();
        }
        // 优化：不再查出全量购物车再内存过滤，而是按主键直接查本条展示 VO（join 商品/SKU 信息），
        // 同时 user_id 条件天然校验归属；查不到则抛异常兜底，避免返回 null。
        return cartMapper.selectItemById(cartId, userId);

        // 原实现（已弃用，保留以供对比；完整改动历史见 git）：查出全量购物车列表后，在内存中过滤出本次操作的 SKU 条目。
        // 缺点是 SKU 多时每次加购都要拉全量列表，且返回最新条目的语义依赖 ORDER BY updated_at DESC。
        // // 返回最新条目
        // return cartMapper.selectItemsByUserId(userId).stream()
        //         .filter(item -> item.getSkuId().equals(req.getSkuId()))
        //         .findFirst()
        //         .orElseThrow(() -> new BusinessException(ResultCode.CART_ITEM_NOT_FOUND));
    }

    @Override
    public void update(Long id, UpdateCartRequest req) {
        // update/remove 同理：均带 userId 条件，影响行数 0 = 非本人条目
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