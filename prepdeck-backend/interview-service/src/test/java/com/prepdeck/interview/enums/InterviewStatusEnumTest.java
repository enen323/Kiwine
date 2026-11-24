package com.prepdeck.interview.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterviewStatusEnumTest {

    @Test
    void valueOfRoundTrip() {
        assertEquals(InterviewStatusEnum.CREATED, InterviewStatusEnum.valueOf("CREATED"));
        assertEquals(InterviewStatusEnum.IN_PROGRESS, InterviewStatusEnum.valueOf("IN_PROGRESS"));
        assertEquals(InterviewStatusEnum.COMPLETED, InterviewStatusEnum.valueOf("COMPLETED"));
        assertEquals(InterviewStatusEnum.TERMINATED, InterviewStatusEnum.valueOf("TERMINATED"));
    }

    @Test
    void exactlyFourValues() {
        assertEquals(4, InterviewStatusEnum.values().length);
    }
}
