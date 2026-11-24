package com.prepdeck.report.dto.response;

public record OverallSummary(
        int totalInterviews,
        double avgScore,
        int highestScore,
        int lowestScore,
        int totalDurationSeconds
) {}
