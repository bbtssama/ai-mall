package com.aimall.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收货地址视图对象
 */
@Data
public class AddressVO {

    private Long id;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String detail;
    private Boolean isDefault;
    private LocalDateTime createdAt;

    /** 拼接的完整地址（省市区 + 详细），供下单快照使用 */
    public String getFullAddress() {
        return (province == null ? "" : province)
                + (city == null ? "" : city)
                + (detail == null ? "" : detail);
    }
}