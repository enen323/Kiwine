package com.prepdeck.interview.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionEnumTest {

    @Test
    void allValuesHaveDisplayName() {
        for (PositionEnum position : PositionEnum.values()) {
            assertNotNull(position.getDisplayName());
            assertFalse(position.getDisplayName().isBlank());
        }
    }

    @Test
    void valueOfRoundTrip() {
        assertEquals(PositionEnum.BACKEND, PositionEnum.valueOf("BACKEND"));
        assertEquals(PositionEnum.FRONTEND, PositionEnum.valueOf("FRONTEND"));
        assertEquals(PositionEnum.ALGORITHM, PositionEnum.valueOf("ALGORITHM"));
    }

    @Test
    void exactlySixValues() {
        assertEquals(6, PositionEnum.values().length);
    }
}
