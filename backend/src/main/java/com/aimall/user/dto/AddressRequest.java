package com.aimall.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增/编辑收货地址请求
 */
@Data
public class AddressRequest {

    @NotBlank(message = "收货人不能为空")
    private String receiver;

    @NotBlank(message = "联系电话不能为空")
    private String phone;

    private String province;

    private String city;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    /** 是否设为默认（默认 false） */
    private boolean isDefault;
}