package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;

/**
 * 资源不存在异常
 */
public final class NotFoundException extends BusinessException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotFoundException(String message) {
        super(404, message);
    }
}
