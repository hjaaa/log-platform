package com.hj.log.common.base;

public abstract class BaseController {

    protected <T> ResponseResult<T> success(T data) {
        return ResponseResult.success(data);
    }

    protected ResponseResult<Void> success() {
        return ResponseResult.success();
    }

    protected <T> ResponseResult<T> fail(int code, String message) {
        return ResponseResult.fail(code, message);
    }
}
