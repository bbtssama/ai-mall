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

        // 原子合并：不存在该 SKU 则插入，存在则在数据库内 quantity+quantity 原子累加（封顶 99）。
        // 单条 INSERT ... ON DUPLICATE KEY UPDATE（依赖 uk_user_sku 唯一索引）消除了"读-改-写"竞态窗口，
        // 解决并发加购因"先读旧值、再写回叠算"而丢更新（lost update）的问题。
        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setSkuId(req.getSkuId());
        cart.setQuantity(req.getQuantity());
        cartMapper.upsert(cart);

        // 定位并返回本条 join 商品/SKU 的展示 VO（user_id 条件天然校验归属；查不到兜底抛异常）
        Cart row = cartMapper.selectByUserIdAndSkuId(userId, req.getSkuId());
        return cartMapper.selectItemById(row.getId(), userId);
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