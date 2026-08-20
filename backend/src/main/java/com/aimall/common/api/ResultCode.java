package com.aimall.common.api;

import lombok.Getter;

/**
 * 统一错误码
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "ok"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器开小差了，请稍后再试"),

    // 用户域 1xxx
    USERNAME_EXISTS(1001, "用户名已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "用户名或密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),

    // 电商域 2xxx
    PRODUCT_NOT_FOUND(2001, "商品不存在"),
    SKU_NOT_FOUND(2002, "商品规格不存在"),
    SKU_OFF_SHELF(2003, "商品已下架"),
    STOCK_NOT_ENOUGH(2004, "库存不足"),
    CART_ITEM_NOT_FOUND(2005, "购物车条目不存在"),
    CART_EMPTY(2006, "购物车为空"),
    ORDER_NOT_FOUND(2007, "订单不存在"),
    ORDER_STATUS_INVALID(2008, "当前订单状态不允许该操作"),

    // AI 域 3xxx
    AI_SERVICE_ERROR(3001, "AI 服务暂时不可用，请稍后再试");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}