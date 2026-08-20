package com.aimall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 种草商城 —— V1 MVP 单体应用
 *
 * <p>启动类。按业务域分包（user/content/goods/order/ai），为 V4 拆微服务预留边界。
 * Mapper 采用逐接口 @Mapper 方式注册（风格与早期项目一致，无 @MapperScan）。</p>
 */
@SpringBootApplication
public class AimallApplication {

    public static void main(String[] args) {
        SpringApplication.run(AimallApplication.class, args);
    }
}