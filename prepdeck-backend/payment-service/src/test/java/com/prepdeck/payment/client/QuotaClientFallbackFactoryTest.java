package com.prepdeck.payment.client;

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
                        .create(new BadRequestException(ErrorCode.BAD_REQUEST)));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void wrapsSystemExceptionAsServiceUnavailable() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fallback.grantPaid(1L, "PAYMENT"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void timeoutExceptionTriggersFallback() {
        QuotaClient quotaClient = new QuotaClientFallbackFactory()
                .create(new java.util.concurrent.TimeoutException("timeout"));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> quotaClient.grantPaid(1L, "PAYMENT"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }
}
