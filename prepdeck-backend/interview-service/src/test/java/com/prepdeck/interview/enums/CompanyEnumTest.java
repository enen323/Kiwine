package com.prepdeck.interview.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompanyEnumTest {

    @Test
    void allValuesHaveDisplayName() {
        for (CompanyEnum company : CompanyEnum.values()) {
            assertNotNull(company.getDisplayName());
            assertFalse(company.getDisplayName().isBlank());
        }
    }

    @Test
    void allValuesHaveStyleDescription() {
        for (CompanyEnum company : CompanyEnum.values()) {
            assertNotNull(company.getStyleDescription());
            assertFalse(company.getStyleDescription().isBlank());
        }
    }

    @Test
    void valueOfRoundTrip() {
        assertEquals(CompanyEnum.BYTE_DANCE, CompanyEnum.valueOf("BYTE_DANCE"));
        assertEquals(CompanyEnum.ALIBABA, CompanyEnum.valueOf("ALIBABA"));
        assertEquals(CompanyEnum.TENCENT, CompanyEnum.valueOf("TENCENT"));
    }

    @Test
    void exactlySixValues() {
        assertEquals(6, CompanyEnum.values().length);
    }
}
