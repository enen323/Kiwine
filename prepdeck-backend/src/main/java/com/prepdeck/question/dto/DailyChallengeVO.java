package com.prepdeck.question.dto;

import com.prepdeck.question.dto.QuestionDetailVO;

public record DailyChallengeVO(
        Long id,
        QuestionDetailVO question,
        Boolean completed
) {
}
