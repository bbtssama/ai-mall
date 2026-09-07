package com.aimall.pay.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.R;
import com.aimall.pay.bean.DropActivity;
import com.aimall.pay.service.DropService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 限量发售接口。
 *
 * <pre>
 *   GET  /api/v1/drops                 进行中的活动列表
 *   GET  /api/v1/drops/{id}            活动详情（顺带懒预热 Redis 库存）
 *   POST /api/v1/drops/{id}/buy        抢购 {quantity}
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropController {

    private final DropService dropService;

    @GetMapping
    public R<List<DropActivity>> ongoing() {
        return R.ok(dropService.listOngoing());
    }

    @GetMapping("/{id}")
    public R<DropActivity> detail(@PathVariable Long id) {
        DropActivity act = dropService.detail(id);
        if (act != null) {
            // 懒预热：第一次被访问时把库存灌进 Redis（正式场景由开售事件触发）
            dropService.warmUpIfAbsent(id);
        }
        return R.ok(act);
    }

    @PostMapping("/{id}/buy")
    public R<Long> buy(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        int quantity = body.getOrDefault("quantity", 1);
        return R.ok(dropService.buy(userId, id, quantity));
    }
}
