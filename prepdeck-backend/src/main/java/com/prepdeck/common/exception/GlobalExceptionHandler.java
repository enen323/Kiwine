package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.model.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一拦截各层异常，转换为 {@link Result} 返回。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        return Result.error(ErrorCode.PARAM_INVALID.getCode(),
                "参数 " + e.getName() + " 类型不匹配");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error(ErrorCode.PARAM_INVALID.getCode(), msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(ErrorCode.MISSING_PARAM.getCode(), "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoHandler(NoResourceFoundException e) {
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), "接口不存在: " + e.getResourcePath());
    }

    @ExceptionHandler(Throwable.class)
    public Result<Void> handleUnknown(Throwable e) {
        log.error("未预期异常", e);
        return Result.error(ErrorCode.INTERNAL_ERROR);
    }
}
