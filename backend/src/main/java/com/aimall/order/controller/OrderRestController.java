package com.aimall.order.controller;

import com.aimall.common.api.R;
import com.aimall.common.page.PageQuery;
import com.aimall.common.page.PageResult;
import com.aimall.order.dto.CreateOrderRequest;
import com.aimall.order.dto.OrderVO;
import com.aimall.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderRestController {

    private final OrderService orderService;

    @PostMapping
    public R<OrderVO> create(@RequestBody @Valid CreateOrderRequest req) {
        return R.ok(orderService.create(req));
    }

    @GetMapping
    public R<PageResult<OrderVO>> page(@Valid PageQuery query) {
        return R.ok(orderService.pageMyOrders(query));
    }

    @GetMapping("/{id}")
    public R<OrderVO> detail(@PathVariable Long id) {
        return R.ok(orderService.detail(id));
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return R.ok();
    }
}