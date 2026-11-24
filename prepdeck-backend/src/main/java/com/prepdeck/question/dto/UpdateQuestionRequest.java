package com.prepdeck.question.dto;

import java.util.List;

public record UpdateQuestionRequest(
        String title,
        String content,
        String answer,
        String type,
        String difficulty,
        String easyMistake,
        List<Long> tagIds
) {
}
