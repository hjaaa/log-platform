package com.hj.log.common.base;

import com.hj.log.common.exception.ErrorCode;

/**
 * 统一响应包装。Controller 层全部走此结构。
 *
 * <p>序列化字段固定为 {@code code} / {@code message} / {@code data}，与
 * detailed-design §2.1 保持一致。{@code code = "OK"} 表示成功；其他 code 见 {@link ErrorCode}。
 */
public class ResponseResult<T> {

    private String code;
    private String message;
    private T data;

    public ResponseResult() {
    }

    public ResponseResult(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ResponseResult<T> ok() {
        return new ResponseResult<>(ErrorCode.OK, "", null);
    }

    public static <T> ResponseResult<T> ok(T data) {
        return new ResponseResult<>(ErrorCode.OK, "", data);
    }

    public static <T> ResponseResult<T> fail(String code, String message) {
        return new ResponseResult<>(code, message, null);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
