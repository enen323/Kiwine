package com.prepdeck.question.dto;

import java.time.LocalDateTime;

public record UpdateTagRequest(
        String name,
        String description
) {
}
