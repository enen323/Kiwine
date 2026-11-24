package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;

/**
 * 业务异常基类 — 密封类
 *
 * <p>禁止直接捕获 Exception，统一抛出子类。</p>
 */
public sealed class BusinessException extends RuntimeException
        permits NotFoundException, UnauthorizedException, ForbiddenException,
                DuplicateException, BadRequestException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
