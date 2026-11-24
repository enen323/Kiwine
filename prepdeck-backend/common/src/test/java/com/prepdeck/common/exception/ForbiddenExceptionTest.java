package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForbiddenExceptionTest {

    @Test
    void constructorWithErrorCode() {
        ForbiddenException ex = new ForbiddenException(ErrorCode.FORBIDDEN);
        assertEquals(20003, ex.getCode());
        assertEquals("无权限访问", ex.getMessage());
    }

    @Test
    void constructorWithMessage() {
        ForbiddenException ex = new ForbiddenException("access denied");
        assertEquals(20003, ex.getCode());
        assertEquals("access denied", ex.getMessage());
    }

    @Test
    void extendsBusinessException() {
        ForbiddenException ex = new ForbiddenException(ErrorCode.FORBIDDEN);
        assertInstanceOf(BusinessException.class, ex);
    }
}
