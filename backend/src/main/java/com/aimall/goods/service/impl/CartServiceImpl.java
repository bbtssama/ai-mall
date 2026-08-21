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
            // 下架商品不可加购（宽松收集的边界：不可购买的东西不塞进购物车）
            throw new BusinessException(ResultCode.SKU_OFF_SHELF);
        }
        if (sku.getStock() == null || sku.getStock() <= 0) {
            // 售罄同样不可加购（与商品详情页"无货/已售罄"置灰一致）
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "商品已售罄");
        }

        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setSkuId(req.getSkuId());
        cart.setQuantity(req.getQuantity() == null ? 1 : req.getQuantity());

        // 原子合并 + 库存封顶：不存在则插入；存在则数据库内原子累加并按可售库存封顶。
        // 宽松收集：即使本次加购数量超过可售库存，也不拒绝，而是自动封顶到可售数（京东式）。
        cartMapper.upsert(cart, sku.getStock());

        // 定位并返回本条 join 商品/SKU 的展示 VO（user_id 条件天然校验归属；查不到兜底抛异常）
        Cart row = cartMapper.selectByUserIdAndSkuId(userId, req.getSkuId());
        return cartMapper.selectItemById(row.getId(), userId);
    }

    @Override
    public void update(Long id, UpdateCartRequest req) {
        Long userId = currentUserId();
        // 取该条当前 skuStock（join 出行内实时库存，带归属校验）
        CartItemVO item = cartMapper.selectItemById(id, userId);
        if (item == null) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND);
        }
        // 改数封顶到可售库存（京东/淘宝式：购物车内 "+" 到库存上限即点不动）
        int target = req.getQuantity();
        int cap = item.getMaxBuyable();
        if (target > cap) {
            target = cap;
        }
        if (cartMapper.updateQuantity(id, userId, target) == 0) {
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