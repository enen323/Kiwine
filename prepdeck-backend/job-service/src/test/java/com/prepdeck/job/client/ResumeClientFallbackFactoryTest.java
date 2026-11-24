package com.prepdeck.job.client;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
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
    void wrapsSystemExceptionAsServiceUnavailable() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fallback.getResume(1L));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void connectionRefusedTriggersFallback() {
        ResumeClient resumeClient = new ResumeClientFallbackFactory()
                .create(new java.net.ConnectException("Connection refused"));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeClient.getResume(999L));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }
}
