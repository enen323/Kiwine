package com.prepdeck.interview.client;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.DuplicateException;
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
                () -> fallback.consume("INTERVIEW"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void deductFreeRethrowsBusinessException() {
        DuplicateException ex = assertThrows(DuplicateException.class,
                () -> new QuotaClientFallbackFactory()
                        .create(new DuplicateException(ErrorCode.INTERVIEW_ALREADY_COMPLETED)));
        assertEquals(ErrorCode.INTERVIEW_ALREADY_COMPLETED.getCode(), ex.getCode());
    }

    @Test
    void deductFreeWrapsSystemException() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fallback.deductFree("INTERVIEW"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void deductPaidWrapsSystemException() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fallback.deductPaid("INTERVIEW"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void systemExceptionTriggersFallback() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fallback.consume("INTERVIEW"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }
}
