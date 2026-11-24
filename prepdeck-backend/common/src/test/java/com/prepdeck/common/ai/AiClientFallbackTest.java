package com.prepdeck.common.ai;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiClientFallbackTest {

    @Test
    void chatFallbackTwoArgThrowsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> AiClientFallback.chatFallback("system", "user", new RuntimeException("AI error")));
        assertEquals(ErrorCode.LLM_SERVICE_ERROR.getCode(), ex.getCode());
    }

    @Test
    void chatFallbackThreeArgThrowsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> AiClientFallback.chatFallback("system", "user", "json_object", new RuntimeException("AI error")));
        assertEquals(ErrorCode.LLM_SERVICE_ERROR.getCode(), ex.getCode());
    }

    @Test
    void chatFallbackCarriesCorrectErrorCode() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> AiClientFallback.chatFallback("s", "u", new IllegalStateException("timeout")));
        assertEquals(ErrorCode.LLM_SERVICE_ERROR.getCode(), ex.getCode());
    }
}
