package com.prepdeck.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiConstantTest {

    @Test
    void apiPrefix() {
        assertEquals("/api/v1", ApiConstant.API_V1);
    }

    @Test
    void paginationDefaults() {
        assertEquals(1, ApiConstant.DEFAULT_PAGE);
        assertEquals(10, ApiConstant.DEFAULT_PAGE_SIZE);
        assertEquals(100, ApiConstant.MAX_PAGE_SIZE);
    }

    @Test
    void headerNames() {
        assertEquals("X-Trace-Id", ApiConstant.HEADER_TRACE_ID);
        assertEquals("X-Request-Id", ApiConstant.HEADER_REQUEST_ID);
    }

    @Test
    void privateConstructorCanBeAccessed() throws Exception {
        var ctor = ApiConstant.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }
}
