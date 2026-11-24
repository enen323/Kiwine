package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnauthorizedExceptionTest {

    @Test
    void constructorWithErrorCode() {
        UnauthorizedException ex = new UnauthorizedException(ErrorCode.TOKEN_EXPIRED);
        assertEquals(20001, ex.getCode());
        assertEquals("Token 已过期", ex.getMessage());
    }

    @Test
    void constructorWithMessage() {
        UnauthorizedException ex = new UnauthorizedException("please login");
        assertEquals(20000, ex.getCode());
        assertEquals("please login", ex.getMessage());
    }

    @Test
    void extendsBusinessException() {
        UnauthorizedException ex = new UnauthorizedException(ErrorCode.UNAUTHORIZED);
        assertInstanceOf(BusinessException.class, ex);
    }
}
