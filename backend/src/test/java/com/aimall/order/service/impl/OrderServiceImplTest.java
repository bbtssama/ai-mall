package com.aimall.order.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.goods.bean.Product;
import com.aimall.goods.bean.ProductSku;
import com.aimall.goods.mapper.CartMapper;
import com.aimall.goods.mapper.ProductMapper;
import com.aimall.goods.mapper.ProductSkuMapper;
import com.aimall.order.bean.Order;
import com.aimall.order.dto.CreateOrderRequest;
import com.aimall.order.dto.OrderItemRequest;
import com.aimall.order.mapper.OrderItemMapper;
import com.aimall.order.mapper.OrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单与订单状态机的单元测试 —— 覆盖本项目最容易出事故的几处逻辑。
 *
 * <p>为什么先测这里：下单涉及<b>钱和库存</b>，一旦出错就是资损，
 * 而这类逻辑用手工点页面很难覆盖（并发超卖、状态机并发流转都点不出来），只能靠单测。</p>
 *
 * <p>技巧：Sa-Token 的 {@code StpUtil.getLoginIdAsLong()} 是<b>静态方法</b>，
 * 单测里没有登录上下文，用 {@code Mockito.mockStatic} 把它"钉住"，
 * 就能在不启动 Spring 容器、不连数据库的前提下测业务逻辑（纯单元测试，毫秒级）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private ProductSkuMapper skuMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private CartMapper cartMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final long USER_ID = 1L;
    private static final long SKU_ID = 10L;
    private static final long PRODUCT_ID = 100L;
    private static final long ORDER_ID = 999L;

    // ------------------------------------------------------------------
    // 下单：库存
    // ------------------------------------------------------------------

    @Test
    @DisplayName("扣库存返回 0（并发下库存被抢完）→ 抛库存不足，且绝不落订单")
    void create_whenDeductStockReturnsZero_shouldThrowAndNeverInsertOrder() {
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);

            when(skuMapper.selectById(SKU_ID)).thenReturn(skuOf(1));
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(onSaleProduct());
            // 关键：数据库层 CAS 未命中（stock 已被别人扣掉）→ 返回 0
            when(skuMapper.deductStock(SKU_ID, 1)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.create(requestOf(1)));

            assertEquals(ResultCode.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
            // 绝不能出现"库存没扣成功但订单却生成了"的资损事故
            verify(orderMapper, never()).insert(any(Order.class));
            verify(orderItemMapper, never()).batchInsert(any());
        }
    }

    @Test
    @DisplayName("库存充足 → 正常落订单，金额按 单价×数量 累加，初始状态为待支付")
    void create_whenStockEnough_shouldInsertOrderWithCorrectAmount() {
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);

            when(skuMapper.selectById(SKU_ID)).thenReturn(skuOf(100));
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(onSaleProduct());
            when(skuMapper.deductStock(SKU_ID, 2)).thenReturn(1);

            // 模拟 MyBatis useGeneratedKeys：insert 后回填自增主键
            doAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(ORDER_ID);
                return 1;
            }).when(orderMapper).insert(any(Order.class));

            Order saved = new Order();
            saved.setId(ORDER_ID);
            saved.setUserId(USER_ID);
            when(orderMapper.selectById(ORDER_ID)).thenReturn(saved);

            orderService.create(requestOf(2));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderMapper).insert(captor.capture());
            Order inserted = captor.getValue();
            // 399.00 × 2 = 798.00
            assertEquals(0, new BigDecimal("798.00").compareTo(inserted.getTotalAmount()));
            assertEquals(Order.STATUS_PENDING_PAY, inserted.getStatus());
        }
    }

    @Test
    @DisplayName("商品已下架 → 抛 SKU_OFF_SHELF，不扣库存")
    void create_whenProductOffShelf_shouldThrowAndNeverDeductStock() {
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);

            when(skuMapper.selectById(SKU_ID)).thenReturn(skuOf(100));
            Product offShelf = onSaleProduct();
            offShelf.setStatus(0);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(offShelf);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.create(requestOf(1)));

            assertEquals(ResultCode.SKU_OFF_SHELF.getCode(), ex.getCode());
            verify(skuMapper, never()).deductStock(anyLong(), anyInt());
        }
    }

    // ------------------------------------------------------------------
    // 取消：状态机
    // ------------------------------------------------------------------

    @Test
    @DisplayName("取消已支付订单 → 状态机 CAS 未命中，抛状态非法且绝不回补库存")
    void cancel_whenNotPendingPay_shouldThrowAndNeverAddStock() {
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);

            Order paid = new Order();
            paid.setId(ORDER_ID);
            paid.setUserId(USER_ID);
            paid.setStatus(Order.STATUS_PAID);
            when(orderMapper.selectById(ORDER_ID)).thenReturn(paid);
            // UPDATE ... WHERE status='PENDING_PAY' 未命中 → 有并发方已改过状态
            when(orderMapper.updateStatus(anyLong(), any(), any(), any())).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.cancel(ORDER_ID));

            assertEquals(ResultCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
            // 防止"重复取消 → 重复回补库存"造成库存虚增
            verify(skuMapper, never()).addStock(anyLong(), anyInt());
        }
    }

    // ------------------------------------------------------------------
    // 安全：越权
    // ------------------------------------------------------------------

    @Test
    @DisplayName("查看他人订单 → 抛订单不存在（防越权，且不泄露订单存在与否）")
    void detail_whenOrderBelongsToAnotherUser_shouldThrowNotFound() {
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);

            Order someoneElse = new Order();
            someoneElse.setId(ORDER_ID);
            someoneElse.setUserId(888L);
            when(orderMapper.selectById(ORDER_ID)).thenReturn(someoneElse);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.detail(ORDER_ID));

            // 用 NOT_FOUND 而不是 FORBIDDEN：不向调用方泄露"这个订单 ID 是否存在"
            assertEquals(ResultCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
        }
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private ProductSku skuOf(int stock) {
        ProductSku sku = new ProductSku();
        sku.setId(SKU_ID);
        sku.setProductId(PRODUCT_ID);
        sku.setSkuName("曜石黑");
        sku.setPrice(new BigDecimal("399.00"));
        sku.setStock(stock);
        return sku;
    }

    private Product onSaleProduct() {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setSpuName("AirSound Pro 真无线降噪耳机");
        p.setStatus(1);
        return p;
    }

    private CreateOrderRequest requestOf(int quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setSkuId(SKU_ID);
        item.setQuantity(quantity);
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item));
        return req;
    }
}
