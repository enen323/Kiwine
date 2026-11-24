package com.prepdeck.common.exception;

import com.prepdeck.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotFoundExceptionTest {

    @Test
    void constructorWithErrorCode() {
        NotFoundException ex = new NotFoundException(ErrorCode.RESUME_NOT_FOUND);
        assertEquals(40000, ex.getCode());
        assertEquals("简历不存在", ex.getMessage());
    }

    @Test
    void constructorWithMessage() {
        NotFoundException ex = new NotFoundException("resource not found");
        assertEquals(404, ex.getCode());
        assertEquals("resource not found", ex.getMessage());
    }

    @Test
    void extendsBusinessException() {
        NotFoundException ex = new NotFoundException(ErrorCode.USER_NOT_FOUND);
        assertInstanceOf(BusinessException.class, ex);
    }
}
