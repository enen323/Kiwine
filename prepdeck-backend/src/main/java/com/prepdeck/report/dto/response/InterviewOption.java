package com.prepdeck.report.dto.response;

import java.time.LocalDateTime;

public record InterviewOption(
        Long id,
        String company,
        String position,
        LocalDateTime completedAt,
        Integer scoreTotal
) {}
