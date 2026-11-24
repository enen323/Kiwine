package com.prepdeck.job.dto.response;

import java.time.LocalDateTime;

public record AiCoverLetterVO(
        String id,
        String resumeId,
        String applicationId,
        String company,
        String position,
        String jobDescription,
        String extraNotes,
        String outputLanguage,
        String title,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
