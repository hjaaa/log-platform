package com.hj.log.common.handler;

import com.hj.log.common.base.ResponseResult;
import com.hj.log.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = extractFieldMessage(exception.getBindingResult().getFieldError());
        return ResponseResult.fail(ResponseResult.VALIDATION_ERROR_CODE, message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseResult<Void> handleBindException(BindException exception) {
        String message = extractFieldMessage(exception.getBindingResult().getFieldError());
        return ResponseResult.fail(ResponseResult.VALIDATION_ERROR_CODE, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseResult<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(constraintViolation -> constraintViolation.getMessage())
                .orElse("参数校验失败");
        return ResponseResult.fail(ResponseResult.VALIDATION_ERROR_CODE, message);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseResult<Void> handleBusinessException(BusinessException exception) {
        return ResponseResult.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseResult<Void> handleException(Exception exception) {
        LOGGER.error("系统异常", exception);
        return ResponseResult.fail(ResponseResult.SYSTEM_ERROR_CODE, "系统异常");
    }

    private String extractFieldMessage(FieldError fieldError) {
        return Optional.ofNullable(fieldError)
                .map(FieldError::getDefaultMessage)
                .filter(message -> !message.isBlank())
                .orElse("参数校验失败");
    }
}
