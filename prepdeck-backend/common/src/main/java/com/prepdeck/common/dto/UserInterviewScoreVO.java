package com.prepdeck.common.dto;

import java.time.LocalDateTime;

public record UserInterviewScoreVO(
        Long interviewId,
        String company,
        String position,
        Integer scoreTech,
        Integer scoreExpression,
        Integer scoreProject,
        Integer scoreLogic,
        Integer scoreAdaptability,
        LocalDateTime completedAt
) {}
