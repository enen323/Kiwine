package com.prepdeck.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusEnumTest {

    @Test
    void enabled() {
        assertEquals(1, StatusEnum.ENABLED.getValue());
        assertEquals("启用", StatusEnum.ENABLED.getLabel());
    }

    @Test
    void disabled() {
        assertEquals(0, StatusEnum.DISABLED.getValue());
        assertEquals("禁用", StatusEnum.DISABLED.getLabel());
    }

    @Test
    void valueOfRoundTrip() {
        assertEquals(StatusEnum.ENABLED, StatusEnum.valueOf("ENABLED"));
        assertEquals(StatusEnum.DISABLED, StatusEnum.valueOf("DISABLED"));
    }

    @Test
    void exactlyTwoValues() {
        assertEquals(2, StatusEnum.values().length);
    }
}
