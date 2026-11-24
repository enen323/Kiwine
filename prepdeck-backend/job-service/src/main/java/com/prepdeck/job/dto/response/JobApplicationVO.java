package com.prepdeck.job.dto.response;

import java.time.LocalDateTime;

public record JobApplicationVO(
        String id,
        String company,
        String position,
        String status,
        String channel,
        String resumeId,
        String resumeTitle,
        LocalDateTime appliedAt,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
