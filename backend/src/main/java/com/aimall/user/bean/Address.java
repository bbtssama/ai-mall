package com.aimall.user.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收货地址（t_user_address）
 */
@Data
public class Address {

    private Long id;
    private Long userId;

    /** 收货人 */
    private String receiver;

    /** 联系电话 */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 详细地址 */
    private String detail;

    /** 是否默认：1是 0否 */
    private Integer isDefault;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}