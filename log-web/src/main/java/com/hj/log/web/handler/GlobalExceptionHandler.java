package com.hj.log.web.handler;

import com.hj.log.common.base.ResponseResult;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常统一出口，落 detailed-design §4.4。
 *
 * <p>三条响应路径：
 * <ul>
 *   <li>{@link BusinessException} → 按 {@code httpStatus} 透出 {@code ResponseResult.fail}</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 + 聚合字段错误摘要（不回显堆栈）</li>
 *   <li>{@link MethodArgumentTypeMismatchException} → 400（枚举等 query 参数绑定失败）</li>
 *   <li>其他 {@link Exception} → 500 + 通用提示（message 会脱敏 {@code lpk_*}，堆栈只进日志）</li>
 * </ul>
 *
 * <p>脱敏规则见 detailed-design §5.3：异常 message 中的 {@code lpk_AB12...} 一律替换为
 * {@code lpk_***}，避免泄漏 key 明文。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 匹配 {@code lpk_} 前缀 + 至少一个字母/数字的连续串，用于兜底日志脱敏。 */
    private static final Pattern LPK_PATTERN = Pattern.compile("lpk_[A-Za-z0-9]+");

    private static final String REQUEST_ID_KEY = "requestId";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseResult<Void>> handleBusiness(BusinessException ex) {
        log.warn(
                "[business] code={} msg={} requestId={}",
                ex.getCode(),
                ex.getMessage(),
                MDC.get(REQUEST_ID_KEY));
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ResponseResult.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseResult<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        log.warn("[validation] detail={} requestId={}", detail, MDC.get(REQUEST_ID_KEY));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResponseResult.fail(ErrorCode.BAD_REQUEST, "请求参数不合法"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseResult<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        log.warn(
                "[validation] param={} value={} requestId={}",
                ex.getName(),
                ex.getValue(),
                MDC.get(REQUEST_ID_KEY));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResponseResult.fail(ErrorCode.BAD_REQUEST, "请求参数不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult<Void>> handleUnknown(Exception ex) {
        String masked = maskSensitive(ex.getMessage());
        log.error("[internal] requestId={} ex={}", MDC.get(REQUEST_ID_KEY), masked, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseResult.fail(ErrorCode.INTERNAL_ERROR, "服务暂时不可用"));
    }

    static String maskSensitive(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return LPK_PATTERN.matcher(raw).replaceAll("lpk_***");
    }
}
