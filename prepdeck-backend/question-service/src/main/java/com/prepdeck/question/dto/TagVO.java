package com.prepdeck.question.dto;

import java.time.LocalDateTime;

public record TagVO(
        Long id,
        String name,
        String description,
        LocalDateTime createTime
) {
}
