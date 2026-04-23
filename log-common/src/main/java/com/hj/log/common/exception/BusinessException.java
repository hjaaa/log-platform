package com.hj.log.common.exception;

/**
 * 业务异常基类。
 *
 * <p>由 GlobalExceptionHandler（落 log-web）统一捕获并按 {@link #httpStatus} 包装为
 * {@code ResponseResult.fail(code, message)} 返回，禁止暴露堆栈给调用方。
 *
 * <p>{@code httpStatus} 默认 400；4xx 表示客户端错，5xx 由通用 {@code Exception} 兜底处理。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_HTTP_STATUS = 400;

    private final String code;
    private final int httpStatus;

    public BusinessException(String code, String message) {
        this(code, message, DEFAULT_HTTP_STATUS);
    }

    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String code, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
