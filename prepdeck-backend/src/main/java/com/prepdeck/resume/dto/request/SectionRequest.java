package com.prepdeck.resume.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SectionRequest(
        Long id,
        @NotBlank String type,
        String title,
        Integer sortOrder,
        Boolean visible,
        String content
) {
}
