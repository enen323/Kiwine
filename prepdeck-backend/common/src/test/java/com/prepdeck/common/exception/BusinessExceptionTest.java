package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void constructorWithErrorCode() {
        BusinessException ex = new NotFoundException(ErrorCode.USER_NOT_FOUND);
        assertEquals(30000, ex.getCode());
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void constructorWithCodeAndMessage() {
        BusinessException ex = new BadRequestException("custom message");
        assertEquals(10000, ex.getCode());
        assertEquals("custom message", ex.getMessage());
    }

    @Test
    void constructorWithCodeAndMessagePreservesCode() {
        NotFoundException ex = new NotFoundException("not found msg");
        assertEquals(404, ex.getCode());
        assertEquals("not found msg", ex.getMessage());
    }

    @Test
    void isRuntimeException() {
        BusinessException ex = new NotFoundException(ErrorCode.USER_NOT_FOUND);
        assertInstanceOf(RuntimeException.class, ex);
    }
}
