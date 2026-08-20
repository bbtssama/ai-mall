package com.aimall.order.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.common.page.PageQuery;
import com.aimall.common.page.PageResult;
import com.aimall.goods.bean.Product;
import com.aimall.goods.bean.ProductSku;
import com.aimall.goods.mapper.CartMapper;
import com.aimall.goods.mapper.ProductMapper;
import com.aimall.goods.mapper.ProductSkuMapper;
import com.aimall.order.dto.CreateOrderRequest;
import com.aimall.order.dto.OrderItemRequest;
import com.aimall.order.dto.OrderVO;
import com.aimall.order.bean.Order;
import com.aimall.order.bean.OrderItem;
import com.aimall.order.mapper.OrderItemMapper;
import com.aimall.order.mapper.OrderMapper;
import com.aimall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final CartMapper cartMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(CreateOrderRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<OrderItemRequest> items = req.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ResultCode.CART_EMPTY, "订单条目不能为空");
        }

        // 1. 逐项校验 + CAS 扣库存（任一失败抛异常，@Transactional 整体回滚）
        //    说明：扣库存放在最前，可尽早暴露库存不足（快速失败）；但热点行锁仍持有至本方法事务提交，
        //    后续写订单/明细/清购物车都在锁覆盖范围内。要进一步缩短锁时长可把扣库存拆为独立短事务。
        List<OrderItem> orderItems = new ArrayList<>(items.size());
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest it : items) {
            ProductSku sku = skuMapper.selectById(it.getSkuId());
            if (sku == null) {
                throw new BusinessException(ResultCode.SKU_NOT_FOUND);
            }
            Product product = productMapper.selectById(sku.getProductId());
            if (product == null || product.getStatus() == null || product.getStatus() != 1) {
                throw new BusinessException(ResultCode.SKU_OFF_SHELF);
            }
            if (sku.getStock() < it.getQuantity()) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
                        "商品[" + product.getSpuName() + "]库存不足");
            }
            // 数据库层 CAS 扣减（WHERE stock >= ?）：InnoDB 对命中行加排他锁，返回 0 = 并发下库存不足
            if (skuMapper.deductStock(sku.getId(), it.getQuantity()) == 0) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
                        "商品[" + product.getSpuName() + "]库存不足");
            }
            OrderItem oi = new OrderItem();
            oi.setSkuId(sku.getId());
            oi.setProductName(product.getSpuName());
            oi.setSkuName(sku.getSkuName());
            oi.setPrice(sku.getPrice());
            oi.setQuantity(it.getQuantity());
            orderItems.add(oi);
            total = total.add(sku.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
        }

        // 2. 订单主表（默认待支付状态）
        Order order = new Order();
        order.setOrderNo(generateOrderNo(userId));
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setStatus(Order.STATUS_PENDING_PAY);
        order.setReceiverName(req.getReceiverName());
        order.setReceiverPhone(req.getReceiverPhone());
        order.setReceiverAddress(req.getReceiverAddress());
        orderMapper.insert(order);

        // 3. 订单明细（快照）
        orderItems.forEach(oi -> oi.setOrderId(order.getId()));
        orderItemMapper.batchInsert(orderItems);

        // 4. 清理购物车中本次下单的 SKU（直接购买场景下购物车本就没有，删除为幂等空操作）
        cartMapper.deleteByUserIdAndSkuIds(userId,
                items.stream().map(OrderItemRequest::getSkuId).toList());

        return buildVO(order.getId(), userId);
    }

    @Override
    public PageResult<OrderVO> pageMyOrders(PageQuery query) {
        Long userId = StpUtil.getLoginIdAsLong();
        long total = orderMapper.countByUserId(userId);
        List<OrderVO> records = orderMapper
                .selectByUserIdPage(userId, query.getOffset(), query.getPageSize())
                .stream()
                .map(o -> toVO(o, null))
                .toList();
        return PageResult.of(records, total, query.getPage(), query.getPageSize());
    }

    @Override
    public OrderVO detail(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return buildVO(id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        Order order = orderMapper.selectById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        // 状态机 CAS：仅 PENDING_PAY 可取消，防并发重复取消
        int rows = orderMapper.updateStatus(id, Order.STATUS_PENDING_PAY,
                Order.STATUS_CANCELLED, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "仅待支付订单可取消");
        }
        // 回补库存
        orderItemMapper.selectByOrderId(id)
                .forEach(oi -> skuMapper.addStock(oi.getSkuId(), oi.getQuantity()));
    }

    /** 组装详情 VO（校验订单归属） */
    private OrderVO buildVO(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || (userId != null && !order.getUserId().equals(userId))) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return toVO(order, orderItemMapper.selectByOrderId(orderId));
    }

    private OrderVO toVO(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setItems(items);
        return vo;
    }

    /** 订单号：时间戳(17位) + 4位随机 + 用户尾号，全局唯一由 uk_order_no 兜底 */
    private String generateOrderNo(Long userId) {
        return ORDER_NO_FMT.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(1000, 10000)
                + (userId % 1000);
    }
}