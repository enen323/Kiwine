package com.prepdeck.question.dto;

import java.time.LocalDateTime;

public record CreateTagRequest(
        String name,
        String description
) {
}
