package com.prepdeck.interview.dto.response;

import com.prepdeck.interview.enums.InterviewStageEnum;
import com.prepdeck.interview.enums.InterviewStatusEnum;

import java.time.LocalDateTime;

/**
 * 面试列表项 VO
 */
public record InterviewListVO(
        Long id,
        String company,
        String position,
        InterviewStatusEnum status,
        InterviewStageEnum currentStage,
        Integer totalQuestions,
        Integer scoreTotal,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Integer durationSeconds,
        LocalDateTime createdAt
) {}
