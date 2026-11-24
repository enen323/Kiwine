package com.prepdeck.resume.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record ResumeUpdateRequest(
        String fileName,
        String contentText,
        @Size(max = 10000)
        String contentJson,
        Boolean isActive,
        String title,
        String template,
        String language,
        Object themeConfig,
        Boolean isPublic,
        List<SectionRequest> sections
) {
}
