package com.aimall.pay.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.R;
import com.aimall.pay.bean.DropActivity;
import com.aimall.pay.dto.DropBuyRequest;
import com.aimall.pay.dto.DropBuyVO;
import com.aimall.pay.dto.DropResultVO;
import com.aimall.pay.service.DropService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 限量发售接口。
 *
 * <pre>
 *   GET  /api/v1/drops                 进行中的活动列表
 *   GET  /api/v1/drops/{id}            活动详情（顺带懒预热 Redis 库存）
 *   POST /api/v1/drops/{id}/buy        提交抢购 {quantity} → 受理（QUEUED）
 *   GET  /api/v1/drops/{id}/result     抢购结果轮询（QUEUED/SUCCESS/FAILED/NONE）
 * </pre>
 *
 * <h2>★ buy 与 result 为什么拆开（异步削峰的接口形态）</h2>
 * buy 是"过闸门"（毫秒级返回受理），订单由 MQ 消费者异步创建——
 * 接口立即返回 orderId 是不可能的（订单还不存在）。前端拿到 QUEUED 后
 * 每 1~2 秒轮询 result，SUCCESS 时展示"抢到了"并跳转订单。
 * 这是秒杀/抢购类系统的标准接口形态。
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

    /**
     * 提交抢购。
     *
     * <p>★ quantity 必须过 Bean Validation（P0 修复）：裸 Map 接参时期传负数
     * 会反向刷库存。高并发入口的参数校验要拦在最外层。</p>
     */
    @PostMapping("/{id}/buy")
    public R<DropBuyVO> buy(@PathVariable Long id,
                            @RequestBody @jakarta.validation.Valid DropBuyRequest body) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(dropService.submitBuy(userId, id, body.getQuantity()));
    }

    /** 抢购结果轮询：前端在 buy 返回 QUEUED 后间隔调用，SUCCESS 携带 orderId */
    @GetMapping("/{id}/result")
    public R<DropResultVO> result(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(dropService.result(userId, id));
    }
}
