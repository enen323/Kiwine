package com.prepdeck.resume.client;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuotaClientFallbackFactoryTest {

    private QuotaClient fallback;

    @BeforeEach
    void setUp() {
        fallback = new QuotaClientFallbackFactory().create(new RuntimeException("system error"));
    }

    @Test
    void rethrowsBusinessException() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> new QuotaClientFallbackFactory()
                        .create(new BadRequestException(ErrorCode.QUOTA_EXHAUSTED)));
        assertEquals(ErrorCode.QUOTA_EXHAUSTED.getCode(), ex.getCode());
    }

    @Test
    void wrapsSystemExceptionAsServiceUnavailable() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fallback.consume("RESUME_AI_CHAT"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void deductFreeWrapsSystemException() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fallback.deductFree("RESUME_AI_CHAT"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void deductPaidWrapsSystemException() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fallback.deductPaid("RESUME_AI_CHAT"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }
}
