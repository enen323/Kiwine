package com.prepdeck.report.dto.request;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record GenerateReportRequest(
        LocalDateTime startDate,

        LocalDateTime endDate,

        @Size(min = 1, message = "interviewIds 不能为空")
        List<Long> interviewIds
) {}