package com.prepdeck.interview.dto.response;

import com.prepdeck.interview.enums.InterviewStageEnum;
import com.prepdeck.interview.enums.InterviewStatusEnum;

import java.time.LocalDateTime;

/**
 * 面试完整信息 VO
 */
public record InterviewVO(
        Long id,
        Long userId,
        String company,
        String companyDisplayName,
        String position,
        String positionDisplayName,
        InterviewStatusEnum status,
        InterviewStageEnum currentStage,
        Integer stageOrder,
        Integer totalQuestions,
        Integer scoreTotal,
        Integer scoreTech,
        Integer scoreExpression,
        Integer scoreProject,
        Integer scoreLogic,
        Integer scoreAdaptability,
        String suggestion,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Integer durationSeconds,
        LocalDateTime createdAt
) {}
