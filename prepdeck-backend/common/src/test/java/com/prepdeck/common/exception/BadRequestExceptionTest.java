package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BadRequestExceptionTest {

    @Test
    void constructorWithErrorCode() {
        BadRequestException ex = new BadRequestException(ErrorCode.BAD_REQUEST);
        assertEquals(10000, ex.getCode());
        assertEquals("请求参数错误", ex.getMessage());
    }

    @Test
    void constructorWithMessage() {
        BadRequestException ex = new BadRequestException("invalid input");
        assertEquals(10000, ex.getCode());
        assertEquals("invalid input", ex.getMessage());
    }

    @Test
    void extendsBusinessException() {
        BadRequestException ex = new BadRequestException(ErrorCode.BAD_REQUEST);
        assertInstanceOf(BusinessException.class, ex);
    }
}
