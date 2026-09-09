package com.aimall.pay.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 抢购请求体。
 *
 * <p>★ P0 修复：早期 buy 接口用裸 Map 接参，quantity 没有任何校验——
 * 传负数会让 Lua 的 DECRBY 变成 INCRBY（库存反向增加）、DB 侧
 * {@code stock = stock - (-5)} 同样加库存、订单金额算出负数。
 * 越是高并发的入口，参数校验越要在最外层拦死（Bean Validation 一行顶十行防御）。</p>
 */
@Data
public class DropBuyRequest {

    @NotNull(message = "quantity 不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = 99, message = "单次最多 99 件")
    private Integer quantity;
}
