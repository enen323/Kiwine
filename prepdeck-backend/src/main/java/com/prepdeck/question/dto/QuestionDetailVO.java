package com.prepdeck.question.dto;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionDetailVO(
        Long id,
        String title,
        String content,
        AnswerVO answer,
        String type,
        String difficulty,
        String easyMistake,
        Integer interviewCount,
        Integer views,
        Integer favorites,
        List<TagVO> tags,
        Boolean isFavorited,
        LocalDateTime createTime
) {
}
