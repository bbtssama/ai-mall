package com.aimall.goods.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.goods.dto.CartItemVO;
import com.aimall.goods.bean.Cart;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车 Mapper（SQL 见 resources/mapper/CartMapper.xml）
 */
@Mapper
public interface CartMapper {

    /** 用户购物车条目（join sku/product），带商品名、主图 */
    List<CartItemVO> selectItemsByUserId(@Param("userId") Long userId);

    /** 按购物车主键 + 用户查单条条目（join sku/product），带 userId 条件天然校验归属 */
    CartItemVO selectItemById(@Param("id") Long id, @Param("userId") Long userId);

    Cart selectByUserIdAndSkuId(@Param("userId") Long userId, @Param("skuId") Long skuId);

    /**
     * 原子合并写入购物车条目（防并发丢更新 + 库存封顶）：
     * 不存在该 (user_id, sku_id) 则插入；存在则在该行当前数量上原子累加，
     * 并按 stockCap（实时可售库存）封顶，保证购物车数量不超可售库存。
     * 依赖 t_cart.uk_user_sku 唯一索引触发 ON DUPLICATE KEY 分支，单条 SQL、无读-改-写竞态窗口。
     */
    int upsert(@Param("cart") Cart cart, @Param("stockCap") int stockCap);

    /** 带 userId 条件，天然校验归属；返回 0 表示条目不存在/不属于该用户 */
    int updateQuantity(@Param("id") Long id, @Param("userId") Long userId, @Param("quantity") int quantity);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    int deleteByUserId(@Param("userId") Long userId);

    /** 下单成功后批量移除已购 SKU */
    int deleteByUserIdAndSkuIds(@Param("userId") Long userId, @Param("skuIds") List<Long> skuIds);
}