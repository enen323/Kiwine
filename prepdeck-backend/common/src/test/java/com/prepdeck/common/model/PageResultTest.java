package com.prepdeck.common.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {

    @Test
    void success() {
        List<String> data = List.of("a", "b");
        PageResult<String> r = PageResult.success(data, 1, 20, 100L);
        assertEquals(0, r.code());
        assertEquals("success", r.message());
        assertEquals(data, r.data());
        assertEquals(1, r.page());
        assertEquals(20, r.pageSize());
        assertEquals(100L, r.total());
    }
}
