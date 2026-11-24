package com.prepdeck.report.dto.response;

public record DimensionScore(
        String name,
        double score,
        int rank
) {}
