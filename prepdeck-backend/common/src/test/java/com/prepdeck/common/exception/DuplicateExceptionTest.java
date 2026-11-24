package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateExceptionTest {

    @Test
    void constructorWithErrorCode() {
        DuplicateException ex = new DuplicateException(ErrorCode.USER_EXISTS);
        assertEquals(30001, ex.getCode());
        assertEquals("用户已存在", ex.getMessage());
    }

    @Test
    void constructorWithMessage() {
        DuplicateException ex = new DuplicateException("duplicate record");
        assertEquals(409, ex.getCode());
        assertEquals("duplicate record", ex.getMessage());
    }

    @Test
    void extendsBusinessException() {
        DuplicateException ex = new DuplicateException(ErrorCode.USER_EXISTS);
        assertInstanceOf(BusinessException.class, ex);
    }
}
