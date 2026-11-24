package com.prepdeck.interview.client;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResumeClientFallbackFactoryTest {

    private ResumeClient fallback;

    @BeforeEach
    void setUp() {
        fallback = new ResumeClientFallbackFactory().create(new RuntimeException("system error"));
    }

    @Test
    void rethrowsBusinessException() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> new ResumeClientFallbackFactory()
                        .create(new BadRequestException(ErrorCode.RESUME_NOT_FOUND)));
        assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void wrapsSystemExceptionAsServiceUnavailable() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fallback.getById(1L));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void connectionTimeoutTriggersFallback() {
        ResumeClient resumeClient = new ResumeClientFallbackFactory()
                .create(new java.net.ConnectException("Connection refused"));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeClient.getById(1L));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }
}
