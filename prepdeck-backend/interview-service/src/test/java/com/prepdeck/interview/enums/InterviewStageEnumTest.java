package com.prepdeck.interview.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterviewStageEnumTest {

    @Test
    void nextSelfIntroToProjectDeepDive() {
        assertEquals(InterviewStageEnum.PROJECT_DEEP_DIVE,
                InterviewStageEnum.SELF_INTRO.next());
    }

    @Test
    void nextProjectDeepDiveToTechAssessment() {
        assertEquals(InterviewStageEnum.TECH_ASSESSMENT,
                InterviewStageEnum.PROJECT_DEEP_DIVE.next());
    }

    @Test
    void nextTechAssessmentToReverseQA() {
        assertEquals(InterviewStageEnum.REVERSE_QA,
                InterviewStageEnum.TECH_ASSESSMENT.next());
    }

    @Test
    void nextReverseQAToCompleted() {
        assertEquals(InterviewStageEnum.COMPLETED,
                InterviewStageEnum.REVERSE_QA.next());
    }

    @Test
    void nextCompletedStaysCompleted() {
        assertEquals(InterviewStageEnum.COMPLETED,
                InterviewStageEnum.COMPLETED.next());
    }

    @Test
    void displayNameNotNull() {
        for (InterviewStageEnum stage : InterviewStageEnum.values()) {
            assertNotNull(stage.getDisplayName());
        }
    }

    @Test
    void exactlyFiveValues() {
        assertEquals(5, InterviewStageEnum.values().length);
    }
}
