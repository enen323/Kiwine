package com.prepdeck.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void success() {
        assertEquals(0, ErrorCode.SUCCESS.getCode());
        assertEquals("success", ErrorCode.SUCCESS.getMessage());
    }

    @Test
    void badRequest() {
        assertEquals(10000, ErrorCode.BAD_REQUEST.getCode());
        assertEquals("请求参数错误", ErrorCode.BAD_REQUEST.getMessage());
    }

    @Test
    void unauthorized() {
        assertEquals(20000, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals("未登录", ErrorCode.UNAUTHORIZED.getMessage());
    }

    @Test
    void userNotFound() {
        assertEquals(30000, ErrorCode.USER_NOT_FOUND.getCode());
        assertEquals("用户不存在", ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    void resumeNotFound() {
        assertEquals(40000, ErrorCode.RESUME_NOT_FOUND.getCode());
        assertEquals("简历不存在", ErrorCode.RESUME_NOT_FOUND.getMessage());
    }

    @Test
    void interviewNotFound() {
        assertEquals(50000, ErrorCode.INTERVIEW_NOT_FOUND.getCode());
        assertEquals("面试记录不存在", ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
    }

    @Test
    void reportNotFound() {
        assertEquals(60000, ErrorCode.REPORT_NOT_FOUND.getCode());
        assertEquals("报告不存在", ErrorCode.REPORT_NOT_FOUND.getMessage());
    }

    @Test
    void internalError() {
        assertEquals(90000, ErrorCode.INTERNAL_ERROR.getCode());
        assertEquals("服务器内部错误", ErrorCode.INTERNAL_ERROR.getMessage());
    }

    @Test
    void quotaExhaustedCodeIs70000() {
        assertEquals(70000, ErrorCode.QUOTA_EXHAUSTED.getCode());
    }

    @Test
    void paymentRequiredCodeIs70001() {
        assertEquals(70001, ErrorCode.PAYMENT_REQUIRED.getCode());
    }

    @Test
    void paymentInvalidCodeIs70002() {
        assertEquals(70002, ErrorCode.PAYMENT_INVALID.getCode());
    }
}
