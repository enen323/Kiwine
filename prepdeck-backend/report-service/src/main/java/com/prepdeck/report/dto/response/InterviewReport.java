package com.prepdeck.report.dto.response;

public record InterviewReport(
        OverallSummary summary,
        DimensionScores dimensionScores,
        StrongAreas strongAreas,
        WeakAreas weakAreas,
        TrendData trend,
        ImprovementSuggestions suggestions
) {}
