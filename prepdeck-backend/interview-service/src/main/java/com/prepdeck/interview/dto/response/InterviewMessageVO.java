package com.prepdeck.interview.dto.response;

import com.prepdeck.interview.enums.MessageRoleEnum;
import com.prepdeck.interview.enums.QuestionTypeEnum;

import java.time.LocalDateTime;

/**
 * 面试消息 VO
 */
public record InterviewMessageVO(
        Long id,
        Long interviewId,
        MessageRoleEnum role,
        String content,
        QuestionTypeEnum questionType,
        Integer sequenceNum,
        Integer tokenCount,
        LocalDateTime createdAt
) {}
