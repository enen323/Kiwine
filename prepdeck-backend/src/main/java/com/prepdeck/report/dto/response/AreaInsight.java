package com.prepdeck.report.dto.response;

import java.util.List;

public record AreaInsight(
        String dimension,
        double avgScore,
        List<QuestionInsight> questions
) {}
