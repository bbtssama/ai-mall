package com.aimall.pay.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 限量发售活动（t_drop_activity）
 */
@Data
public class DropActivity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_ENDED = "ENDED";

    private Long id;
    private String title;
    private Long productId;
    private Long skuId;
    private BigDecimal dropPrice;
    private Integer dropStock;
    /** 每人限购数量 */
    private Integer perLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private LocalDateTime createdAt;
}
