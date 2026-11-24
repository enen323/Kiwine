package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;

/**
 * 数据重复异常
 */
public final class DuplicateException extends BusinessException {

    public DuplicateException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DuplicateException(String message) {
        super(409, message);
    }
}
