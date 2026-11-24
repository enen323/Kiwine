package com.prepdeck.question.dto;

import java.util.List;

public record QuestionListRequest(
        Integer page,
        Integer pageSize,
        String sortBy,
        String order,
        String type,
        String difficulty,
        List<Long> tagIds,
        String keyword
) {
}
