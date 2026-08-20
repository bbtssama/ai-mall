package com.aimall.common.api;

import lombok.Data;

/**
 * 统一返回体：{code, msg, data}
 * 风格与早期项目（order-system-v2 / blog-system）保持一致：
 * ok(...) 成功系 / fail(...) 失败系；fail(String) 默认服务端错误(500)。
 */
@Data
public class R<T> {

    public static final int CODE_SUCCESS = 200;

    private int code;
    private String msg;
    private T data;

    public R() {
    }

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(CODE_SUCCESS, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(CODE_SUCCESS, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(CODE_SUCCESS, msg, data);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(ResultCode.SERVER_ERROR.getCode(), msg, null);
    }

    public static <T> R<T> fail(ResultCode rc) {
        return new R<>(rc.getCode(), rc.getMsg(), null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }
}