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

    Cart selectByUserIdAndSkuId(@Param("userId") Long userId, @Param("skuId") Long skuId);

    /** 返回受影响行数，主键回填 cart.id */
    int insert(Cart cart);

    /** 带 userId 条件，天然校验归属；返回 0 表示条目不存在/不属于该用户 */
    int updateQuantity(@Param("id") Long id, @Param("userId") Long userId, @Param("quantity") int quantity);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    int deleteByUserId(@Param("userId") Long userId);

    /** 下单成功后批量移除已购 SKU */
    int deleteByUserIdAndSkuIds(@Param("userId") Long userId, @Param("skuIds") List<Long> skuIds);
}