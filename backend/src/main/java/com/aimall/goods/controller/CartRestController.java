package com.aimall.goods.controller;

import com.aimall.common.api.R;
import com.aimall.goods.dto.AddCartRequest;
import com.aimall.goods.dto.CartItemVO;
import com.aimall.goods.dto.UpdateCartRequest;
import com.aimall.goods.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车接口
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartRestController {

    private final CartService cartService;

    @GetMapping
    public R<List<CartItemVO>> list() {
        return R.ok(cartService.list());
    }

    @PostMapping
    public R<CartItemVO> add(@RequestBody @Valid AddCartRequest req) {
        return R.ok(cartService.add(req));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid UpdateCartRequest req) {
        cartService.update(id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        cartService.remove(id);
        return R.ok();
    }

    @DeleteMapping
    public R<Void> clear() {
        cartService.clear();
        return R.ok();
    }
}