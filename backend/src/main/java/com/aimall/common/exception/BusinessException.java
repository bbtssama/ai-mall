package com.aimall.common.exception;

import com.aimall.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常：带错误码，由全局异常处理器统一转成 R
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    /** 默认服务端错误(500)，风格对齐早期项目 */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.SERVER_ERROR.getCode();
    }

    public BusinessException(ResultCode rc) {
        super(rc.getMsg());
        this.code = rc.getCode();
    }

    public BusinessException(ResultCode rc, String message) {
        super(message);
        this.code = rc.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}