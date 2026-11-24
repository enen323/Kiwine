package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;

/**
 * 请求参数错误异常
 */
public final class BadRequestException extends BusinessException {

    public BadRequestException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST.getCode(), message);
    }
}
