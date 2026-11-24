package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;

/**
 * 无权限异常
 */
public final class ForbiddenException extends BusinessException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN.getCode(), message);
    }
}
