package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;

/**
 * 未认证异常
 */
public final class UnauthorizedException extends BusinessException {

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
    }

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED.getCode(), message);
    }
}
