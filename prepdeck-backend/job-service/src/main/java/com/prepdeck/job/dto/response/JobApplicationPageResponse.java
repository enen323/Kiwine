package com.prepdeck.job.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record JobApplicationPageResponse(
        List<JobApplicationVO> items,
        long total,
        int page,
        int pageSize,
        int totalPages
) {
}
