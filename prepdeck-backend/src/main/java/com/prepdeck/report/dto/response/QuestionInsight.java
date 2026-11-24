package com.prepdeck.report.dto.response;

public record QuestionInsight(
        String description,
        int score,
        String feedback
) {}
