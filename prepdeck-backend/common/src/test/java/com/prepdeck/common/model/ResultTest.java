package com.prepdeck.common.model;

import com.prepdeck.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void successWithData() {
        Result<String> r = Result.success("hello");
        assertEquals(0, r.code());
        assertEquals("success", r.message());
        assertEquals("hello", r.data());
    }

    @Test
    void successWithoutData() {
        Result<Void> r = Result.success();
        assertEquals(0, r.code());
        assertEquals("success", r.message());
        assertNull(r.data());
    }

    @Test
    void errorWithCodeAndMessage() {
        Result<Void> r = Result.error(400, "bad request");
        assertEquals(400, r.code());
        assertEquals("bad request", r.message());
        assertNull(r.data());
    }

    @Test
    void errorWithErrorCode() {
        Result<Void> r = Result.error(ErrorCode.USER_NOT_FOUND);
        assertEquals(30000, r.code());
        assertEquals("用户不存在", r.message());
        assertNull(r.data());
    }
}
